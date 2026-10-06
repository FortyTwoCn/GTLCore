package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.data.GTLRecipeTypes;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.data.recipe.generated.OreRecipeHandler;
import com.gregtechceu.gtceu.data.recipe.generated.MaterialRecipeHandler;
import com.gregtechceu.gtceu.data.recipe.VanillaRecipeHelper;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.neoforged.neoforge.common.conditions.ICondition;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeGeneratedMaterialIntegrationTest {
    private static final class Output implements RecipeOutput {
        final Map<ResourceLocation, Recipe<?>> recipes = new LinkedHashMap<>();
        @Override public Advancement.Builder advancement() { return Advancement.Builder.advancement(); }
        @Override public void accept(ResourceLocation id, Recipe<?> recipe, AdvancementHolder advancement, ICondition... conditions) {
            assertNull(recipes.put(id, recipe), "Duplicate generated recipe: " + id);
        }
        GTRecipe find(GTRecipeType type, String suffix) {
            var id = ResourceLocation.fromNamespaceAndPath("gtceu", type.getId().getPath() + "/" + suffix);
            return (GTRecipe) Objects.requireNonNull(recipes.get(id), "Missing " + id + " from " + recipes.keySet());
        }
    }
    private static long firstOutput(GTRecipe recipe) {
        return LongIngredient.amount(ItemRecipeCapability.CAP.of(recipe.getOutputContents(ItemRecipeCapability.CAP).getFirst().content()));
    }

    @Test void nativeOreEntryRetainsCoreMultiplierIdsAndNativeDownstreamProcessors() {
        var output = new Output(); OreRecipeHandler.run(output, GTMaterials.Iron);
        int multiplier = GTMaterials.Iron.getProperty(PropertyKey.ORE).getOreMultiplier();
        int configured = org.gtlcore.gtlcore.config.ConfigHolder.INSTANCE.oreMultiplier;
        var ore = output.find(GTLRecipeTypes.INTEGRATED_ORE_PROCESSOR.value(), "integrated_ore_processor_1_iron");
        var raw = output.find(GTLRecipeTypes.INTEGRATED_ORE_PROCESSOR.value(), "raw_integrated_ore_processor_1_iron");
        assertSame(GTLRecipeTypes.INTEGRATED_ORE_PROCESSOR.value(), ore.getType());
        assertEquals(multiplier * 2L * configured, firstOutput(ore));
        assertEquals(multiplier * (long) configured, firstOutput(raw));
        assertEquals(26 + (26 + GTMaterials.Iron.getMass() * 4) * multiplier * 2 * configured, ore.duration);
        assertEquals(multiplier * (long) configured, firstOutput(output.find(GTRecipeTypes.FORGE_HAMMER_RECIPES.value(), "hammer_iron_ore_to_raw_ore")));
        assertSame(GTRecipeTypes.MACERATOR_RECIPES.value(), output.find(GTRecipeTypes.MACERATOR_RECIPES.value(), "macerate_iron_crushed_ore_to_impure_dust").getType());
        assertSame(GTRecipeTypes.ORE_WASHER_RECIPES.value(), output.find(GTRecipeTypes.ORE_WASHER_RECIPES.value(), "wash_iron_crushed_ore_to_purified_ore").getType());
        assertTrue(ore.id.toString().startsWith("gtceu:integrated_ore_processor/"));
    }

    @Test void nativeWashPairRetainsExactFluidAndFourByproductFallbacks() {
        var output = new Output(); OreRecipeHandler.run(output, GTMaterials.Copper);
        var property = GTMaterials.Copper.getProperty(PropertyKey.ORE);
        var ore = output.find(GTLRecipeTypes.INTEGRATED_ORE_PROCESSOR.value(), "integrated_ore_processor_6_copper");
        var fluid = FluidRecipeCapability.CAP.of(ore.getInputContents(FluidRecipeCapability.CAP).getFirst().content());
        long crushed = property.getOreMultiplier() * 2L * org.gtlcore.gtlcore.config.ConfigHolder.INSTANCE.oreMultiplier;
        assertEquals(property.getWashedIn().rightInt() * crushed, LongFluidIngredient.amount(fluid));
        assertTrue(LongFluidIngredient.matchingIngredient(fluid).test(property.getWashedIn().left().getFluid(1)));
        var gold = ChemicalHelper.get(TagPrefix.dust, property.getOreByProduct(3, GTMaterials.Copper));
        assertTrue(ore.getOutputContents(ItemRecipeCapability.CAP).stream()
                .map(content -> ItemRecipeCapability.CAP.of(content.content()).getItems())
                .anyMatch(stacks -> stacks.length > 0 && ItemStack.isSameItemSameComponents(gold, stacks[0])));
        assertNotNull(output.find(GTLRecipeTypes.INTEGRATED_ORE_PROCESSOR.value(), "raw_integrated_ore_processor_6_copper"));
        assertSame(GTRecipeTypes.CHEMICAL_BATH_RECIPES.value(), output.find(GTRecipeTypes.CHEMICAL_BATH_RECIPES.value(), "bathe_copper_crushed_ore_to_purified_ore").getType());
    }

    @Test void nativeDustProcessorRetainsElectricAndExplosiveImplosionRecipes() {
        var output = new Output(); MaterialRecipeHandler.run(output, GTMaterials.Diamond);
        var electric = output.find(GTLRecipeTypes.ELECTRIC_IMPLOSION_COMPRESSOR_RECIPES.value(), "electric_implode_dust_diamond_");
        assertSame(GTLRecipeTypes.ELECTRIC_IMPLOSION_COMPRESSOR_RECIPES.value(), electric.getType());
        assertEquals(4, LongIngredient.amount(ItemRecipeCapability.CAP.of(electric.getInputContents(ItemRecipeCapability.CAP).getFirst().content())));
        assertEquals(3, firstOutput(electric));
        var explosive = output.find(GTRecipeTypes.IMPLOSION_RECIPES.value(), "implode_dust_diamond__tnt");
        assertSame(GTRecipeTypes.IMPLOSION_RECIPES.value(), explosive.getType());
        assertTrue(explosive.getInputContents(ItemRecipeCapability.CAP).size() >= 2);
        assertEquals(3, firstOutput(explosive));
    }

    @Test void nativeBlastingOverloadsKeepSeparateIdsTypesTimingAndOutputCounts() {
        var input = ChemicalHelper.get(TagPrefix.dust, GTMaterials.Iron);
        var result = ChemicalHelper.get(TagPrefix.ingot, GTMaterials.Iron, 7);
        var output = new Output();
        for (var kind : List.of("tag", "ingredient", "stack")) {
            String name = "native_blasting_" + kind;
            switch (kind) {
                case "tag" -> {
                    var tag = ChemicalHelper.getTag(TagPrefix.dust, GTMaterials.Iron);
                    VanillaRecipeHelper.addSmeltingRecipe(output, name, tag, result, 0.5f);
                    VanillaRecipeHelper.addBlastingRecipe(output, name, tag, result, 0.5f);
                }
                case "ingredient" -> {
                    var ingredient = Ingredient.of(input);
                    VanillaRecipeHelper.addSmeltingRecipe(output, name, ingredient, result, 0.5f);
                    VanillaRecipeHelper.addBlastingRecipe(output, name, ingredient, result, 0.5f);
                }
                case "stack" -> {
                    VanillaRecipeHelper.addSmeltingRecipe(output, name, input, result, 0.5f);
                    VanillaRecipeHelper.addBlastingRecipe(output, name, input, result, 0.5f);
                }
            }
            var smelting = (SmeltingRecipe) output.recipes.get(ResourceLocation.fromNamespaceAndPath("gtceu", "smelting/" + name));
            var blasting = (BlastingRecipe) output.recipes.get(ResourceLocation.fromNamespaceAndPath("gtceu", "blasting/" + name));
            assertNotNull(smelting); assertNotNull(blasting);
            assertEquals(200, smelting.getCookingTime()); assertEquals(100, blasting.getCookingTime());
            assertEquals(7, blasting.getResultItem(net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY)).getCount());
            assertEquals(0.5f, blasting.getExperience());
        }
        assertEquals(6, output.recipes.size());
    }
}
