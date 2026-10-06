package org.gtlcore.gtlcore.mixin.gtm.api.recipe;

import org.gtlcore.gtlcore.api.recipe.NativeRecipeState;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer;
import com.mojang.serialization.MapCodec;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Uses native registry-aware JSON/NBT and streams; no removed LDLib recipe payload is required. */
@Mixin(GTRecipeSerializer.class)
public abstract class NativeRecipeSerializerMixin {
    @ModifyReturnValue(method = "makeCodec", at = @At("RETURN"), remap = false, require = 1)
    private static MapCodec<GTRecipe> gtlcore$stateCodec(MapCodec<GTRecipe> original, boolean kubeLoaded) {
        return original.xmap(NativeRecipeState::restore, NativeRecipeState::snapshot);
    }
    @ModifyVariable(method = "toNetwork", at = @At("HEAD"), argsOnly = true, remap = false, require = 1)
    private static GTRecipe gtlcore$stateSnapshot(GTRecipe recipe) { return NativeRecipeState.snapshot(recipe); }
    @Inject(method = "fromNetwork", at = @At("RETURN"), remap = false, require = 1)
    private static void gtlcore$restoreState(CallbackInfoReturnable<GTRecipe> cir) { NativeRecipeState.restore(cir.getReturnValue()); }
}
