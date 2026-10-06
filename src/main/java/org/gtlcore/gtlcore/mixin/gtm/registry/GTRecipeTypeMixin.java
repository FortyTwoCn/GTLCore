package org.gtlcore.gtlcore.mixin.gtm.registry;

import org.gtlcore.gtlcore.common.recipe.RecipeModify;

import com.gregtechceu.gtceu.api.recipe.GTRecipeType;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GTRecipeType.class)
public class GTRecipeTypeMixin {
    @Inject(method = "<init>", at = @At("TAIL"), remap = false, require = 1)
    private void gtlcore$nativeRecipeCallbacks(CallbackInfo ci) {
        RecipeModify.modifyOnRegistration((GTRecipeType) (Object) this);
    }
}
