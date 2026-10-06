package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import org.gtlcore.gtlcore.common.data.GTLCallbackMaterials;
import org.gtlcore.gtlcore.common.data.GTLRecipeTypes;
import org.gtlcore.gtlcore.common.recipe.GTLRecipeCallbacks;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Production constructor mixin and actual native material fluids, with captured generated recipes. */
class RecipeCallbackIntegrationTest {
    private static class Output implements RecipeOutput {
        final Map<ResourceLocation, GTRecipe> recipes = new LinkedHashMap<>();
        @Override public Advancement.Builder advancement() { return Advancement.Builder.advancement(); }
        @Override public void accept(ResourceLocation id, Recipe<?> recipe, AdvancementHolder advancement,
                                      ICondition... conditions) {
            assertNull(recipes.put(id, (GTRecipe) recipe), "Duplicate generated recipe " + id);
        }
        GTRecipe get(String path) {
            return Objects.requireNonNull(recipes.get(ResourceLocation.fromNamespaceAndPath("gtlcore_recipe_test", path)), path);
        }
    }

    private static GTRecipeBuilder cutter(String path, long eu) {
        return GTRecipeTypes.CUTTER_RECIPES.recipeBuilder(ResourceLocation.fromNamespaceAndPath("gtlcore_recipe_test", path))
                .inputItems(Items.IRON_INGOT).outputItems(Items.IRON_NUGGET).duration(1000).EUt(eu);
    }

    private static SizedFluidIngredient inputFluid(GTRecipe recipe) {
        return FluidRecipeCapability.CAP.of(recipe.getInputContents(FluidRecipeCapability.CAP).getFirst().content());
    }

    private static void assertFluid(GTRecipe recipe, Fluid fluid, long amount) {
        var ingredient = inputFluid(recipe);
        assertTrue(LongFluidIngredient.matchingIngredient(ingredient).test(new net.neoforged.neoforge.fluids.FluidStack(fluid, 1)));
        assertEquals(amount, LongFluidIngredient.amount(ingredient));
    }

    @Test
    void theSevenProductionCallbackMaterialsPreserveTheirFluidRegistryIds() {
        var fluids = Map.of("grade_8_purified_water", GTLCallbackMaterials.GradePurifiedWater8,
                "grade_16_purified_water", GTLCallbackMaterials.GradePurifiedWater16,
                "mutated_living_solder", GTLCallbackMaterials.MutatedLivingSolder,
                "super_mutated_living_solder", GTLCallbackMaterials.SuperMutatedLivingSolder,
                "supercritical_steam", GTLCallbackMaterials.SupercriticalSteam,
                "photoresist", GTLCallbackMaterials.Photoresist, "euv_photoresist", GTLCallbackMaterials.EuvPhotoresist);
        fluids.forEach((name, material) -> assertEquals(ResourceLocation.fromNamespaceAndPath("gtceu", name),
                BuiltInRegistries.FLUID.getKey(material.getFluid())));
    }

    @Test
    void nativeCutterGeneratesTheFiveLowVoltageVariantsWithOriginalIdsAndQuantities() {
        var output = new Output(); cutter("cut_low", 32).save(output);
        assertEquals(5, output.recipes.size());
        assertFluid(output.get("cutter/cut_low_water"), GTMaterials.Water.getFluid(), 100);
        assertFluid(output.get("cutter/cut_low_distilled_water"), GTMaterials.DistilledWater.getFluid(), 75);
        assertFluid(output.get("cutter/cut_low_8_water"), GTLCallbackMaterials.GradePurifiedWater8.getFluid(), 44);
        assertFluid(output.get("cutter/cut_low_16_water"), GTLCallbackMaterials.GradePurifiedWater16.getFluid(), 33);
        assertFluid(output.get("cutter/cut_low"), GTMaterials.Lubricant.getFluid(), 25);
        assertEquals(2000, output.get("cutter/cut_low_water").duration);
        assertEquals(1500, output.get("cutter/cut_low_distilled_water").duration);
        assertEquals(800, output.get("cutter/cut_low_8_water").duration);
        assertEquals(500, output.get("cutter/cut_low_16_water").duration);
        assertEquals(1000, output.get("cutter/cut_low").duration);
    }

    @Test
    void cutterVoltageBoundariesKeepThePurifiedWaterRules() {
        var output = new Output(); cutter("cut_below", 524287).save(output);
        assertEquals(5, output.recipes.size());
        output = new Output(); cutter("cut_middle", 524288).save(output);
        assertEquals(2, output.recipes.size());
        assertFluid(output.get("cutter/cut_middle"), GTLCallbackMaterials.GradePurifiedWater8.getFluid(), 1000);
        assertFluid(output.get("cutter/cut_middle_16_water"), GTLCallbackMaterials.GradePurifiedWater16.getFluid(), 500);
        output = new Output(); cutter("cut_uev", GTValues.VA[GTValues.UEV]).save(output);
        assertEquals(1, output.recipes.size());
        assertFluid(output.get("cutter/cut_uev"), GTLCallbackMaterials.GradePurifiedWater16.getFluid(), 1000);
    }

    @Test
    void explicitAndTickFluidInputsDoNotGenerateExtraCoolantRecipes() {
        var output = new Output(); cutter("cut_explicit", 32).inputFluids(GTMaterials.Water.getFluid(7)).save(output);
        assertEquals(1, output.recipes.size());
        assertEquals(7, LongFluidIngredient.amount(inputFluid(output.get("cutter/cut_explicit"))));
        assertEquals(GTMaterials.Water.getFluidTag(),
                ((net.neoforged.neoforge.fluids.crafting.TagFluidIngredient) inputFluid(output.get("cutter/cut_explicit")).ingredient()).tag());
        output = new Output(); cutter("cut_tick", 32).perTick(true).inputFluids(GTMaterials.Water.getFluid(7)).save(output);
        assertEquals(1, output.recipes.size());
        assertTrue(output.get("cutter/cut_tick").getInputContents(FluidRecipeCapability.CAP).isEmpty());
    }

    @Test
    void lowVoltageCircuitAssemblyHonorsTheNativeSolderMultiplierSetter() {
        var builder = GTRecipeTypes.CIRCUIT_ASSEMBLER_RECIPES.recipeBuilder(
                ResourceLocation.fromNamespaceAndPath("gtlcore_recipe_test", "circuit_low"))
                .inputItems(Items.IRON_INGOT).outputItems(Items.IRON_NUGGET).duration(100).EUt(159999).solderMultiplier(2);
        assertEquals(2, builder.getSolderMultiplier());
        var output = new Output(); builder.save(output);
        assertEquals(2, output.recipes.size());
        assertFluid(output.get("circuit_assembler/circuit_low"), GTMaterials.Tin.getFluid(), 288);
        assertFluid(output.get("circuit_assembler/circuit_low_soldering_alloy"), GTMaterials.SolderingAlloy.getFluid(), 144);
    }

    @Test
    void circuitAssemblyAtTheHighVoltageBoundaryUsesTheTwoLivingSolders() {
        var output = new Output();
        GTRecipeTypes.CIRCUIT_ASSEMBLER_RECIPES.recipeBuilder(
                ResourceLocation.fromNamespaceAndPath("gtlcore_recipe_test", "circuit_high"))
                .inputItems(Items.IRON_INGOT).outputItems(Items.IRON_NUGGET).duration(100).EUt(160000).save(output);
        assertEquals(2, output.recipes.size());
        assertFluid(output.get("circuit_assembler/circuit_high"), GTLCallbackMaterials.MutatedLivingSolder.getFluid(), 144);
        assertFluid(output.get("circuit_assembler/circuit_high_soldering_alloy"), GTLCallbackMaterials.SuperMutatedLivingSolder.getFluid(), 72);
    }

    @Test
    void plasmaCallbackPreservesSupercriticalSteamBeyondTheIntLimit() {
        var output = new Output();
        GTRecipeTypes.PLASMA_GENERATOR_FUELS.recipeBuilder(
                ResourceLocation.fromNamespaceAndPath("gtlcore_recipe_test", "plasma_long"))
                .inputFluids(GTMaterials.Helium.getFluid(com.gregtechceu.gtceu.api.fluids.store.FluidStorageKeys.PLASMA, 100)).outputFluids(GTMaterials.Helium.getFluid(100))
                .duration(2_000_000).EUt(GTValues.V[GTValues.EV]).save(output);
        assertEquals(2, output.recipes.size());
        var heat = output.get("heat_exchanger/plasma_long");
        assertSame(GTLRecipeTypes.HEAT_EXCHANGER_RECIPES.value(), heat.recipeType);
        assertEquals(200, heat.duration);
        assertEquals(4_096_000_000L, heat.data.getLong("eu"));
        var steam = FluidRecipeCapability.CAP.of(heat.getOutputContents(FluidRecipeCapability.CAP).get(1).content());
        assertEquals(4_096_000_000L, LongFluidIngredient.amount(steam));
        assertTrue(LongFluidIngredient.matchingIngredient(steam).test(GTLCallbackMaterials.SupercriticalSteam.getFluid(1)));
    }

    @Test
    void laserEngravingSelectsPhotoresistAndPreservesScaledEnergyAndDuration() {
        var output = new Output();
        GTRecipeTypes.LASER_ENGRAVER_RECIPES.recipeBuilder(
                ResourceLocation.fromNamespaceAndPath("gtlcore_recipe_test", "engrave_low"))
                .inputItems(Items.IRON_INGOT).outputItems(Items.IRON_NUGGET).duration(100).EUt(8192).save(output);
        assertEquals(3, output.recipes.size());
        var array = output.get("dimensional_focus_engraving_array/engrave_low");
        assertFluid(array, GTLCallbackMaterials.Photoresist.getFluid(), 6);
        assertEquals(20, array.duration);
        assertEquals(32768, array.getInputEUt().getTotalEU());
        output = new Output();
        GTRecipeTypes.LASER_ENGRAVER_RECIPES.recipeBuilder(
                ResourceLocation.fromNamespaceAndPath("gtlcore_recipe_test", "engrave_high"))
                .inputItems(Items.IRON_INGOT).outputItems(Items.IRON_NUGGET).duration(100).EUt(4_194_304).save(output);
        assertFluid(output.get("dimensional_focus_engraving_array/engrave_high"), GTLCallbackMaterials.EuvPhotoresist.getFluid(), 5);
    }

    @Test
    void nativeTypeCapacitiesAndExtendedFusionTiersAreInstalledDuringRegistration() {
        assertEquals(1, GTRecipeTypes.SIFTER_RECIPES.value().getMaxInputs(FluidRecipeCapability.CAP));
        assertEquals(1, GTRecipeTypes.SIFTER_RECIPES.value().getMaxOutputs(FluidRecipeCapability.CAP));
        assertEquals(2, GTRecipeTypes.LASER_ENGRAVER_RECIPES.value().getMaxInputs(FluidRecipeCapability.CAP));
        assertEquals(1, GTRecipeTypes.LASER_ENGRAVER_RECIPES.value().getMaxOutputs(FluidRecipeCapability.CAP));
        assertEquals(1, GTRecipeTypes.FUSION_RECIPES.value().getUiLayout().getRecipeUIModifiers().size());
        assertEquals("I", GTLRecipeCallbacks.fusionTier(160));
        assertEquals("II", GTLRecipeCallbacks.fusionTier(161));
        assertEquals("III", GTLRecipeCallbacks.fusionTier(321));
        assertEquals("IV", GTLRecipeCallbacks.fusionTier(641));
        assertEquals("V", GTLRecipeCallbacks.fusionTier(1281));
    }
}
