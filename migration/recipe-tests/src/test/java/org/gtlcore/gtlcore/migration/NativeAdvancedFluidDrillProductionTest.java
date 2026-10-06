package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import org.gtlcore.gtlcore.common.machine.trait.AdvancedFluidDrillProduction;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class NativeAdvancedFluidDrillProductionTest {
    @Test void nativeToolTagArraysRetainAllExistingKeysAndKeepCoreLevelTen() {
        var needs = com.gregtechceu.gtceu.data.recipe.CustomTags.TOOL_TIERS;
        var incorrect = com.gregtechceu.gtceu.data.recipe.CustomTags.INCORRECT_TOOL_TIERS;
        assertEquals(11, needs.length); assertEquals(11, incorrect.length);
        assertEquals(net.minecraft.tags.BlockTags.NEEDS_STONE_TOOL, needs[1]);
        assertEquals(com.gregtechceu.gtceu.data.recipe.CustomTags.NEEDS_NEUTRONIUM_TOOL, needs[6]);
        assertEquals(net.minecraft.tags.BlockTags.INCORRECT_FOR_NETHERITE_TOOL, incorrect[4]);
        assertEquals(ResourceLocation.parse("gtlcore:needs_tool_level_10"), needs[10].location());
        var tier = new com.gregtechceu.gtceu.api.item.tool.MaterialToolTier(NativeAdvancedFluidRigFixtures.VIBRANIUM);
        assertEquals(10, tier.property.getHarvestLevel()); assertEquals(incorrect[10], tier.getIncorrectBlocksForDrops());
    }
    @Test void allSevenOriginalCoilMaterialsKeepTheirOriginalThermalLimits() {
        var expected = Map.of("titansteel", 8000, "adamantine", 9000, "naquadriatictaranium", 10000,
                "starmetal", 11000, "infinity", 12000, "hypogen", 14000, "eternity", 16000);
        expected.forEach((name, heat) -> assertEquals(heat, AdvancedFluidDrillProduction.maxHeat(ResourceLocation.parse("gtceu:" + name))));
        assertEquals(-1, AdvancedFluidDrillProduction.maxHeat(null));
        assertEquals(-1, AdvancedFluidDrillProduction.maxHeat(ResourceLocation.parse("other:titansteel")));
        assertEquals(-1, AdvancedFluidDrillProduction.maxHeat(ResourceLocation.parse("gtceu:cupronickel")));
    }
    @Test void originalDrillThermalAndVoltageFactorsKeepThresholdsAndOverheatBoost() {
        assertEquals(25000, AdvancedFluidDrillProduction.rate(50000, 8000, 8000, GTValues.MAX));
        assertEquals(150000, AdvancedFluidDrillProduction.rate(100000, 16000, 16000, GTValues.MAX));
        assertEquals(0, AdvancedFluidDrillProduction.rate(50000, 1999, 8000, GTValues.MAX));
        assertEquals(0, AdvancedFluidDrillProduction.rate(50000, 2000, 8000, GTValues.MAX));
        assertEquals(0, AdvancedFluidDrillProduction.rate(50000, 8000, 8000, GTValues.UHV));
        assertEquals(0, AdvancedFluidDrillProduction.rate(50000, 8000, -1, GTValues.MAX));
        assertEquals(0, AdvancedFluidDrillProduction.rate(0, 8000, 8000, GTValues.MAX));
        assertTrue(AdvancedFluidDrillProduction.rate(50000, 9000, 8000, GTValues.MAX) > 25000);
        assertEquals(25000, AdvancedFluidDrillProduction.rate(50000, 8000, 8000, GTValues.MAX + 5));
        assertTrue(AdvancedFluidDrillProduction.rate(50000, 4399, 8000, GTValues.MAX) <= AdvancedFluidDrillProduction.rate(50000, 4400, 8000, GTValues.MAX));
    }
    @Test void recipeEnergyAndFluidAmountsStayExactAboveDoubleIntegerPrecision() {
        long amount = 9_007_199_254_740_993L;
        var recipe = AdvancedFluidDrillProduction.recipe(Map.of(Fluids.WATER, amount), 1);
        assertNotNull(recipe);
        var fluid = FluidRecipeCapability.CAP.of(recipe.outputs.get(FluidRecipeCapability.CAP).getFirst().content());
        assertEquals(amount, LongFluidIngredient.amount(fluid));
        assertEquals(Math.addExact(amount, GTValues.V[GTValues.UHV]), recipe.getInputEUt().getTotalEU());
        assertEquals(20, recipe.duration);
    }
    @Test void nativeRecipeKeepsBothFluidsAndScalesTheBaseEnergyTogetherWithOutputs() {
        var recipe = AdvancedFluidDrillProduction.recipe(Map.of(Fluids.WATER, 1010L, Fluids.LAVA, 3030L), 25000);
        assertNotNull(recipe);
        assertEquals((GTValues.V[GTValues.UHV] + 4040) * 25000L, recipe.getInputEUt().getTotalEU());
        assertEquals(2, recipe.outputs.get(FluidRecipeCapability.CAP).size());
        var quantities = recipe.outputs.get(FluidRecipeCapability.CAP).stream()
                .map(c -> LongFluidIngredient.amount(FluidRecipeCapability.CAP.of(c.content()))).sorted().toList();
        assertEquals(java.util.List.of(25_250_000L, 75_750_000L), quantities);
    }
    @Test void absentZeroNegativeAndOverflowingProductionCannotAdvertiseAFreeCycle() {
        assertNull(AdvancedFluidDrillProduction.recipe(Map.of(), 1));
        assertNull(AdvancedFluidDrillProduction.recipe(Map.of(Fluids.WATER, 1L), 0));
        assertNull(AdvancedFluidDrillProduction.recipe(Map.of(Fluids.WATER, -1L), 1));
        assertNull(AdvancedFluidDrillProduction.recipe(Map.of(Fluids.WATER, 0L), 1));
        assertNull(AdvancedFluidDrillProduction.recipe(Map.of(Fluids.WATER, Long.MAX_VALUE), 2));
        assertNull(AdvancedFluidDrillProduction.recipe(Map.of(Fluids.WATER, Long.MAX_VALUE, Fluids.LAVA, 1L), 1));
        assertNull(AdvancedFluidDrillProduction.recipe(Map.of(Fluids.WATER, Long.MAX_VALUE / 10), 1));
    }
}
