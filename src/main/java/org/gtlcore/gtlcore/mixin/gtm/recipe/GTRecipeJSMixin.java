package org.gtlcore.gtlcore.mixin.gtm.recipe;

import org.gtlcore.gtlcore.utils.Registries;

import com.gregtechceu.gtceu.integration.kjs.recipe.GTRecipeSchema;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import net.neoforged.neoforge.common.crafting.SizedIngredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(GTRecipeSchema.GTKubeRecipe.class)
public abstract class GTRecipeJSMixin {

    @Shadow(remap = false)
    public int chance;

    /**
     * Special handling for kubejs:ingot_field_shape: convert quantity from 64 to 8
     */
    @ModifyArg(
               method = "notConsumableItem(Lnet/neoforged/neoforge/common/crafting/SizedIngredient;)Lcom/gregtechceu/gtceu/integration/kjs/recipe/GTRecipeSchema$GTKubeRecipe;",
               at = @At(value = "INVOKE", target = "Lcom/gregtechceu/gtceu/integration/kjs/recipe/GTRecipeSchema$GTKubeRecipe;inputItems([Lnet/neoforged/neoforge/common/crafting/SizedIngredient;)Lcom/gregtechceu/gtceu/integration/kjs/recipe/GTRecipeSchema$GTKubeRecipe;"),
               index = 0,
               remap = false)
    private SizedIngredient[] modifyInputItemInNotConsumable(SizedIngredient[] inputs) {
        // Check if we have exactly one input item and it needs modification
        if (inputs.length == 1) {
            SizedIngredient itemStack = inputs[0];
            if (itemStack.count() == 64) {
                Ingredient ingredient = itemStack.ingredient();
                ItemStack[] items = ingredient.getItems();

                if (items.length > 0) {
                    String itemId = Registries.getItemId(items[0]);
                    if ("kubejs:ingot_field_shape".equals(itemId)) {
                        // Return a new array with modified InputItem (count 8 instead of 64)
                        return new SizedIngredient[] { new SizedIngredient(ingredient, 8) };
                    }
                }
            }
        }

        // Return the original inputs array if no modification needed
        return inputs;
    }
}
