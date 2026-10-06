package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.*;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import org.gtlcore.gtlcore.integration.ae2.AEOutputBuffer;
import org.gtlcore.gtlcore.integration.ae2.AEOutputIngredients;
import org.gtlcore.gtlcore.integration.ae2.async.AEAccumulator;
import org.gtlcore.gtlcore.integration.ae2.async.AEWriteService;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntProviderIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntProviderFluidIngredient;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.util.valueproviders.ConstantInt;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.junit.jupiter.api.Test;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class NativeAeOutputIntegrationTest {
    private static final long LARGE = 9_007_199_254_740_993L;
    private static final RegistryAccess REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    private static MEExtendedOutputPartMachineBase create(MachineEntry<MachineDefinition> definition) {
        return (MEExtendedOutputPartMachineBase) definition.getBlockEntityType().create(BlockPos.ZERO, definition.getBlock().defaultBlockState());
    }
    private static ItemStack item(String name) {
        var result = new ItemStack(Items.IRON_INGOT); result.set(DataComponents.CUSTOM_NAME, Component.literal(name)); return result;
    }
    private static FluidStack fluid(String name) {
        var result = new FluidStack(Fluids.WATER, 1000); result.set(DataComponents.CUSTOM_NAME, Component.literal(name)); return result;
    }

    @Test void nativeRecipeBuilderPreservesFluidComponentsForSingleAndArrayOutputs() {
        var named = fluid("builder fluid"); var plain = new FluidStack(Fluids.LAVA, 500);
        var single = com.gregtechceu.gtceu.common.data.GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("ae_component_single").outputFluids(named).build();
        var array = com.gregtechceu.gtceu.common.data.GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("ae_component_array").outputFluids(named, plain).build();
        var cap = com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability.CAP;
        for (var recipe : List.of(single, array)) {
            var ingredient = cap.of(recipe.getOutputContents(cap).getFirst().content());
            assertEquals(AEFluidKey.of(named), AEOutputIngredients.fluid(ingredient, false).what());
            assertEquals(1000, AEOutputIngredients.fluid(ingredient, false).amount());
            assertFalse(ingredient.test(fluid("different")));
        }
        assertEquals(AEFluidKey.of(Fluids.LAVA), AEOutputIngredients.fluid(cap.of(array.getOutputContents(cap).get(1).content()), false).what());
        assertEquals(500, AEOutputIngredients.fluid(cap.of(array.getOutputContents(cap).get(1).content()), false).amount());
    }

    @Test void componentAwareFiltersChangeImmediatelyAndRestoreAllSlotsWithNativeRegistryData() {
        var dirty = new AtomicInteger();
        var filter = new MEOutputFilterHandler(3, 9, dirty::incrementAndGet, () -> {}, () -> 10, value -> {});
        var right = item("required"); var water = fluid("required fluid");
        filter.setItemFilter(26, AEItemKey.of(right)); filter.setFluidFilter(25, AEFluidKey.of(water));
        assertTrue(filter.test(item("different"))); assertTrue(filter.test(fluid("different")));
        filter.updateItemFilterSettings(false, false); filter.updateFluidFilterSettings(false, false);
        assertTrue(filter.test(right)); assertFalse(filter.test(item("different")));
        assertTrue(filter.test(water)); assertFalse(filter.test(fluid("different")));
        filter.updateItemFilterSettings(true, false); assertFalse(filter.test(right)); assertTrue(filter.test(item("different")));
        var saved = filter.serializeNBT(REGISTRIES); var copy = new MEOutputFilterHandler(3, 9, () -> {}, () -> {}, () -> 10, value -> {});
        copy.deserializeNBT(REGISTRIES, saved);
        assertEquals(saved, copy.serializeNBT(REGISTRIES)); assertEquals(AEItemKey.of(right), copy.getItemFilter(26));
        assertEquals(AEFluidKey.of(water), copy.getFluidFilter(25)); assertTrue(copy.isItemBlackList()); assertFalse(copy.isIgnoreFluidNbt());
        copy.setItemFilter(26, null); copy.setFluidFilter(25, null);
        assertFalse(copy.isHasItemFilter()); assertFalse(copy.isHasFluidFilter()); assertTrue(copy.test(right)); assertTrue(copy.test(water));
        assertTrue(dirty.get() >= 5);
    }

    @Test void filteredNativeHandlersReturnOnlyRejectedContentsAndPersistExactLongComponents() {
        var host = (MEExtendedOutputPartMachine) create(NativeAeGridFixtures.FILTERED_OUTPUT);
        var right = item("accepted"); var wrong = item("rejected"); var water = fluid("accepted water");
        host.getFilterHandler().setItemFilter(0, AEItemKey.of(right)); host.getFilterHandler().updateItemFilterSettings(false, false);
        host.getFilterHandler().setFluidFilter(0, AEFluidKey.of(water)); host.getFilterHandler().updateFluidFilterSettings(false, false);
        var wanted = LongIngredient.create(right, LARGE); var rejected = LongIngredient.create(wrong, 6);
        assertSame(host, host.getItemOutputHandler().getMachine()); assertSame(host, host.getFluidOutputHandler().getMachine());
        assertEquals(List.of(rejected), host.getItemOutputHandler().meHandleRecipeOutput(List.of(wanted, rejected), true)); assertTrue(host.getBuffer().isEmpty());
        assertEquals(List.of(rejected), host.getItemOutputHandler().meHandleRecipeOutput(List.of(wanted, rejected), false));
        assertTrue(host.getFluidOutputHandler().meHandleRecipeOutput(List.of(LongFluidIngredient.create(water, LARGE)), false).isEmpty());
        host.setPriority(234);
        var copy = (MEExtendedOutputPartMachine) create(NativeAeGridFixtures.FILTERED_OUTPUT);
        copy.loadAdditional(host.saveWithoutMetadata(REGISTRIES), REGISTRIES);
        assertEquals(LARGE, copy.getBuffer().getLong(AEItemKey.of(right))); assertEquals(LARGE, copy.getBuffer().getLong(AEFluidKey.of(water)));
        assertEquals(234, copy.getPriority()); assertFalse(copy.getFilterHandler().test(wrong));
        copy.setPriority(Integer.MAX_VALUE); assertEquals(100_000, copy.getPriority());
    }

    @Test void asyncNativeSaveFlushesAcceptedItemsAndFluidsWithoutWaitingForTheAeGrid() {
        var host = create(NativeAeGridFixtures.ASYNC_OUTPUT); var right = item("async component"); var water = fluid("async component");
        var items = List.of(LongIngredient.create(right, LARGE)); var fluids = List.of(LongFluidIngredient.create(water, LARGE));
        assertTrue(host.getItemOutputHandler().meHandleRecipeOutput(items, true).isEmpty()); assertTrue(host.getBuffer().isEmpty());
        host.getItemOutputHandler().meHandleRecipeOutput(items, false); host.getFluidOutputHandler().meHandleRecipeOutput(fluids, false);
        host.setPriority(79);
        var saved = host.saveWithoutMetadata(REGISTRIES); var copy = create(NativeAeGridFixtures.ASYNC_OUTPUT); copy.loadAdditional(saved, REGISTRIES);
        assertEquals(LARGE, copy.getBuffer().getLong(AEItemKey.of(right))); assertEquals(LARGE, copy.getBuffer().getLong(AEFluidKey.of(water))); assertEquals(79, copy.getPriority());
        host.saveWithoutMetadata(REGISTRIES); assertEquals(copy.getBuffer(), host.getBuffer());
        assertSame(copy, copy.getItemOutputHandler().getMachine());
    }

    @Test void bufferedAndUnpublishedOutputAreIncludedInOverflowPreflightWithoutMutation() {
        for (var definition : List.of(NativeAeGridFixtures.FILTERED_OUTPUT, NativeAeGridFixtures.ASYNC_OUTPUT)) {
            var host = create(definition); var key = AEItemKey.of(Items.IRON_INGOT);
            host.getItemOutputHandler().meHandleRecipeOutput(List.of(LongIngredient.create(new ItemStack(Items.IRON_INGOT), Long.MAX_VALUE - 2)), false);
            var extra = List.of(LongIngredient.create(new ItemStack(Items.IRON_INGOT), 3));
            assertEquals(extra, host.getItemOutputHandler().meHandleRecipeOutput(extra, true));
            assertEquals(extra, host.getItemOutputHandler().meHandleRecipeOutput(extra, false));
            host.saveWithoutMetadata(REGISTRIES); assertEquals(Long.MAX_VALUE - 2, host.getBuffer().getLong(key));
        }
    }

    @Test void nativeRangedOutputsUseTheirExistingRollWithoutResettingSharedRecipeIngredients() {
        var ranged = IntProviderIngredient.of(LongIngredient.matchingIngredient(LongIngredient.create(item("ranged"), 1)), ConstantInt.of(8));
        ranged.setSampledCount(5); var sized = new SizedIngredient(ranged.toVanilla(), 1);
        assertEquals(8, AEOutputIngredients.item(sized, true).amount()); assertEquals(5, AEOutputIngredients.item(sized, false).amount()); assertEquals(5, ranged.getSampledCount());
        var fluid = IntProviderFluidIngredient.of(LongFluidIngredient.matchingIngredient(LongFluidIngredient.create(fluid("ranged"), 1)), ConstantInt.of(1000));
        fluid.setSampledCount(750); var output = new net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient(fluid, 1);
        assertEquals(1000, AEOutputIngredients.fluid(output, true).amount()); assertEquals(750, AEOutputIngredients.fluid(output, false).amount()); assertEquals(750, fluid.getSampledCount());
        assertEquals(AEFluidKey.of(fluid("ranged")), AEOutputIngredients.fluid(output, false).what());
    }

    @Test void workerDrainAndImmediateSavePublishAllOutputOnceAndInvalidSaveDoesNotPartiallyLoad() {
        var accumulator = new AEAccumulator(); var ref = new WeakReference<>(accumulator); var queued = new ConcurrentLinkedQueue<Object2LongOpenHashMap<AEKey>>();
        var requested = new AtomicBoolean(true); var key = AEItemKey.of(item("worker"));
        accumulator.add(key, LARGE); AEWriteService.INSTANCE.prepareDrainedData(ref, queued, requested);
        AEWriteService.INSTANCE.flushBlocking(ref, queued, requested, 0);
        var total = new Object2LongOpenHashMap<AEKey>();
        for (var map : queued) for (var entry : map.object2LongEntrySet()) total.addTo(entry.getKey(), entry.getLongValue());
        assertEquals(LARGE, total.getLong(key)); assertTrue(accumulator.isEmpty());
        var ledger = new AEOutputBuffer(() -> {}); ledger.storage.put(key, LARGE); var saved = ledger.serializeNBT(REGISTRIES);
        var invalid = saved.copy(); var bad = invalid.getCompound(0).copy(); bad.putLong("value", Long.MAX_VALUE); invalid.add(bad);
        assertThrows(ArithmeticException.class, () -> ledger.deserializeNBT(REGISTRIES, invalid)); assertEquals(LARGE, ledger.storage.getLong(key));
        ledger.deserializeNBT(REGISTRIES, new net.minecraft.nbt.ListTag()); assertTrue(ledger.storage.isEmpty());
    }
}
