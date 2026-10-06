package org.gtlcore.gtlcore.migration;

import com.gtladd.gtladditions.integration.ae2.*;
import com.gtladd.gtladditions.common.machine.multiblock.part.MESuperPatternBufferPartMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.PaginationUIManager;
import org.gtlcore.gtlcore.integration.ae2.widget.AEPatternViewExtendSlotWidget;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.item.GTDataComponents;
import com.gregtechceu.gtceu.utils.ResearchManager;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import brachy.modularui.value.sync.*;
import brachy.modularui.widgets.PagedWidget;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.api.stacks.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class NativeSuperPatternIntegrationTest {
    private static final long LARGE = 9_007_199_254_740_993L;
    private static ItemStack item(Item item, String name) { var value = new ItemStack(item); value.set(DataComponents.CUSTOM_NAME, Component.literal(name)); return value; }
    private static AEProcessingPattern pattern(List<GenericStack> inputs, List<GenericStack> outputs) {
        return org.gtlcore.gtlcore.api.item.tool.ae2.patternTool.Ae2BaseProcessingPatternHelper.decodeToAEProcessingPattern(PatternDetailsHelper.encodeProcessingPattern(inputs, outputs), null);
    }
    private static long amount(List<GenericStack> values, AEKey key) { return values.stream().filter(Objects::nonNull).filter(value -> value.what().equals(key)).mapToLong(GenericStack::amount).sum(); }
    @Test void nativeFOATransformationKeepsComponentKeysAndScalesOnlyTheCorrectCycleSideWithExactLongSaturation() {
        var cell = AEItemKey.of(item(NativeSuperPatternFixtures.TIME_CELL.get(), "owned returnable cell")); var input = AEItemKey.of(item(Items.COPPER_INGOT, "component input"));
        var output = AEItemKey.of(item(Items.GOLD_INGOT, "component output")); var fluid = new FluidStack(Fluids.WATER, 1); fluid.set(DataComponents.CUSTOM_NAME, Component.literal("component FOA fluid")); var fluidKey = AEFluidKey.of(fluid);
        var original = pattern(List.of(new GenericStack(input, 2), new GenericStack(cell, 3), new GenericStack(fluidKey, 7)),
                List.of(new GenericStack(output, LARGE), new GenericStack(cell, 3), new GenericStack(fluidKey, Long.MAX_VALUE)));
        var adjusted = (AEProcessingPattern) MEBufferPatternHelperExtensions.rewriteForgeOfTheAntichristPattern(original, null, 15, null);
        assertEquals(2, amount(adjusted.getSparseInputs(), input)); assertEquals(45, amount(adjusted.getSparseInputs(), cell)); assertEquals(7, amount(adjusted.getSparseInputs(), fluidKey));
        assertEquals(LARGE * 15, amount(adjusted.getSparseOutputs(), output)); assertEquals(3, amount(adjusted.getSparseOutputs(), cell)); assertEquals(Long.MAX_VALUE, amount(adjusted.getSparseOutputs(), fluidKey));
        assertEquals(3, amount(original.getSparseInputs(), cell)); assertEquals(LARGE, amount(original.getSparseOutputs(), output));
        assertSame(original, MEBufferPatternHelperExtensions.rewriteForgeOfTheAntichristPattern(original, null, -5, null));
    }
    @Test void nativeSpecialContainerMergesIntoFullPatternsAndNeverAdvertisesScaledOutputsWithoutItsRequiredExtraInput() {
        var cell = AEItemKey.of(NativeSuperPatternFixtures.TIME_CELL.asStack()); var output = AEItemKey.of(Items.GOLD_INGOT);
        var inputs = new ArrayList<GenericStack>(); inputs.add(new GenericStack(cell, 1));
        for (int index = 1; index < AEProcessingPattern.MAX_INPUT_SLOTS; index++) inputs.add(new GenericStack(AEItemKey.of(item(Items.COPPER_INGOT, "full input " + index)), 1));
        var original = pattern(inputs, List.of(new GenericStack(output, 2))); var extra = new GenericStack(cell, 1);
        var adjusted = (AEProcessingPattern) MEBufferPatternHelperExtensions.rewriteForgeOfTheAntichristPattern(original, null, 999, extra);
        assertEquals(30, amount(adjusted.getSparseInputs(), cell)); assertEquals(60, amount(adjusted.getSparseOutputs(), output)); assertEquals(AEProcessingPattern.MAX_INPUT_SLOTS, adjusted.getInputs().length);
        var missingCell = new GenericStack(AEItemKey.of(NativeSuperPatternFixtures.DURABLE_CELL.asStack()), 1);
        assertSame(original, MEBufferPatternHelperExtensions.rewriteForgeOfTheAntichristPattern(original, null, 15, missingCell));
        assertEquals(1, amount(original.getSparseInputs(), cell));
    }
    @Test void nativeSuperDataSticksPreserveOtherComponentsAndRejectWrongDimensionsMalformedPositionsAndResearchData() {
        var stick = GTItems.TOOL_DATA_STICK.asStack(); CustomData.update(DataComponents.CUSTOM_DATA, stick, tag -> tag.putString("other", "retained")); stick.set(GTDataComponents.DATA_COPY_POS, BlockPos.ZERO);
        var target = new BlockPos(-27, -64, 2000000); SuperPatternDataStick.write(stick, target, Level.OVERWORLD.location());
        assertFalse(stick.has(GTDataComponents.DATA_COPY_POS)); assertEquals(target, SuperPatternDataStick.read(stick, Level.OVERWORLD.location())); assertNull(SuperPatternDataStick.read(stick, Level.NETHER.location()));
        assertEquals("retained", stick.get(DataComponents.CUSTOM_DATA).copyTag().getString("other"));
        CustomData.update(DataComponents.CUSTOM_DATA, stick, tag -> tag.putIntArray("superPos", new int[]{1, 2})); assertNull(SuperPatternDataStick.read(stick, Level.OVERWORLD.location()));
        SuperPatternDataStick.write(stick, target, Level.OVERWORLD.location()); stick.set(GTDataComponents.RESEARCH_ITEM, new ResearchManager.ResearchItem("protected research", GTRecipeTypes.CHEMICAL_RECIPES.get()));
        var before = stick.copy(); SuperPatternDataStick.write(stick, BlockPos.ZERO, Level.NETHER.location()); SuperPatternDataStick.clear(stick); assertTrue(ItemStack.isSameItemSameComponents(before, stick)); assertNull(SuperPatternDataStick.read(stick, Level.OVERWORLD.location()));
        stick.remove(GTDataComponents.RESEARCH_ITEM); SuperPatternDataStick.clear(stick); assertNull(SuperPatternDataStick.read(stick, Level.OVERWORLD.location())); assertEquals("retained", stick.get(DataComponents.CUSTOM_DATA).copyTag().getString("other"));
    }
    @Test void actualNativeSuperPartPersistsFOAModeBoundedMultiplierLastPatternAndExactOwnedQuantities() {
        var definition = NativeSuperPatternFixtures.BUFFER; var registry = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var part = (MESuperPatternBufferPartMachine) definition.getBlockEntityType().create(BlockPos.ZERO, definition.getBlock().defaultBlockState());
        assertEquals(162, part.getPatternInventory().getSlots()); assertEquals(15, part.getFOAPatternOutputMultiplier()); assertFalse(part.isFOAModeEnabled());
        part.setFOAModeEnabled(true); part.setFOAPatternOutputMultiplier(999); assertEquals(30, part.getFOAPatternOutputMultiplier()); part.setFOAPatternOutputMultiplier(-1); assertEquals(1, part.getFOAPatternOutputMultiplier()); part.setFOAPatternOutputMultiplier(17);
        var key = AEItemKey.of(item(Items.COPPER_INGOT, "saved native super input"));
        var encoded = PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(key, 1)), List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1))); part.getTerminalPatternInventory().setItemDirect(161, encoded);
        part.getInternalInventory()[161].add(key, LARGE);
        part.getBuffer().put(key, LARGE);
        var copy = (MESuperPatternBufferPartMachine) definition.getBlockEntityType().create(BlockPos.ZERO, definition.getBlock().defaultBlockState()); copy.loadAdditional(part.saveWithoutMetadata(registry), registry);
        assertTrue(copy.isFOAModeEnabled()); assertEquals(17, copy.getFOAPatternOutputMultiplier()); assertTrue(ItemStack.isSameItemSameComponents(encoded, copy.getPatternInventory().getStackInSlot(161)));
        assertEquals(LARGE, copy.getInternalInventory()[161].getItemInventory().getLong(key)); assertEquals(LARGE, copy.getBuffer().getLong(key));
    }
    @Test void configurableNativePaginationKeepsAll1800RealSlotsAndBothScrollAxesWhileNarrowPagesStayValid() {
        var inventory = new CustomItemStackHandler(1800); var manager = new PanelSyncManager(new ModularSyncManager(false), true); var changes = new AtomicInteger(-1);
        var pagination = new PaginationUIManager(18, 10, 10, 340, 208, changes::set, ignored -> true, inventory);
        var root = pagination.createPaginationUI(manager, ignored -> {}); var pages = (PagedWidget<?>) root.getChildren().getFirst();
        assertEquals(10, pages.getPages().size()); var viewport = (brachy.modularui.widget.ScrollWidget<?>) pages.getPages().getFirst();
        assertNotNull(viewport.getScrollArea().getScrollX()); assertNotNull(viewport.getScrollArea().getScrollY()); assertEquals(180, viewport.getChildren().getFirst().getChildren().size());
        manager.findSyncHandler("molecularPageIndex", IntSyncValue.class).setIntValue(Integer.MAX_VALUE, true, false); assertEquals(9, manager.findSyncHandler("molecularPageIndex", IntSyncValue.class).getIntValue()); assertTrue(manager.findSyncHandler("molecularPatternSlot", 1799, ItemSlotSyncHandler.class).getSlot().isActive());
        var slot = manager.findSyncHandler("molecularPatternSlot", 1799, ItemSlotSyncHandler.class).getSlot(); slot.onSlotChangedReal(ItemStack.EMPTY, ItemStack.EMPTY, false, false); assertEquals(1799, changes.get());
        var narrow = new PaginationUIManager(1, 1, 1, 106, 46, changes::set, null, new CustomItemStackHandler(1)); var narrowManager = new PanelSyncManager(new ModularSyncManager(false), true);
        assertNotNull(narrow.createPaginationUI(narrowManager, null)); assertTrue(narrowManager.findSyncHandler("molecularPatternSlot", 0, ItemSlotSyncHandler.class).getSlot().isActive());
        var widget = new AEPatternViewExtendSlotWidget(new CustomItemStackHandler(1), narrowManager, 0, 0, 0).setOnPatternSlotChanged(() -> changes.set(2000));
        narrowManager.findSyncHandler("extendedPatternSlot", 0, ItemSlotSyncHandler.class).getSlot().onSlotChangedReal(ItemStack.EMPTY, ItemStack.EMPTY, false, false); assertEquals(2000, changes.get()); assertNotNull(widget);
    }
}
