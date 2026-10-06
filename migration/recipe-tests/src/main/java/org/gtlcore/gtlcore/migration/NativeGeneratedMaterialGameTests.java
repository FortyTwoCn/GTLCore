package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.data.GTLRecipeTypes;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeGeneratedMaterialGameTests {
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void generatedCoreOreAndElectricImplosionRecipesLoadIntoActualWorld(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        var integrated = recipes.getAllRecipesFor(GTLRecipeTypes.INTEGRATED_ORE_PROCESSOR.value()).stream()
                .filter(holder -> holder.id().getPath().endsWith("/integrated_ore_processor_1_iron"))
                .findFirst().orElseThrow(() -> new AssertionError("Generated Core integrated ore recipe is absent from world"));
        var recipe = integrated.value();
        long expected = GTMaterials.Iron.getProperty(PropertyKey.ORE).getOreMultiplier() * 2L * org.gtlcore.gtlcore.config.ConfigHolder.INSTANCE.oreMultiplier;
        helper.assertTrue(LongIngredient.amount(ItemRecipeCapability.CAP.of(recipe.getOutputContents(ItemRecipeCapability.CAP).getFirst().content())) == expected,
                "World recipe lost Core ore multiplier");
        helper.assertTrue(recipes.getAllRecipesFor(GTLRecipeTypes.ELECTRIC_IMPLOSION_COMPRESSOR_RECIPES.value()).stream()
                        .anyMatch(holder -> holder.id().getPath().endsWith("/electric_implode_dust_diamond_")),
                "Generated Core electric implosion recipe is absent from world");
        helper.assertTrue(recipes.getAllRecipesFor(GTRecipeTypes.ORE_WASHER_RECIPES.value()).stream()
                        .anyMatch(holder -> holder.id().getPath().endsWith("/wash_iron_crushed_ore_to_purified_ore")),
                "Core hook suppressed native downstream ore washing");
        helper.succeed();
    }
}
