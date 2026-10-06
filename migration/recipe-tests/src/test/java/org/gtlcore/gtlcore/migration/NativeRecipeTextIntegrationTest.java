package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.recipe.RecipeText;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeRecipeTextIntegrationTest {
    private static TranslatableContents translated(Component component) {
        return (TranslatableContents) component.getContents();
    }
    @Test void machineRecipeTextKeepsExactLongItemAndFluidAmountsIncludingFractionalBuckets() {
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_long_recipe_text")
                .inputItems(LongIngredient.create(Ingredient.of(Items.IRON_INGOT), 9_007_199_254_740_993L))
                .inputFluids(LongFluidIngredient.create(Fluids.WATER, 9_007_199_254_741_001L)).build();
        var lines = RecipeText.getRecipeInputText(recipe).getSiblings();
        assertEquals(9_007_199_254_740_993L, translated(lines.get(0)).getArgs()[1]);
        assertEquals("9007199254741.001B", translated(lines.get(1)).getArgs()[1]);
        assertEquals(9_007_199_254_740_993L, LongIngredient.amount(ItemRecipeCapability.CAP.of(recipe.inputs.get(ItemRecipeCapability.CAP).getFirst().content())));
    }
    @Test void machineOutputTextRetainsNotConsumedAndProbabilityLabelsWithLongQuantities() {
        var builder = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_chanced_recipe_text")
                .outputItems(LongIngredient.create(Ingredient.of(Items.DIAMOND), 3_000_000_003L)).outputItems(Items.IRON_INGOT);
        var output = builder.output.get(ItemRecipeCapability.CAP);
        output.set(0, new Content(output.get(0).content(), 5000, 10000));
        output.set(1, new Content(output.get(1).content(), 0, 10000));
        var lines = RecipeText.getRecipeOutputText(builder.build()).getSiblings();
        assertEquals("gtceu.machine.lockrecipe.line.1", translated(lines.get(0)).getKey());
        assertEquals(3_000_000_003L, translated(lines.get(0)).getArgs()[1]);
        assertEquals("gtceu.machine.lockrecipe.line.2", translated(lines.get(1)).getKey());
    }
    @Test void missingRecipesAndEmptyNativeContentsRemainEmptyWithoutInventingAnIngredient() {
        assertNull(RecipeText.getRecipeInputText(null)); assertNull(RecipeText.getRecipeOutputText(null));
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_empty_recipe_text").build();
        assertTrue(RecipeText.getRecipeInputText(recipe).getSiblings().isEmpty());
        assertTrue(RecipeText.getRecipeOutputText(recipe).getSiblings().isEmpty());
    }
}
