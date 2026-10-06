package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.integration.ae2.AEUtils;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntCircuitIngredient;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Real native sized ingredients, AE processing components and AE CPU inventory. */
class NativeAeRecipeInputsIntegrationTest {
    private static final long LARGE = 9_007_199_254_740_993L;
    private static ItemStack namedItem(String name) {
        var stack = new ItemStack(Items.IRON_INGOT); stack.set(DataComponents.CUSTOM_NAME, Component.literal(name)); return stack;
    }
    private static FluidStack namedFluid(String name) {
        var stack = new FluidStack(Fluids.WATER, 1); stack.set(DataComponents.CUSTOM_NAME, Component.literal(name)); return stack;
    }
    private static appeng.api.crafting.IPatternDetails pattern(List<GenericStack> inputs, List<GenericStack> outputs) {
        var stack = PatternDetailsHelper.encodeProcessingPattern(inputs, outputs);
        return ((appeng.crafting.pattern.EncodedPatternItem<?>) stack.getItem()).decode(AEItemKey.of(stack), null);
    }
    private static ListCraftingInventory inventory() { return new ListCraftingInventory(ignored -> {}); }

    @Test void nativeIngredientMapsCombinePredicatesWithExactLongQuantitiesAndCircuitConfiguration() {
        var item = namedItem("component predicate");
        var first = LongIngredient.create(item, LARGE); var second = LongIngredient.create(item, 7);
        var config = new AtomicInteger(-1);
        var circuit = new SizedIngredient(IntCircuitIngredient.circuit(19), 1);
        var result = AEUtils.ingredientsMapWithOutCircuit(List.of(first, second, circuit), config::set);
        assertEquals(1, result.size()); assertEquals(LARGE + 7, result.getLong(first)); assertEquals(19, config.get());
        assertEquals(2, AEUtils.ingredientsMap(List.of(first, circuit)).size());
        var fluid = namedFluid("fluid predicate"); var fluidInput = LongFluidIngredient.create(fluid, LARGE);
        var fluids = AEUtils.fluidIngredientsMap(List.of(fluidInput, LongFluidIngredient.create(fluid, 9)));
        assertEquals(1, fluids.size()); assertEquals(LARGE + 9, fluids.getLong(fluidInput));
        assertTrue(AEUtils.testFluidIngredient(fluidInput, AEFluidKey.of(fluid)));
        assertFalse(AEUtils.testFluidIngredient(fluidInput, AEFluidKey.of(namedFluid("wrong components"))));
        assertFalse(AEUtils.testFluidIngredient(fluidInput, AEFluidKey.of(Fluids.LAVA)));
        assertThrows(ArithmeticException.class, () -> AEUtils.ingredientsMap(List.of(LongIngredient.create(item, Long.MAX_VALUE), first)));
        assertThrows(ArithmeticException.class, () -> AEUtils.fluidIngredientsMap(List.of(LongFluidIngredient.create(fluid, Long.MAX_VALUE), fluidInput)));
    }

    @Test void processingExtractionPreservesComponentKeysExactLargeAmountsAndOneCircuitPerBatch() {
        var item = AEItemKey.of(namedItem("queued input")); var fluid = AEFluidKey.of(namedFluid("queued fluid"));
        var circuit = AEItemKey.of(IntCircuitBehaviour.stack(24)); var diamond = AEItemKey.of(Items.DIAMOND);
        var pattern = pattern(List.of(new GenericStack(item, LARGE), new GenericStack(fluid, 1000), new GenericStack(circuit, 1)),
                List.of(new GenericStack(diamond, 3)));
        var source = inventory(); source.insert(item, LARGE * 2, Actionable.MODULATE);
        source.insert(fluid, 2000, Actionable.MODULATE); source.insert(circuit, 1, Actionable.MODULATE);
        var outputs = new KeyCounter(); var extracted = AEUtils.extractForProcessingPattern(pattern, source, outputs, 2);
        assertNotNull(extracted); assertEquals(3, extracted.length);
        assertEquals(LARGE * 2, extracted[0].get(item)); assertEquals(2000, extracted[1].get(fluid)); assertEquals(1, extracted[2].get(circuit));
        assertEquals(6, outputs.get(diamond)); assertTrue(source.list.isEmpty());
        assertTrue(AEUtils.isIntegratedCircuit(circuit)); assertFalse(AEUtils.isIntegratedCircuit(item));
    }

    @Test void missingLaterInputReinjectsEarlierExtractionAndKeepsExpectedOutputsUnchanged() {
        var iron = AEItemKey.of(Items.IRON_INGOT); var gold = AEItemKey.of(Items.GOLD_INGOT); var diamond = AEItemKey.of(Items.DIAMOND);
        var pattern = pattern(List.of(new GenericStack(iron, 3), new GenericStack(gold, 4)), List.of(new GenericStack(diamond, 2)));
        var source = inventory(); source.insert(iron, 12, Actionable.MODULATE); source.insert(gold, 7, Actionable.MODULATE);
        var outputs = new KeyCounter(); outputs.add(diamond, 5);
        assertNull(AEUtils.extractForProcessingPattern(pattern, source, outputs, 2));
        assertEquals(12, source.list.get(iron)); assertEquals(7, source.list.get(gold)); assertEquals(5, outputs.get(diamond));
    }

    @Test void inputOutputAndExistingExpectedQuantityOverflowRejectBeforeInventoryMutation() {
        var iron = AEItemKey.of(Items.IRON_INGOT); var diamond = AEItemKey.of(Items.DIAMOND);
        var changes = new AtomicInteger(); var source = new ListCraftingInventory(ignored -> changes.incrementAndGet());
        source.insert(iron, Long.MAX_VALUE, Actionable.MODULATE); changes.set(0);
        var outputs = new KeyCounter();
        assertNull(AEUtils.extractForProcessingPattern(pattern(List.of(new GenericStack(iron, Long.MAX_VALUE)), List.of(new GenericStack(diamond, 1))), source, outputs, 2));
        assertNull(AEUtils.extractForProcessingPattern(pattern(List.of(new GenericStack(iron, 1)), List.of(new GenericStack(diamond, Long.MAX_VALUE))), source, outputs, 2));
        outputs.add(diamond, Long.MAX_VALUE - 1);
        assertNull(AEUtils.extractForProcessingPattern(pattern(List.of(new GenericStack(iron, 1)), List.of(new GenericStack(diamond, 2))), source, outputs, 1));
        assertEquals(0, changes.get()); assertEquals(Long.MAX_VALUE, source.list.get(iron)); assertEquals(Long.MAX_VALUE - 1, outputs.get(diamond));
        for (long invalid : new long[] {0, -1, Long.MIN_VALUE}) assertNull(AEUtils.extractForProcessingPattern(
                pattern(List.of(new GenericStack(iron, 1)), List.of(new GenericStack(diamond, 1))), source, outputs, invalid));
    }
}
