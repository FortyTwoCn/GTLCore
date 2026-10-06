package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.machine.multiblock.part.MEDualInputHatchPartMachine;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.mui.factory.MachineUIFactory;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEItemList;
import com.gregtechceu.gtceu.common.data.GTItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.*;
import brachy.modularui.value.sync.*;
import brachy.modularui.widget.WidgetTree;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.*;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeDualInputGameTests {
    private static final long LARGE = 9_007_199_254_740_993L;
    private static ExportOnlyAEItemList items(MEDualInputHatchPartMachine machine) { return (ExportOnlyAEItemList) machine.getInventory(); }
    private static ItemStack item() {
        var stack = new ItemStack(Items.IRON_INGOT); stack.set(DataComponents.CUSTOM_NAME, Component.literal("owned dual input item")); return stack;
    }
    private static FluidStack fluid() {
        var stack = new FluidStack(Fluids.WATER, 1); stack.set(DataComponents.CUSTOM_NAME, Component.literal("owned dual input fluid")); return stack;
    }
    private static MEDualInputHatchPartMachine build(GameTestHelper helper, boolean cells) {
        var relative = new BlockPos(4, 3, 5); helper.setBlock(relative, NativeAeGridFixtures.DUAL_INPUT.getBlock());
        var machine = (MEDualInputHatchPartMachine) helper.getBlockEntity(relative); machine.setFrontFacing(Direction.NORTH);
        var pos = machine.getBlockPos();
        helper.getLevel().setBlockAndUpdate(pos.north(), appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        if (cells) {
            helper.getLevel().setBlockAndUpdate(pos.north(2), appeng.core.definitions.AEBlocks.DRIVE.block().defaultBlockState());
            var drive = (appeng.blockentity.storage.DriveBlockEntity) helper.getLevel().getBlockEntity(pos.north(2));
            drive.getInternalInventory().setItemDirect(0, appeng.core.definitions.AEItems.ITEM_CELL_1K.stack());
            drive.getInternalInventory().setItemDirect(1, appeng.core.definitions.AEItems.FLUID_CELL_1K.stack());
        }
        machine.setMEUpdateInterval(1); return machine;
    }
    private static net.minecraft.server.level.ServerPlayer player(GameTestHelper helper, String name) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)), name));
    }
    private static PanelSyncManager menu(MEDualInputHatchPartMachine machine, net.minecraft.server.level.ServerPlayer player) {
        player.setPos(machine.getBlockPos().getCenter());
        var settings = new UISettings(brachy.modularui.api.RecipeViewerSettings.DUMMY); var sync = new ModularSyncManager(false);
        var manager = new PanelSyncManager(sync, true); var data = new PosGuiData(player, machine.getBlockPos());
        var panel = MachineUIFactory.INSTANCE.createPanel(data, manager, settings); WidgetTree.collectSyncValues(manager, panel);
        var container = new ModularContainerMenu(75); container.construct(player, sync, settings, panel.getName(), data); player.containerMenu = container;
        return manager;
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 200)
    public static void physicalMixedBuffersRefillFromRealCellsAndKeepRefusedReturnsAfterStorageRemoval(GameTestHelper helper) {
        var machine = build(helper, true); var itemKey = AEItemKey.of(item()); var fluidKey = AEFluidKey.of(fluid());
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(machine.isOnline(), "Buffered mixed input did not join its native powered grid"))
                .thenExecute(() -> {
            var storage = machine.getMainNode().getGrid().getStorageService().getInventory(); var source = IActionSource.empty();
            helper.assertTrue(storage.insert(itemKey, 37, Actionable.MODULATE, source) == 37 && storage.insert(fluidKey, 5500, Actionable.MODULATE, source) == 5500, "Real AE cells rejected physical buffer fixtures");
            items(machine).getInventory()[0].setConfig(new GenericStack(itemKey, 20));
            machine.aeFluidHandler.getInventory()[63].setConfig(new GenericStack(fluidKey, 3000)); machine.autoIO();
            helper.assertTrue(items(machine).getInventory()[0].getStock().amount() == 20 && machine.aeFluidHandler.getInventory()[63].getStock().amount() == 3000 &&
                    storage.extract(itemKey, Long.MAX_VALUE, Actionable.SIMULATE, source) == 17 && storage.extract(fluidKey, Long.MAX_VALUE, Actionable.SIMULATE, source) == 2500,
                    "Configured buffers did not own quantities extracted from actual AE cells");
            helper.assertTrue(items(machine).handleRecipeInner(IO.IN, null, new ArrayList<>(List.of(LongIngredient.create(item(), 5))), false).isEmpty() &&
                    machine.aeFluidHandler.handleRecipeInner(IO.IN, null, new ArrayList<>(List.of(LongFluidIngredient.create(fluid(), 500))), false).isEmpty(), "Buffered recipe did not consume owned inputs");
            machine.autoIO();
            helper.assertTrue(storage.extract(itemKey, Long.MAX_VALUE, Actionable.SIMULATE, source) == 12 && storage.extract(fluidKey, Long.MAX_VALUE, Actionable.SIMULATE, source) == 2000,
                    "Buffer refill duplicated or lost consumed quantities");
            helper.assertTrue(machine.aeFluidHandler.getInventory()[63].drain(new FluidStack(Fluids.WATER, 1), FluidAction.EXECUTE).isEmpty(), "Physical fluid capability ignored components");
            machine.setWorkingEnabled(false);
            var drive = (appeng.blockentity.storage.DriveBlockEntity) helper.getLevel().getBlockEntity(machine.getBlockPos().north(2));
            drive.getInternalInventory().setItemDirect(0, ItemStack.EMPTY); drive.getInternalInventory().setItemDirect(1, ItemStack.EMPTY);
        }).thenIdle(3).thenExecute(() -> {
            var storage = machine.getMainNode().getGrid().getStorageService().getInventory();
            helper.assertTrue(storage.insert(itemKey, 1, Actionable.SIMULATE, IActionSource.empty()) == 0, "Removed cell still accepts stock");
            items(machine).getInventory()[0].setConfig(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 7));
            machine.aeFluidHandler.getInventory()[63].setConfig(new GenericStack(AEFluidKey.of(Fluids.LAVA), 1000));
            machine.setWorkingEnabled(true); machine.autoIO();
            helper.assertTrue(new GenericStack(itemKey, 20).equals(items(machine).getInventory()[0].getStock()) &&
                    new GenericStack(fluidKey, 3000).equals(machine.aeFluidHandler.getInventory()[63].getStock()), "Refused old-key returns were discarded or replaced");
            machine.setWorkingEnabled(false);
        }).thenSucceed();
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 200)
    public static void nativeDropAndItemReplacementPreserveRefusedLongBuffersAndProviderAwareConfigurations(GameTestHelper helper) {
        var machine = build(helper, false); var owner = player(helper, "dual_input_drop");
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(machine.isOnline(), "No-storage input node did not become active"))
                .thenExecute(() -> {
            machine.setWorkingEnabled(false);
            var manager = menu(machine, owner); var packet = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                packet.writeVarInt(0).writeBoolean(false); ItemStack.STREAM_CODEC.encode(packet, item()); manager.receiveWidgetUpdate("dual_config_ghost", true, 0, packet);
                packet.clear(); packet.writeVarInt(0).writeVarLong(LARGE); manager.receiveWidgetUpdate("dual_config_amount", true, 0, packet);
                packet.clear(); packet.writeVarInt(63).writeBoolean(true); FluidStack.STREAM_CODEC.encode(packet, fluid()); manager.receiveWidgetUpdate("dual_config_ghost", true, 0, packet);
                packet.clear(); packet.writeVarInt(63).writeVarLong(LARGE + 7); manager.receiveWidgetUpdate("dual_config_amount", true, 0, packet);
            } finally { packet.release(); }
            machine.getCircuitSlot().setCurrentCircuit(14); machine.setDistinct(true); machine.setMEUpdateInterval(23); machine.setPage(4);
            items(machine).getInventory()[0].setStock(new GenericStack(AEItemKey.of(item()), LARGE));
            machine.aeFluidHandler.getInventory()[63].setStock(new GenericStack(AEFluidKey.of(fluid()), LARGE + 7));
            var stick = GTItems.TOOL_DATA_STICK.asStack(); var custom = new net.minecraft.nbt.CompoundTag(); custom.putString("unrelated", "retained"); stick.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
            machine.onDataStickShiftUse(owner, stick);
            var destination = new BlockPos(10, 3, 5); helper.setBlock(destination, NativeAeGridFixtures.DUAL_INPUT.getBlock());
            var configured = (MEDualInputHatchPartMachine) helper.getBlockEntity(destination); configured.onDataStickUse(owner, stick);
            helper.assertTrue(items(configured).getInventory()[0].getConfig().amount() == LARGE && configured.aeFluidHandler.getInventory()[63].getConfig().amount() == LARGE + 7 &&
                    configured.getCircuitSlot().getCurrentCircuit() == 14 && configured.getMEUpdateInterval() == 23 && configured.isDistinct() &&
                    "retained".equals(stick.get(DataComponents.CUSTOM_DATA).copyTag().getString("unrelated")), "Provider-aware data-stick copy lost long configs or unrelated components");
            helper.getLevel().destroyBlock(machine.getBlockPos(), true);
            var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(machine.getBlockPos()).inflate(2)).stream()
                    .filter(entity -> entity.getItem().is(NativeAeGridFixtures.DUAL_INPUT.asStack().getItem())).toList();
            helper.assertTrue(drops.size() == 1, "Native block destruction did not produce exactly one machine item");
            var dropped = drops.getFirst().getItem().copy(); drops.getFirst().discard();
            helper.setBlock(new BlockPos(7, 3, 9), NativeAeGridFixtures.DUAL_INPUT.getBlock());
            var restored = (MEDualInputHatchPartMachine) helper.getBlockEntity(new BlockPos(7, 3, 9)); restored.applyComponentsFromItemStack(dropped);
            helper.assertTrue(new GenericStack(AEItemKey.of(item()), LARGE).equals(items(restored).getInventory()[0].getStock()) &&
                    new GenericStack(AEFluidKey.of(fluid()), LARGE + 7).equals(restored.aeFluidHandler.getInventory()[63].getStock()) &&
                    restored.getCircuitSlot().getCurrentCircuit() == 14 && restored.getMEUpdateInterval() == 23 && restored.isDistinct(), "Native dropped machine lost owned long buffers or settings");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(machine.getBlockPos()).inflate(2)).stream()
                    .noneMatch(entity -> entity.getItem().is(Items.IRON_INGOT)), "Native destruction duplicated retained stock into separate item drops");
        }).thenSucceed();
    }
}
