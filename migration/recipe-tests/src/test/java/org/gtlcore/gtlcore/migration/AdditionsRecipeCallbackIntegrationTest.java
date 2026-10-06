package org.gtlcore.gtlcore.migration;

import com.gtladd.gtladditions.common.recipe.GTLAddRecipesTypes;
import com.gtladd.gtladditions.utils.TempChemicalHelper;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.common.data.GTLCallbackMaterials;
import org.gtlcore.gtlcore.common.data.GTLRecipeTypes;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.fluids.store.FluidStorageKeys;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.ingredient.EnergyStack;
import com.gregtechceu.gtceu.common.data.GCYMRecipeTypes;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gregtechceu.gtceu.utils.TagUtil;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Both production type mixins run during real FML registration, before native recipe generation. */
class AdditionsRecipeCallbackIntegrationTest {
    private static class Output implements RecipeOutput {
        final Map<ResourceLocation, GTRecipe> recipes = new LinkedHashMap<>();
        @Override public Advancement.Builder advancement() { return Advancement.Builder.advancement(); }
        @Override public void accept(ResourceLocation id, Recipe<?> recipe, AdvancementHolder advancement, ICondition... conditions) {
            assertNull(recipes.put(id, (GTRecipe) recipe), "Duplicate generated recipe " + id);
        }
        GTRecipe get(String type, String path) {
            var id = ResourceLocation.fromNamespaceAndPath("gtlcore_recipe_test", type + "/" + path);
            assertTrue(recipes.containsKey(id), () -> "Missing " + id + " among " + recipes.keySet());
            return recipes.get(id);
        }
    }
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("gtlcore_recipe_test", path);
    }
    private static SizedFluidIngredient helium(boolean liquid, long amount) {
        var fluid = GTMaterials.Helium.getFluid(liquid ? FluidStorageKeys.LIQUID : FluidStorageKeys.GAS);
        return LongFluidIngredient.create(TagUtil.createFluidTag(BuiltInRegistries.FLUID.getKey(fluid).getPath()), amount);
    }
    private static GTRecipeBuilder vacuum(String path) {
        return GTRecipeTypes.VACUUM_RECIPES.recipeBuilder(id(path)).inputItems(Items.IRON_INGOT)
                .outputItems(Items.IRON_NUGGET).duration(100).EUt(32);
    }

    @Test
    void vacuumRemovesUnboundHeliumTagsAndKeepsTheSourceAndOtherFluids() {
        var output = new Output();
        vacuum("vacuum_helium").inputFluids(helium(true, 3_000_000_001L), GTMaterials.Water.asFluidIngredient(7))
                .outputFluids(helium(false, 3_000_000_001L), GTMaterials.Oxygen.asFluidIngredient(9)).save(output);
        assertEquals(2, output.recipes.size());
        var source = output.get("vacuum_freezer", "vacuum_helium");
        var derived = output.get("antientropy_condensation", "vacuum_helium");
        assertEquals(100, source.duration); assertEquals(66, derived.duration);
        assertEquals(2, source.inputs.get(FluidRecipeCapability.CAP).size());
        assertEquals(2, source.outputs.get(FluidRecipeCapability.CAP).size());
        assertEquals(1, derived.inputs.get(FluidRecipeCapability.CAP).size());
        assertEquals(1, derived.outputs.get(FluidRecipeCapability.CAP).size());
        assertEquals(7, LongFluidIngredient.amount(FluidRecipeCapability.CAP.of(derived.inputs.get(FluidRecipeCapability.CAP).getFirst().content())));
    }

    @Test
    void plasmaCondenserKeepsTheHeliumCondenserOutputExceptionAndDuration() {
        var output = new Output();
        GTLRecipeTypes.PLASMA_CONDENSER_RECIPES.recipeBuilder(id("helium_condenser"))
                .inputFluids(helium(true, 3_000_000_002L)).outputFluids(helium(false, 3_000_000_002L)).duration(73).EUt(32).save(output);
        var derived = output.get("antientropy_condensation", "helium_condenser");
        assertEquals(73, derived.duration);
        assertTrue(derived.inputs.get(FluidRecipeCapability.CAP).isEmpty());
        assertEquals(3_000_000_002L, LongFluidIngredient.amount(FluidRecipeCapability.CAP.of(derived.outputs.get(FluidRecipeCapability.CAP).getFirst().content())));
        output = new Output();
        GTLRecipeTypes.PLASMA_CONDENSER_RECIPES.recipeBuilder(id("other_condenser"))
                .inputFluids(helium(true, 1)).outputFluids(helium(false, 1)).duration(73).EUt(32).save(output);
        assertTrue(output.get("antientropy_condensation", "other_condenser").outputs.get(FluidRecipeCapability.CAP).isEmpty());
    }

    @Test
    void chaoticAlchemyResolvesMaterialTagsBeforeBindingAndPreservesAnExactLongAmount() {
        long amount = 9_007_199_254_740_993L;
        var output = new Output();
        GCYMRecipeTypes.ALLOY_BLAST_RECIPES.recipeBuilder(id("chaotic_helium"))
                .inputItems(Items.IRON_INGOT).outputFluids(helium(false, amount), GTMaterials.Water.asFluidIngredient(1))
                .duration(100).EUt(32, 3).save(output);
        assertEquals(2, output.recipes.size());
        var source = output.get("alloy_blast_smelter", "chaotic_helium");
        var derived = output.get("chaotic_alchemy", "chaotic_helium");
        assertEquals(75, derived.duration); assertEquals(new EnergyStack(48), derived.getInputEUt());
        assertEquals(2, source.outputs.get(FluidRecipeCapability.CAP).size());
        assertEquals(1, derived.outputs.get(FluidRecipeCapability.CAP).size());
        var fluid = FluidRecipeCapability.CAP.of(derived.outputs.get(FluidRecipeCapability.CAP).getFirst().content());
        assertEquals(amount, LongFluidIngredient.amount(fluid));
        assertTrue(LongFluidIngredient.matchingIngredient(fluid).test(GTMaterials.Helium.getFluid(FluidStorageKeys.LIQUID, 1)));
        assertSame(GTMaterials.Helium, TempChemicalHelper.INSTANCE.getMaterialFromIngredient(helium(false, amount)));
    }

    @Test
    void combinedLaserHooksProduceEachIdOnceAndKeepCorePhotoresistAndNativeAmperage() {
        var output = new Output();
        GTRecipeTypes.LASER_ENGRAVER_RECIPES.recipeBuilder(id("combined_engraving"))
                .inputItems(Items.IRON_INGOT).outputItems(Items.IRON_NUGGET).duration(100).EUt(8192, 3).save(output);
        assertEquals(3, output.recipes.size());
        var source = output.get("laser_engraver", "combined_engraving");
        var photon = output.get("photon_matrix_etch", "combined_engraving");
        var focus = output.get("dimensional_focus_engraving_array", "combined_engraving");
        assertEquals(100, source.duration); assertEquals(20, photon.duration); assertEquals(20, focus.duration);
        assertEquals(new EnergyStack(8192, 3), photon.getInputEUt());
        assertEquals(98_304, focus.getInputEUt().getTotalEU());
        assertTrue(LongFluidIngredient.matchingIngredient(FluidRecipeCapability.CAP.of(focus.inputs.get(FluidRecipeCapability.CAP).getFirst().content()))
                .test(GTLCallbackMaterials.Photoresist.getFluid(1)));
    }

    @Test
    void lasersWithFluidOutputsDoNotGenerateEitherDerivedVariantAndOneTickRecipesStayPositive() {
        var output = new Output();
        GTRecipeTypes.LASER_ENGRAVER_RECIPES.recipeBuilder(id("fluid_laser"))
                .outputFluids(GTMaterials.Water.asFluidIngredient(1)).duration(1).EUt(32).save(output);
        assertEquals(1, output.recipes.size());
        output = new Output();
        GTRecipeTypes.LASER_ENGRAVER_RECIPES.recipeBuilder(id("one_tick_laser"))
                .inputItems(Items.IRON_INGOT).outputItems(Items.IRON_NUGGET).duration(1).EUt(32).save(output);
        assertEquals(1, output.get("photon_matrix_etch", "one_tick_laser").duration);
    }

    @Test
    void spaceProcessorDropsRegularFluidInputsAndKeepsOutputsAndTheHvEnergyRule() {
        var output = new Output();
        GTLRecipeTypes.INTEGRATED_ORE_PROCESSOR.recipeBuilder(id("space_ore"))
                .inputItems(Items.IRON_ORE).inputFluids(GTMaterials.Water.asFluidIngredient(1000))
                .outputItems(LongIngredient.create(Ingredient.of(Items.IRON_INGOT), 3_000_000_003L)).duration(90).EUt(32).save(output);
        var derived = output.get("space_ore_processor", "space_ore");
        assertTrue(derived.inputs.get(FluidRecipeCapability.CAP).isEmpty());
        assertEquals(GTValues.VA[GTValues.HV], derived.getInputEUt().getTotalEU());
        assertEquals(3_000_000_003L, LongIngredient.amount(ItemRecipeCapability.CAP.of(derived.outputs.get(ItemRecipeCapability.CAP).getFirst().content())));
    }

    @Test
    void leylineScalesAnExactLongFirstOutputAndSaturatesEnergyWithoutChangingTheSource() {
        var output = new Output();
        GTLRecipeTypes.AGGREGATION_DEVICE_RECIPES.recipeBuilder(id("leyline_long"))
                .inputItems(Items.IRON_INGOT).outputItems(LongIngredient.create(Ingredient.of(Items.DIAMOND), 9_007_199_254_740_993L)).outputItems(LongIngredient.create(Ingredient.of(Items.EMERALD), 3)).duration(10).EUt(Long.MAX_VALUE / 2, 3).save(output);
        var source = output.get("aggregation_device", "leyline_long");
        var derived = output.get("leyline_crystallize", "leyline_long");
        assertEquals(2, source.outputs.get(ItemRecipeCapability.CAP).size());
        assertEquals(1, derived.outputs.get(ItemRecipeCapability.CAP).size());
        assertEquals(13_510_798_882_111_489L, LongIngredient.amount(ItemRecipeCapability.CAP.of(derived.outputs.get(ItemRecipeCapability.CAP).getFirst().content())));
        assertEquals(Long.MAX_VALUE, derived.getInputEUt().getTotalEU());
    }

    @Test
    void reactionChamberKeepsAllOutputsAndAdditionsCapacityOverridesReachNativeLayouts() {
        var output = new Output();
        GTLRecipeTypes.AGGREGATION_DEVICE_RECIPES.recipeBuilder(ResourceLocation.fromNamespaceAndPath("gtladditions", "reaction_chamber"))
                .inputItems(Items.IRON_INGOT).outputItems(Items.DIAMOND).outputItems(Items.EMERALD).duration(10).EUt(32).save(output);
        var derived = output.recipes.get(ResourceLocation.fromNamespaceAndPath("gtladditions", "leyline_crystallize/reaction_chamber"));
        assertNotNull(derived); assertEquals(2, derived.outputs.get(ItemRecipeCapability.CAP).size());
        assertEquals(128, derived.getInputEUt().getTotalEU());
        assertEquals(1, GTLRecipeTypes.DOOR_OF_CREATE_RECIPES.value().getMaxInputs(ItemRecipeCapability.CAP));
        assertEquals(2, GTLRecipeTypes.CREATE_AGGREGATION_RECIPES.value().getMaxInputs(ItemRecipeCapability.CAP));
        assertEquals(16, GTLRecipeTypes.DIMENSIONALLY_TRANSCENDENT_MIXER_RECIPES.value().getMaxInputs(FluidRecipeCapability.CAP));
        var grid = GTLRecipeTypes.DIMENSIONALLY_TRANSCENDENT_MIXER_RECIPES.value().getUiLayout().capabilityInfo(FluidRecipeCapability.CAP).getRecipeViewerGrid(IO.IN);
        assertEquals(16, String.join("", grid).chars().filter(c -> c == 's').count());
    }
}
