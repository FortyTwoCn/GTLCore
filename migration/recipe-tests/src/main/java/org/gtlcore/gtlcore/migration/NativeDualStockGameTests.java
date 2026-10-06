package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.machine.trait.MEStock.IOptimizedMEList;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import org.gtlcore.gtlcore.common.machine.multiblock.part.MEDualHatchStockPartMachine;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.mui.factory.MachineUIFactory;
import com.gregtechceu.gtceu.common.data.item.GTDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import appeng.api.config.Actionable;
import appeng.api.stacks.*;
import appeng.api.networking.security.IActionSource;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.*;
import brachy.modularui.value.sync.*;
import brachy.modularui.widget.WidgetTree;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeDualStockGameTests {
    private static MEDualHatchStockPartMachine build(GameTestHelper helper) {
        var relative = new BlockPos(4, 3, 5); helper.setBlock(relative, NativeAeGridFixtures.DUAL_STOCK.getBlock());
        var machine = (MEDualHatchStockPartMachine) helper.getBlockEntity(relative); machine.setFrontFacing(net.minecraft.core.Direction.NORTH);
        var pos = machine.getBlockPos();
        helper.getLevel().setBlockAndUpdate(pos.north(), appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        helper.getLevel().setBlockAndUpdate(pos.north(2), appeng.core.definitions.AEBlocks.DRIVE.block().defaultBlockState());
        var drive = (appeng.blockentity.storage.DriveBlockEntity) helper.getLevel().getBlockEntity(pos.north(2));
        drive.getInternalInventory().setItemDirect(0, appeng.core.definitions.AEItems.ITEM_CELL_1K.stack());
        drive.getInternalInventory().setItemDirect(1, appeng.core.definitions.AEItems.FLUID_CELL_1K.stack());
        machine.setMEUpdateInterval(1); return machine;
    }
    private static net.minecraft.server.level.ServerPlayer player(GameTestHelper helper, String name) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)), name));
        player.getInventory().clearContent(); return player;
    }
    private static ItemStack item() {
        var item = new ItemStack(Items.IRON_INGOT); item.set(DataComponents.CUSTOM_NAME, Component.literal("dual stocking item")); return item;
    }
    private static FluidStack fluid() {
        var fluid = new FluidStack(Fluids.WATER, 1); fluid.set(DataComponents.CUSTOM_NAME, Component.literal("dual stocking fluid")); return fluid;
    }
    private static PanelSyncManager menu(GameTestHelper helper, MEDualHatchStockPartMachine machine, net.minecraft.server.level.ServerPlayer player) {
        player.setPos(machine.getBlockPos().getCenter());
        var settings = new UISettings(brachy.modularui.api.RecipeViewerSettings.DUMMY); var sync = new ModularSyncManager(false);
        var manager = new PanelSyncManager(sync, true); var data = new PosGuiData(player, machine.getBlockPos());
        var panel = MachineUIFactory.INSTANCE.createPanel(data, manager, settings); WidgetTree.collectSyncValues(manager, panel);
        var container = new ModularContainerMenu(72); container.construct(player, sync, settings, panel.getName(), data);
        player.containerMenu = container; return manager;
    }
    private static void action(PanelSyncManager manager, RegistryFriendlyByteBuf packet, String key, Consumer<RegistryFriendlyByteBuf> data) {
        packet.clear(); data.accept(packet); manager.receiveWidgetUpdate(key, true, 0, packet);
    }
    private static void ghost(PanelSyncManager manager, RegistryFriendlyByteBuf packet, int index, Object ingredient) {
        action(manager, packet, "dual_config_ghost", buf -> {
            buf.writeVarInt(index); buf.writeBoolean(ingredient instanceof FluidStack);
            if (ingredient instanceof FluidStack fluid) FluidStack.STREAM_CODEC.encode(buf, fluid);
            else ItemStack.STREAM_CODEC.encode(buf, (ItemStack) ingredient);
        });
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 200)
    public static void mixedStockingUsesRealCellsForReservedSimulationPartialConsumptionAndStoppedGates(GameTestHelper helper) {
        var machine = build(helper); var player = player(helper, "dual_stock_transactions");
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(machine.isOnline(), "Dual stocking hatch did not join its powered native ME network"))
                .thenExecute(() -> {
            var storage = machine.getMainNode().getGrid().getStorageService().getInventory(); var source = IActionSource.ofPlayer(player);
            var itemKey = AEItemKey.of(item()); var fluidKey = AEFluidKey.of(fluid());
            helper.assertTrue(storage.insert(itemKey, 37, Actionable.MODULATE, source) == 37 &&
                    storage.insert(fluidKey, 5500, Actionable.MODULATE, source) == 5500, "Real AE cells rejected test quantities");
            var manager = menu(helper, machine, player); var packet = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                ghost(manager, packet, 0, item()); ghost(manager, packet, 63, fluid().copyWithAmount(1000)); machine.autoIO();
                helper.assertTrue(machine.aeItemHandler.getInventory()[0].getConfig().what().equals(itemKey) &&
                        machine.aeFluidHandler.getInventory()[63].getConfig().what().equals(fluidKey), "Native mixed GUI lost component keys or the last of 64 slots");
                var items = new ArrayList<>(List.of(LongIngredient.create(item(), 20), LongIngredient.create(item(), 20)));
                machine.aeItemHandler.handleRecipeInner(IO.IN, null, items, true);
                var fluids = new ArrayList<>(List.of(LongFluidIngredient.create(fluid(), 3000), LongFluidIngredient.create(fluid(), 3000)));
                machine.aeFluidHandler.handleRecipeInner(IO.IN, null, fluids, true);
                helper.assertTrue(items.size() == 1 && LongIngredient.amount(items.getFirst()) == 3 &&
                        fluids.size() == 1 && LongFluidIngredient.amount(fluids.getFirst()) == 500, "Overlapping simulation reused the same network stock");
                helper.assertTrue(storage.extract(itemKey, Long.MAX_VALUE, Actionable.SIMULATE, source) == 37 &&
                        storage.extract(fluidKey, Long.MAX_VALUE, Actionable.SIMULATE, source) == 5500, "Simulation consumed real AE cell contents");
                machine.aeItemHandler.handleRecipeInner(IO.IN, null, new ArrayList<>(List.of(LongIngredient.create(item(), 5))), false);
                machine.aeFluidHandler.handleRecipeInner(IO.IN, null, new ArrayList<>(List.of(LongFluidIngredient.create(fluid(), 1500))), false);
                helper.assertTrue(((IOptimizedMEList) machine.aeItemHandler).getMEItemMap().getLong(item()) == 32 &&
                        ((IOptimizedMEList) machine.aeFluidHandler).getMEFluidMap().getLong(fluid()) == 4000, "Live quantity snapshots did not follow recipe extraction");
                machine.setWorkingEnabled(false);
                helper.assertTrue(machine.aeItemHandler.getInventory()[0].extractItem(0, 1, false).isEmpty() &&
                        machine.aeFluidHandler.getInventory()[63].drain(100, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE).isEmpty() &&
                        ((IOptimizedMEList) machine.aeItemHandler).getMEItemMap() == null &&
                        ((IOptimizedMEList) machine.aeFluidHandler).getMEFluidMap().isEmpty(), "Stopped hatch still exposed or extracted live ME stock");
                machine.setWorkingEnabled(true); machine.autoIO();
                var slot = machine.aeFluidHandler.getInventory()[63];
                helper.assertTrue(slot.drain(new FluidStack(Fluids.WATER, 500), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE).isEmpty(), "Fluid drain ignored the configured components");
                helper.assertTrue(slot.drain(fluid().copyWithAmount(500), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE).getAmount() == 500 &&
                        machine.aeItemHandler.getInventory()[0].extractItem(0, 1, false).getCount() == 1, "Native item/fluid slot extraction failed");
                helper.assertTrue(machine.aeItemHandler.extractItem(0, 1, false).isEmpty(), "Stocking bus exposed its virtual inventory to external item extraction");
                helper.assertTrue(storage.extract(itemKey, Long.MAX_VALUE, Actionable.SIMULATE, source) == 31 &&
                        storage.extract(fluidKey, Long.MAX_VALUE, Actionable.SIMULATE, source) == 3500, "Actual capability extraction was duplicated or lost");
            } finally { packet.release(); player.containerMenu = player.inventoryMenu; }
        }).thenSucceed();
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 240)
    public static void lastPageNativeConfigurationAndDataStickSurviveBlockEntityRecreation(GameTestHelper helper) {
        var machine = build(helper); var player = player(helper, "dual_stock_save"); var restored = new AtomicReference<MEDualHatchStockPartMachine>();
        var stick = new ItemStack(Items.PAPER); var unrelated = new net.minecraft.nbt.CompoundTag(); unrelated.putString("unrelated", "kept");
        stick.set(DataComponents.CUSTOM_DATA, CustomData.of(unrelated));
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(machine.isOnline(), "Dual stocking save source grid is offline")).thenExecute(() -> {
            var manager = menu(helper, machine, player); var packet = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                ghost(manager, packet, 0, item()); ghost(manager, packet, 63, fluid().copyWithAmount(1000));
                packet.clear(); packet.writeVarInt(99999); manager.receiveWidgetUpdate("dualConfigPage:0", false, 0, packet);
                helper.assertTrue(machine.getPage() == 4, "Native page packet was not bounded to all four pages");
                ghost(manager, packet, 64, new ItemStack(Items.DIAMOND));
                ghost(manager, packet, 1, item());
                helper.assertTrue(machine.aeItemHandler.getInventory()[1].getConfig() == null, "Native ghost action accepted duplicate configuration");
                machine.getCircuitSlot().setCurrentCircuit(17); machine.setMEUpdateInterval(23);
                machine.onDataStickShiftUse(player, stick);
                helper.assertTrue(stick.has(GTDataComponents.DATA_COPY_TAG) && stick.get(DataComponents.CUSTOM_DATA).copyTag().getString("unrelated").equals("kept"), "Data stick overwrote unrelated native item components");
                machine.setWorkingEnabled(false);
                var registry = helper.getLevel().registryAccess(); var saved = machine.saveWithoutMetadata(registry); var pos = machine.getBlockPos();
                var copy = (MEDualHatchStockPartMachine) NativeAeGridFixtures.DUAL_STOCK.getBlockEntityType().create(pos, machine.getBlockState());
                helper.getLevel().removeBlockEntity(pos); copy.loadAdditional(saved, registry); helper.getLevel().setBlockEntity(copy); restored.set(copy);
            } finally { packet.release(); player.containerMenu = player.inventoryMenu; }
        }).thenWaitUntil(() -> helper.assertTrue(restored.get().isOnline(), "Reloaded mixed hatch did not restore its native ME node")).thenExecute(() -> {
            var copy = restored.get();
            helper.assertTrue(copy.getPage() == 4 && copy.getMEUpdateInterval() == 23 && copy.getCircuitSlot().getCurrentCircuit() == 17 &&
                    !copy.isWorkingEnabled() && copy.aeItemHandler.getInventory()[0].getConfig().what().equals(AEItemKey.of(item())) &&
                    copy.aeFluidHandler.getInventory()[63].getConfig().what().equals(AEFluidKey.of(fluid())), "Native world save lost paired slots, components, page, circuit, interval or stopped state");
            copy.aeItemHandler.getInventory()[0].setConfig(null); copy.aeFluidHandler.getInventory()[63].setConfig(null);
            copy.getCircuitSlot().setCurrentCircuit(0); copy.setMEUpdateInterval(1); copy.onDataStickUse(player, stick);
            helper.assertTrue(copy.getCircuitSlot().getCurrentCircuit() == 17 && copy.getMEUpdateInterval() == 23 &&
                    copy.aeFluidHandler.getInventory()[63].getConfig().what().equals(AEFluidKey.of(fluid())) &&
                    copy.aeItemHandler.getInventory()[0].getConfig().what().equals(AEItemKey.of(item())), "Data stick did not restore native mixed configurations");
        }).thenSucceed();
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 200)
    public static void allFourAutoPullModesReferenceCellsWithoutOwningOrDroppingTheVisualStock(GameTestHelper helper) {
        var machine = build(helper); var player = player(helper, "dual_stock_modes");
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(machine.isOnline(), "Dual auto-pull source grid is offline")).thenExecute(() -> {
            var storage = machine.getMainNode().getGrid().getStorageService().getInventory(); var source = IActionSource.ofPlayer(player);
            var itemKey = AEItemKey.of(item()); var gold = AEItemKey.of(Items.GOLD_INGOT); var fluidKey = AEFluidKey.of(fluid());
            storage.insert(itemKey, 6, Actionable.MODULATE, source); storage.insert(gold, 8, Actionable.MODULATE, source); storage.insert(fluidKey, 7500, Actionable.MODULATE, source);
            machine.setTerminalAutoPullMode(1);
            helper.assertTrue(configured(machine, true) == 2 && configured(machine, false) == 1, "ALL mode omitted an item or fluid");
            machine.setTerminalAutoPullMode(2);
            helper.assertTrue(configured(machine, true) == 2 && configured(machine, false) == 0, "Item-only mode retained fluid configuration");
            machine.setTerminalAutoPullMode(3);
            helper.assertTrue(configured(machine, true) == 0 && configured(machine, false) == 1, "Fluid-only mode retained item configuration");
            machine.setTerminalAutoPullMode(0);
            helper.assertTrue(configured(machine, true) == 0 && configured(machine, false) == 0, "OFF mode did not clear auto-pull configuration");
            machine.setTerminalAutoPullMode(1);
            helper.getLevel().setBlockAndUpdate(machine.getBlockPos(), Blocks.AIR.defaultBlockState());
            helper.assertEntityNotPresent(net.minecraft.world.entity.EntityType.ITEM);
            helper.assertTrue(storage.extract(itemKey, Long.MAX_VALUE, Actionable.SIMULATE, source) == 6 &&
                    storage.extract(gold, Long.MAX_VALUE, Actionable.SIMULATE, source) == 8 &&
                    storage.extract(fluidKey, Long.MAX_VALUE, Actionable.SIMULATE, source) == 7500, "Mode change or hatch destruction modified the cell contents");
        }).thenSucceed();
    }
    private static int configured(MEDualHatchStockPartMachine machine, boolean item) {
        var slots = item ? machine.aeItemHandler.getInventory() : machine.aeFluidHandler.getInventory();
        int count = 0; for (var slot : slots) if (slot.getConfig() != null) count++; return count;
    }
}
