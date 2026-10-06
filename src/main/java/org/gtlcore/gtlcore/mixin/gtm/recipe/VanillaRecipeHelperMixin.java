package org.gtlcore.gtlcore.mixin.gtm.recipe;

import com.gregtechceu.gtceu.data.recipe.VanillaRecipeHelper;
import com.gregtechceu.gtceu.data.recipe.builder.SimpleCookingRecipeBuilder;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Preserve blasting semantics in the pinned GT fork's ingredient/tag helper overloads. */
@Mixin(VanillaRecipeHelper.class)
public abstract class VanillaRecipeHelperMixin {
    @WrapOperation(method = {
            "addBlastingRecipe(Lnet/minecraft/data/recipes/RecipeOutput;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/world/item/crafting/Ingredient;Lnet/minecraft/world/item/ItemStack;F)V",
            "addBlastingRecipe(Lnet/minecraft/data/recipes/RecipeOutput;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/tags/TagKey;Lnet/minecraft/world/item/ItemStack;F)V"
    }, at = @At(value = "INVOKE", target = "Lcom/gregtechceu/gtceu/data/recipe/builder/SimpleCookingRecipeBuilder;smelting(Lnet/minecraft/resources/ResourceLocation;)Lcom/gregtechceu/gtceu/data/recipe/builder/SimpleCookingRecipeBuilder;"), remap = false)
    private static SimpleCookingRecipeBuilder<?> gtlcore$nativeBlasting(ResourceLocation id, Operation<SimpleCookingRecipeBuilder<?>> original) {
        return SimpleCookingRecipeBuilder.blasting(id);
    }

    @ModifyArg(method = {
            "addBlastingRecipe(Lnet/minecraft/data/recipes/RecipeOutput;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/world/item/crafting/Ingredient;Lnet/minecraft/world/item/ItemStack;F)V",
            "addBlastingRecipe(Lnet/minecraft/data/recipes/RecipeOutput;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/tags/TagKey;Lnet/minecraft/world/item/ItemStack;F)V",
            "addBlastingRecipe(Lnet/minecraft/data/recipes/RecipeOutput;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;F)V"
    }, at = @At(value = "INVOKE", target = "Lcom/gregtechceu/gtceu/data/recipe/builder/SimpleCookingRecipeBuilder;cookingTime(I)Lcom/gregtechceu/gtceu/data/recipe/builder/SimpleCookingRecipeBuilder;"), index = 0, remap = false)
    private static int gtlcore$blastingCookingTime(int time) {
        return Math.max(1, time / 2);
    }
}
