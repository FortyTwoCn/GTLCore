package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.pattern.DebugBlockPattern;
import org.gtlcore.gtlcore.common.item.StructureWriteBehavior;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.multiblock.Predicates;
import com.gregtechceu.gtceu.api.multiblock.pattern.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import brachy.modularui.factory.PlayerInventoryGuiData;
import brachy.modularui.factory.PlayerInventoryUIFactory;
import brachy.modularui.factory.inventory.InventoryTypes;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.*;
import brachy.modularui.widget.WidgetTree;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.UUID;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeStructureWriterGameTests {
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void offhandSelectionNativeRotationPacketAndSaveKeepThePhysicalToolIndependent(GameTestHelper helper) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.fromString("c5a27077-9392-4b63-bb97-e9b0c6c024c6"), "structure_writer"));
        player.getInventory().clearContent(); var item = NativeAePatternFixtures.STRUCTURE_WRITER.asStack();
        var other = item.copy(); var unrelated = new net.minecraft.nbt.CompoundTag(); unrelated.putString("unrelated", "kept");
        item.set(DataComponents.CUSTOM_DATA, CustomData.of(unrelated)); player.getInventory().setItem(40, item);
        player.getInventory().setItem(0, new ItemStack(Items.APPLE));
        var min = helper.absolutePos(new BlockPos(1, 2, 3)); var max = min.offset(2, 1, 3);
        StructureWriteBehavior.INSTANCE.onItemUseFirst(item, new UseOnContext(player, InteractionHand.OFF_HAND,
                new BlockHitResult(Vec3.atCenterOf(max), Direction.UP, max, false)));
        StructureWriteBehavior.INSTANCE.onItemUseFirst(item, new UseOnContext(player, InteractionHand.OFF_HAND,
                new BlockHitResult(Vec3.atCenterOf(min), Direction.UP, min, false)));
        var data = PlayerInventoryGuiData.of(player, InventoryTypes.PLAYER, null, 40);
        var sync = new PanelSyncManager(new ModularSyncManager(false), true); data.addSyncHandlers(sync);
        var panel = PlayerInventoryUIFactory.INSTANCE.createPanel(data, sync, new UISettings(brachy.modularui.api.RecipeViewerSettings.DUMMY));
        WidgetTree.collectSyncValues(sync, panel); var packet = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            packet.writeVarInt(0); packet.writeByte(0); sync.receiveWidgetUpdate("structureWriter_rotate_y:0", false, 1, packet);
            helper.assertTrue(StructureWriteBehavior.getDir(item) == Direction.NORTH, "Server rotation did not update the offhand tool");
            var pos = StructureWriteBehavior.getPos(item);
            helper.assertTrue(pos[0].equals(min) && pos[1].equals(max), "Selection bounds changed during rotation");
            var loaded = ItemStack.parse(helper.getLevel().registryAccess(), item.save(helper.getLevel().registryAccess())).orElseThrow();
            helper.assertTrue(loaded.getComponentsPatch().equals(item.getComponentsPatch()) &&
                    StructureWriteBehavior.getDir(loaded) == Direction.NORTH, "Native item save lost rotation or selection");
            helper.assertTrue(StructureWriteBehavior.getPos(other) == null && !other.has(DataComponents.CUSTOM_DATA) &&
                    player.getMainHandItem().is(Items.APPLE), "Reading/rotating the tool mutated another item");
            player.setShiftKeyDown(true); StructureWriteBehavior.INSTANCE.use(item, helper.getLevel(), player, InteractionHand.OFF_HAND); player.setShiftKeyDown(false);
            helper.assertTrue(StructureWriteBehavior.getPos(item) == null && StructureWriteBehavior.getDir(item) == Direction.NORTH &&
                    item.get(DataComponents.CUSTOM_DATA).copyTag().getString("unrelated").equals("kept"), "Reset removed unrelated data or rotation");
            var unavailable = new BlockPos(16384, min.getY(), 16384);
            StructureWriteBehavior.addPos(item, unavailable);
            try { StructureWriteBehavior.createExport(helper.getLevel(), item); helper.fail("Export accepted an unloaded chunk"); }
            catch (IllegalStateException expected) { helper.assertTrue(!helper.getLevel().hasChunkAt(unavailable), "Export loaded an unavailable chunk"); }
        } finally { packet.release(); player.setShiftKeyDown(false); }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void allSixExportDirectionsMatchTheCapturedAsymmetricWorldUsingNativeBuilder(GameTestHelper helper) {
        var min = helper.absolutePos(new BlockPos(1, 2, 1)); var max = min.offset(2, 1, 3);
        for (int x = 0; x < 3; x++) for (int y = 0; y < 2; y++) for (int z = 0; z < 4; z++)
            helper.getLevel().setBlock(min.offset(x, y, z), (x == 0 ? Blocks.GOLD_BLOCK : y == 0 ? Blocks.IRON_BLOCK : Blocks.DIAMOND_BLOCK).defaultBlockState(), 3);
        var center = min.offset(1, 1, 2);
        helper.getLevel().setBlock(center, NativeAePatternFixtures.CONTROLLER.getBlock().defaultBlockState(), 3);
        var controller = (MultiblockControllerMachine) helper.getLevel().getBlockEntity(center);
        controller.setFrontFacing(Direction.NORTH);
        var captured = new DebugBlockPattern(helper.getLevel(), min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ());
        helper.assertTrue(captured.hasController(), "Captured pattern omitted its actual native controller");
        for (var facing : Direction.values()) {
            var rotated = captured.copy(); var dirs = DebugBlockPattern.getDir(facing); rotated.changeDir(dirs[0], dirs[1], dirs[2]);
            var builder = MultiblockPatternBuilder.start(dirs[2], dirs[1], dirs[0]);
            for (var slice : rotated.pattern) builder.slice(slice);
            builder.where('~', Predicates.controller(NativeAePatternFixtures.CONTROLLER.get()));
            rotated.legend.forEach((block, symbol) -> { if (symbol != ' ') builder.where(symbol, Predicates.blocks(block)); });
            var pattern = (BlockPattern) builder.build(); var state = new PatternState(); state.setController(controller, center);
            helper.assertTrue(pattern.checkPatternAt(helper.getLevel(), state, center, Direction.NORTH, Direction.UP, false),
                    "Export orientation " + facing + " changed the captured world geometry");
            helper.assertTrue(state.getCache().size() == 24, "Export orientation " + facing + " lost captured positions");
        }
        helper.succeed();
    }
}
