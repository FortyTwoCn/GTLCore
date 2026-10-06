package org.gtlcore.gtlcore.mixin.gtm.api.recipe;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native JSON carries the ID in RecipeHolder, outside the recipe's codec. */
@Mixin(RecipeHolder.class)
public abstract class NativeRecipeHolderIdsMixin {
    @Inject(method = "<init>", at = @At("RETURN"), require = 1)
    private void gtlcore$initializeRecipeId(ResourceLocation id, Recipe<?> recipe, CallbackInfo ci) {
        // Initialize before any native child regeneration, and keep decoded/network IDs canonical.
        if (recipe instanceof GTRecipe gtRecipe) gtRecipe.setId(id);
    }
}
