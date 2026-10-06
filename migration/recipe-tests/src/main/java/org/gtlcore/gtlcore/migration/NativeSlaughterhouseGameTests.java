package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.machine.multiblock.electric.SlaughterhouseMachine;
import org.gtlcore.gtlcore.config.ConfigHolder;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.multiblock.util.*;
import com.gregtechceu.gtceu.api.mui.factory.MachineUIFactory;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.gametest.*;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.*;
import brachy.modularui.utils.MouseData;
import brachy.modularui.value.sync.*;
import brachy.modularui.widget.WidgetTree;
import io.netty.buffer.Unpooled;
import java.util.*;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeSlaughterhouseGameTests {
    private record Fixture(SlaughterhouseMachine machine, BlockPos mobPos, NotifiableItemStackHandler output) {}

    private static Fixture build(GameTestHelper helper, String mob) {
        var entry = NativeSlaughterhouseFixtures.SLAUGHTERHOUSE;
        var blocks = new LinkedHashMap<BlockPos, BlockInfo>();
        AbstractStructureHelper.blockPattern(new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap(Map.of(0, 1)))
                .populate(blocks, entry.get().getStructurePatterns().get("main").get(), null, Direction.NORTH, Direction.UP, false);
        var old1 = ConfigHolder.INSTANCE.mobList1; var old2 = ConfigHolder.INSTANCE.mobList2;
        BlockPos controller = null;
        try {
            ConfigHolder.INSTANCE.mobList1 = new String[] { mob }; ConfigHolder.INSTANCE.mobList2 = new String[] { mob };
            for (var block : blocks.entrySet()) {
                var pos = new BlockPos(4, 3, 4).offset(block.getKey()); helper.setBlock(pos, block.getValue().getBlockState());
                if (block.getValue().getBlockState().is(entry.getBlock())) controller = pos;
            }
        } finally { ConfigHolder.INSTANCE.mobList1 = old1; ConfigHolder.INSTANCE.mobList2 = old2; }
        var machine = (SlaughterhouseMachine) helper.getBlockEntity(Objects.requireNonNull(controller));
        machine.checkAndFormStructure(); helper.assertTrue(machine.isFormed() && machine.getTier() == 3, "Native HV slaughterhouse did not form");
        var level = helper.getLevel();
        for (var direction : Direction.Plane.HORIZONTAL) {
            var gearbox = machine.getBlockPos().relative(direction);
            if (!level.getBlockState(gearbox).isAir()) continue;
            level.setBlockAndUpdate(gearbox, GTBlocks.CASING_STEEL_GEARBOX.getDefaultState());
            var mobPos = machine.getBlockPos().relative(direction, 3).above();
            var output = machine.getTraitsFromParts(NotifiableItemStackHandler.class).stream()
                    .filter(trait -> trait.getHandlerIO() == IO.OUT).findFirst().orElseThrow();
            return new Fixture(machine, mobPos, output);
        }
        throw new AssertionError("No free native slaughterhouse gearbox direction");
    }

    private static List<ItemEntity> drops(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3));
    }

    @GameTest(template = "gcym_empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeEntityLootRetainsBlockedStacksAndRecoversTheirExactComponents(GameTestHelper helper) {
        var f = build(helper, "cow"); var machine = f.machine(); machine.setSimulatingLoot(true);
        for (int i = 0; i < f.output().getSlots(); i++) f.output().setStackInSlot(i, new ItemStack(Items.IRON_NUGGET, 64));
        var stack = new ItemStack(Items.DIAMOND, 32); stack.set(DataComponents.CUSTOM_NAME, Component.literal("blocked slaughterhouse drop"));
        var item = new ItemEntity(helper.getLevel(), f.mobPos().getX() + .5, f.mobPos().getY(), f.mobPos().getZ() + .5, stack.copy());
        helper.getLevel().addFreshEntity(item);
        helper.startSequence().thenIdle(1).thenExecute(() -> {
            machine.afterWorking();
            helper.assertTrue(!item.isRemoved() && ItemStack.matches(item.getItem(), stack), "Full native output deleted or changed a physical drop");
            helper.assertTrue(drops(helper, f.mobPos()).stream().anyMatch(drop -> drop.getItem().is(Items.BEEF)), "Native cow ENTITY loot table produced no beef");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(Cow.class, new AABB(f.mobPos()).inflate(3)).isEmpty(), "Direct loot mode spawned its prototype into the world");
            for (int i = 0; i < f.output().getSlots(); i++) f.output().setStackInSlot(i, ItemStack.EMPTY);
            machine.afterWorking();
            int diamonds = 0;
            for (int i = 0; i < f.output().getSlots(); i++) {
                var actual = f.output().getStackInSlot(i);
                if (ItemStack.isSameItemSameComponents(actual, stack)) diamonds += actual.getCount();
            }
            helper.assertTrue(item.isRemoved() && diamonds == 32, "Native resumed recovery lost, duplicated or stripped the blocked stack");
        }).thenSucceed();
    }

    @GameTest(template = "gcym_empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeModeClickPersistsAndLiveSpawningDoesNotKillAnInWorldServerPlayer(GameTestHelper helper) {
        var f = build(helper, "cow"); var machine = f.machine(); var level = helper.getLevel();
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "SlaughterTest"));
        player.setPos(Vec3.atCenterOf(f.mobPos())); level.addNewPlayer(player);
        helper.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class, new AABB(f.mobPos()).inflate(3)).contains(player),
                "Native test player was not in the slaughterhouse entity query");
        var settings = new UISettings(brachy.modularui.api.RecipeViewerSettings.DUMMY);
        var sync = new ModularSyncManager(false); var manager = new PanelSyncManager(sync, true);
        var data = new PosGuiData(player, machine.getBlockPos()); var panel = MachineUIFactory.INSTANCE.createPanel(data, manager, settings);
        WidgetTree.collectSyncValues(manager, panel);
        var menu = new ModularContainerMenu(87); menu.construct(player, sync, settings, panel.getName(), data); player.containerMenu = menu;
        var packet = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        try {
            machine.afterWorking();
            helper.assertTrue(!player.isRemoved() && player.isAlive() && player.getHealth() == player.getMaxHealth(), "Native slaughterhouse damaged or removed a server player");
            helper.assertTrue(level.getEntitiesOfClass(Cow.class, new AABB(f.mobPos()).inflate(3)).size() == 9, "Native HV spawning lost its original nine-mob cycle");
            new MouseData(Dist.DEDICATED_SERVER, 1, false, false, false).writeToPacket(packet);
            manager.receiveWidgetUpdate("slaughterhouseToggle:0", false, 1, packet);
            helper.assertTrue(!machine.isSimulatingLoot(), "Right click changed the native slaughterhouse mode");
            packet.clear(); new MouseData(Dist.DEDICATED_SERVER, 0, false, false, false).writeToPacket(packet);
            manager.receiveWidgetUpdate("slaughterhouseToggle:0", false, 1, packet);
            helper.assertTrue(machine.isSimulatingLoot(), "Native C2S mode click did not reach the server controller");
            var saved = machine.saveWithoutMetadata(level.registryAccess()); machine.onUnload(); level.removeBlockEntity(machine.getBlockPos());
            var restored = (SlaughterhouseMachine) NativeSlaughterhouseFixtures.SLAUGHTERHOUSE.getBlockEntityType().create(machine.getBlockPos(), machine.getBlockState());
            restored.loadAdditional(saved, level.registryAccess()); level.setBlockEntity(restored);
            helper.assertTrue(restored.isSimulatingLoot(), "Native SaveField reload lost the slaughterhouse mode");
            packet.clear(); new MouseData(Dist.DEDICATED_SERVER, 0, false, false, false).writeToPacket(packet);
            manager.receiveWidgetUpdate("slaughterhouseToggle:0", false, 1, packet);
            helper.assertTrue(machine.isSimulatingLoot(), "Invalidated controller accepted an obsolete mode click");
        } finally { packet.release(); player.closeContainer(); level.removePlayerImmediately(player, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED); }
        helper.succeed();
    }
}
