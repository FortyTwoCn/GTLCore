package org.gtlcore.gtlcore.mixin.ae2.client;

import org.gtlcore.gtlcore.GTLCore;

import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;

import appeng.hooks.BuiltInModelHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Let AE2's native model loader resolve the Core models in its built-in registry. */
@Mixin(BuiltInModelHooks.class)
public abstract class ModelBakeryMixin {

    @Inject(method = "getBuiltInModel", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private static void gtlcore$builtInModel(ResourceLocation id, CallbackInfoReturnable<UnbakedModel> cir) {
        if (id.getNamespace().equals(GTLCore.MOD_ID)) {
            var model = BuiltInModelHooksAccessor.getBuiltInModels().get(id);
            if (model != null) cir.setReturnValue(model);
        }
    }
}
