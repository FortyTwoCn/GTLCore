package org.gtlcore.gtlcore.mixin.gtm.api.capability;

import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongItemParallelLogic;

import com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native entry points; ordinary ingredients keep GTCEu's implementation. */
@Mixin(ItemRecipeCapability.class)
public class ItemRecipeCapabilityMixin {
    @Inject(method = "getMaxParallelByInput", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void gtlcore$longInputParallel(IRecipeCapabilityHolder holder, GTRecipe recipe, int limit,
                                          boolean tick, CallbackInfoReturnable<Integer> cir) {
        var contents = (tick ? recipe.tickInputs : recipe.inputs).get(ItemRecipeCapability.CAP);
        if (LongItemParallelLogic.hasLongIngredients(contents) || LongItemParallelLogic.hasLongInventory(holder)) {
            cir.setReturnValue((int) LongItemParallelLogic.getMaxParallelByInput(holder, recipe, limit, tick));
        }
    }

    @Inject(method = "limitMaxParallelByOutput", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void gtlcore$longOutputParallel(IRecipeCapabilityHolder holder, GTRecipe recipe, int limit,
                                           boolean tick, CallbackInfoReturnable<Integer> cir) {
        if (holder instanceof ItemRecipeCapability.ICustomParallel) return;
        var contents = (tick ? recipe.tickOutputs : recipe.outputs).get(ItemRecipeCapability.CAP);
        if (contents == null || contents.isEmpty()) return;
        boolean large = LongItemParallelLogic.hasLongIngredients(contents) || contents.stream().anyMatch(content ->
                LongIngredient.amount(ItemRecipeCapability.CAP.of(content.content())) * (double) limit > Integer.MAX_VALUE);
        if (large) cir.setReturnValue((int) LongItemParallelLogic.limitMaxParallelByOutput(holder, recipe, limit, tick));
    }
}
