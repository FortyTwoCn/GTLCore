package org.gtlcore.gtlcore.mixin.gtm;

import com.gregtechceu.gtceu.api.recipe.category.GTRecipeCategory;
import com.gregtechceu.gtceu.integration.recipeviewer.jei.GTJEIPlugin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GTJEIPlugin.class)
public abstract class GTJEIPluginMixin {

    @Redirect(
              method = "registerCategories",
              at = @At(
                       value = "INVOKE",
                       target = "Lcom/gregtechceu/gtceu/api/recipe/category/GTRecipeCategory;shouldRegisterDisplays()Z"),
              remap = false, require = 1)
    private boolean gtlcore$visibleNativeCategory(@Nullable GTRecipeCategory category) {
        return category != null && category.shouldRegisterDisplays();
    }
}
