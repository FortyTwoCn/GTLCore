package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.*;
import org.gtlcore.gtlcore.api.machine.multiblock.NoEnergyMultiblockMachine;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.multiblock.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.multiblock.util.AbstractStructureHelper;
import com.gregtechceu.gtceu.api.multiblock.util.BlockInfo;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeAeOutputGameTests {
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeOutputFilterPopupAndDataStickUseComponentAwareServerSync(GameTestHelper helper) {
        var pos = new BlockPos(4, 4, 4); helper.setBlock(pos, NativeAeGridFixtures.FILTERED_OUTPUT.getBlock().defaultBlockState());
        var host = (MEExtendedOutputPartMachine) helper.getBlockEntity(pos);
        var player = FakePlayerFactory.getMinecraft(helper.getLevel()); player.setPos(host.getBlockPos().getCenter());
        var settings = new brachy.modularui.screen.UISettings(brachy.modularui.api.RecipeViewerSettings.DUMMY);
        var sync = new brachy.modularui.value.sync.ModularSyncManager(false);
        var manager = new brachy.modularui.value.sync.PanelSyncManager(sync, true);
        var data = new brachy.modularui.factory.PosGuiData(player, host.getBlockPos());
        var panel = com.gregtechceu.gtceu.api.mui.factory.MachineUIFactory.INSTANCE.createPanel(data, manager, settings);
        brachy.modularui.widget.WidgetTree.collectSyncValues(manager, panel);
        var menu = new brachy.modularui.screen.ModularContainerMenu(41); menu.construct(player, sync, settings, panel.getName(), data);
        var popup = (brachy.modularui.value.sync.PanelSyncHandler) manager.findPanelHandlerNullable("extendedOutputFilters");
        popup.openPanel(); var filters = sync.getPanelSyncManager("extendedOutputFilterPanel");
        var itemSlot = filters.findSyncHandler("outputItemFilter", 26, brachy.modularui.value.sync.PhantomItemSlotSyncHandler.class);
        var fluidSlot = filters.findSyncHandler("outputFluidFilter", 25, brachy.modularui.value.sync.FluidSlotSyncHandler.class);
        helper.assertTrue(itemSlot.isPhantom() && fluidSlot.phantom() && !fluidSlot.controlsAmount() && menu.slots.size() == 36,
                "Production filter popup exposed consumable vanilla inventory slots");
        var packet = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(packet, item());
            filters.receiveWidgetUpdate("outputItemFilter:26", false, brachy.modularui.value.sync.PhantomItemSlotSyncHandler.SYNC_ITEM_SIMPLE, packet);
            fluidSlot.setValue(fluid(), true, false);
            packet.clear(); packet.writeBoolean(false); filters.receiveWidgetUpdate("itemIgnoreComponents:0", false, 0, packet);
            packet.clear(); packet.writeBoolean(false); filters.receiveWidgetUpdate("fluidIgnoreComponents:0", false, 0, packet);
            packet.clear(); brachy.modularui.utils.NetworkUtils.writeStringSafe(packet, "678", Short.MAX_VALUE - 74);
            filters.receiveWidgetUpdate("filterPriority:0", false, 0, packet);
            helper.assertTrue(host.getPriority() == 678 && !host.getFilterHandler().isIgnoreItemNbt() && !host.getFilterHandler().isIgnoreFluidNbt() &&
                    AEItemKey.of(item()).equals(host.getFilterHandler().getItemFilter(26)) && AEFluidKey.of(fluid()).equals(host.getFilterHandler().getFluidFilter(25)),
                    "Native filter edit/priority packets lost component configuration");
        } finally { packet.release(); }
        var stick = com.gregtechceu.gtceu.common.data.GTItems.TOOL_DATA_STICK.asStack();
        helper.assertTrue(host.onDataStickShiftUse(player, stick).consumesAction() && stick.has(DataComponents.CUSTOM_DATA), "Native data-stick copy did not create component data");
        var secondPos = pos.east(3); helper.setBlock(secondPos, NativeAeGridFixtures.FILTERED_OUTPUT.getBlock().defaultBlockState());
        var second = (MEExtendedOutputPartMachine) helper.getBlockEntity(secondPos);
        helper.assertTrue(second.onDataStickUse(player, stick).consumesAction() && second.getPriority() == 678 &&
                AEItemKey.of(item()).equals(second.getFilterHandler().getItemFilter(26)) && AEFluidKey.of(fluid()).equals(second.getFilterHandler().getFluidFilter(25)) &&
                !second.getFilterHandler().isIgnoreFluidNbt(), "Native data-stick paste lost filter or priority components");
        popup.closePanelInternal(); helper.succeed();
    }
    private static ItemStack item() { var stack = new ItemStack(Items.GOLD_INGOT); stack.set(DataComponents.CUSTOM_NAME, Component.literal("real AE output")); return stack; }
    private static FluidStack fluid() { var stack = new FluidStack(Fluids.WATER, 750); stack.set(DataComponents.CUSTOM_NAME, Component.literal("real AE output fluid")); return stack; }
    private static void connect(GameTestHelper helper, MEExtendedOutputPartMachineBase part) {
        part.setFrontFacing(Direction.NORTH);
        var pos = part.getBlockPos();
        helper.getLevel().setBlockAndUpdate(pos.north(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        helper.getLevel().setBlockAndUpdate(pos.north(2), AEBlocks.DRIVE.block().defaultBlockState());
        var drive = (DriveBlockEntity) helper.getLevel().getBlockEntity(pos.north(2));
        drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
        drive.getInternalInventory().setItemDirect(1, AEItems.FLUID_CELL_1K.stack());
    }
    private static NoEnergyMultiblockMachine build(GameTestHelper helper, boolean async) {
        var definition = NativeAeGridFixtures.OUTPUT_CONTROLLER.get();
        var pattern = (BlockPattern) definition.getStructurePatterns().get("main").get();
        var repetitions = new Int2IntOpenHashMap();
        for (int i = 0; i < pattern.getSlices().length; i++) repetitions.put(i, pattern.getSlices()[i].getMinRepeats());
        var preview = new LinkedHashMap<BlockPos, BlockInfo>();
        AbstractStructureHelper.blockPattern(repetitions).populate(preview, pattern, null, Direction.NORTH, Direction.UP, false);
        BlockPos controllerPos = null;
        for (var entry : preview.entrySet()) {
            var pos = new BlockPos(4, 4, 4).offset(entry.getKey()); var state = entry.getValue().getBlockState();
            if (async && !state.is(definition.getBlock())) state = NativeAeGridFixtures.ASYNC_OUTPUT.getBlock().defaultBlockState();
            helper.setBlock(pos, state); if (state.is(definition.getBlock())) controllerPos = pos;
        }
        var machine = (NoEnergyMultiblockMachine) helper.getBlockEntity(controllerPos);
        machine.setFrontFacing(Direction.NORTH); machine.checkAndFormStructure();
        helper.assertTrue(machine.isFormed() && machine.getParts().size() == 1, "Native controller did not form with the production AE output part");
        return machine;
    }
    private static void cycle(GameTestHelper helper, boolean async) {
        var machine = build(helper, async); var output = (MEExtendedOutputPartMachineBase) machine.getParts().getFirst();
        connect(helper, output);
        if (output instanceof MEExtendedOutputPartMachine filtered) {
            filtered.getFilterHandler().setItemFilter(0, AEItemKey.of(item())); filtered.getFilterHandler().updateItemFilterSettings(false, false);
            filtered.getFilterHandler().setFluidFilter(0, AEFluidKey.of(fluid())); filtered.getFilterHandler().updateFluidFilterSettings(false, false);
        }
        var inputs = machine.getTraits(NotifiableItemStackHandler.class).getFirst();
        var logic = machine.getRecipeLogic();
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(output.isOnline(), "Production AE output has no powered native network"))
                .thenExecute(() -> {
                    helper.assertTrue(((IRecipeCapabilityMachine) machine).getMEOutputRecipeHandleParts().size() == 1,
                            "Production output was not discovered by the native controller recipe routing");
                    inputs.storage.setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
                    var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_ae_output_" + async)
                            .inputItems(Items.IRON_INGOT).outputItems(item().copyWithCount(3)).outputFluids(fluid()).duration(3).build();
                    // This fixture isolates native output routing; powered controllers have separate physical-energy tests.
                    recipe.tickInputs.remove(com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability.CAP);
                    boolean available = logic.checkMatchedRecipeAvailable(recipe);
                    helper.assertTrue(available && inputs.getStackInSlot(0).isEmpty(), "Native setup rejected the output part or failed to consume one input: formed=" + machine.isFormed() +
                            " available=" + available + " input=" + inputs.getStackInSlot(0) + " status=" + logic.getStatus() +
                            " matched=" + com.gregtechceu.gtceu.api.recipe.RecipeHelper.matchContents(machine, recipe) +
                            " modified=" + machine.fullModifyRecipe(recipe));
                    logic.setSuspendAfterFinish(true);
                }).thenWaitUntil(() -> helper.assertTrue(logic.isSuspend(), "Native controller ticks did not finish the output recipe"))
                .thenWaitUntil(() -> {
                    var grid = output.getMainNode().getGrid();
                    helper.assertTrue(grid.getStorageService().getInventory().extract(AEItemKey.of(item()), 4, Actionable.SIMULATE, appeng.api.networking.security.IActionSource.empty()) == 3,
                            "Production AE output did not insert exact component-aware item output");
                    helper.assertTrue(grid.getStorageService().getInventory().extract(AEFluidKey.of(fluid()), 751, Actionable.SIMULATE, appeng.api.networking.security.IActionSource.empty()) == 750,
                            "Production AE output did not insert exact component-aware fluid output");
                }).thenIdle(20).thenExecute(() -> {
                    var storage = output.getMainNode().getGrid().getStorageService().getInventory();
                    helper.assertTrue(output.getBuffer().isEmpty() && storage.extract(AEItemKey.of(item()), 4, Actionable.MODULATE, appeng.api.networking.security.IActionSource.empty()) == 3 &&
                            storage.extract(AEFluidKey.of(fluid()), 751, Actionable.MODULATE, appeng.api.networking.security.IActionSource.empty()) == 750,
                            "Production output duplicated or retained already delivered output");
                }).thenSucceed();
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 500)
    public static void nativeFilteredOutputCompletesControllerRecipeOnActualAeStorage(GameTestHelper helper) { cycle(helper, false); }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 500)
    public static void nativeAsyncOutputCompletesControllerRecipeOnActualAeStorage(GameTestHelper helper) { cycle(helper, true); }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 500)
    public static void nativeAsyncOutputSaveBeforeDrainRecreatesTheCompletePendingQueue(GameTestHelper helper) {
        var pos = new BlockPos(4, 4, 4); var definition = NativeAeGridFixtures.ASYNC_OUTPUT;
        helper.setBlock(pos, definition.getBlock().defaultBlockState());
        var original = (MEExtendedAsyncOutputPartMachine) helper.getBlockEntity(pos);
        long amount = 9_007_199_254_740_993L;
        original.getItemOutputHandler().meHandleRecipeOutput(List.of(LongIngredient.create(item(), amount)), false);
        original.getFluidOutputHandler().meHandleRecipeOutput(List.of(LongFluidIngredient.create(fluid(), amount)), false);
        var registries = helper.getLevel().registryAccess(); var saved = original.saveWithoutMetadata(registries);
        var copy = (MEExtendedAsyncOutputPartMachine) definition.getBlockEntityType().create(original.getBlockPos(), original.getBlockState());
        helper.getLevel().removeBlockEntity(original.getBlockPos()); copy.loadAdditional(saved, registries); helper.getLevel().setBlockEntity(copy);
        helper.assertTrue(copy.getBuffer().getLong(AEItemKey.of(item())) == amount && copy.getBuffer().getLong(AEFluidKey.of(fluid())) == amount && original.getMainNode().getNode() == null,
                "Saving before an async drain lost long quantities, components or the old node");
        // Without cell storage, accepted output must remain queued even after a native AE node is active.
        copy.setFrontFacing(Direction.NORTH); helper.setBlock(pos.north(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(copy.isOnline(), "Restored async output did not reconnect"))
                .thenIdle(20).thenExecute(() -> helper.assertTrue(copy.getBuffer().getLong(AEItemKey.of(item())) == amount && copy.getBuffer().getLong(AEFluidKey.of(fluid())) == amount,
                        "A native powered grid without storage discarded queued output")).thenSucceed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeOutputMachineItemComponentsPreserveQueuesAndConfigurationOnReplacement(GameTestHelper helper) {
        for (int i = 0; i < 2; i++) {
            var definition = i == 0 ? NativeAeGridFixtures.FILTERED_OUTPUT : NativeAeGridFixtures.ASYNC_OUTPUT;
            var pos = new BlockPos(3 + i * 6, 4, 4); helper.setBlock(pos, definition.getBlock().defaultBlockState());
            var machine = (MEExtendedOutputPartMachineBase) helper.getBlockEntity(pos);
            machine.getItemOutputHandler().meHandleRecipeOutput(List.of(LongIngredient.create(item(), 9_007_199_254_740_993L)), false);
            machine.getFluidOutputHandler().meHandleRecipeOutput(List.of(LongFluidIngredient.create(fluid(), 9_007_199_254_740_993L)), false);
            machine.setPriority(889);
            if (machine instanceof MEExtendedOutputPartMachine filtered) {
                filtered.getFilterHandler().setItemFilter(26, AEItemKey.of(item())); filtered.getFilterHandler().updateItemFilterSettings(false, false);
            }
            var dropped = definition.asStack(); machine.saveToItem(dropped, helper.getLevel().registryAccess());
            helper.getLevel().removeBlockEntity(machine.getBlockPos());
            var restored = (MEExtendedOutputPartMachineBase) definition.getBlockEntityType().create(machine.getBlockPos(), machine.getBlockState());
            helper.getLevel().setBlockEntity(restored); restored.applyComponentsFromItemStack(dropped);
            helper.assertTrue(restored.getPriority() == 889 && restored.getBuffer().getLong(AEItemKey.of(item())) == 9_007_199_254_740_993L &&
                    restored.getBuffer().getLong(AEFluidKey.of(fluid())) == 9_007_199_254_740_993L,
                    "Native machine item components discarded accepted output or configuration");
            if (restored instanceof MEExtendedOutputPartMachine filtered) helper.assertTrue(AEItemKey.of(item()).equals(filtered.getFilterHandler().getItemFilter(26)) &&
                    !filtered.getFilterHandler().isIgnoreItemNbt(), "Native replacement lost filter components");
        }
        helper.succeed();
    }
}
