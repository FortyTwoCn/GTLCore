package org.gtlcore.gtlcore.mixin.gtm.api.recipe;

import org.gtlcore.gtlcore.api.recipe.NativeModifierSupport;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ModifierFunction.FunctionBuilder.class)
public abstract class NativeModifierFunctionMixin {
    @Shadow(remap = false) private int parallels, subtickParallels, batchParallels;
    @Inject(method = "build", at = @At("RETURN"), remap = false, require = 1, cancellable = true)
    private void gtlcore$exactNativeCounts(CallbackInfoReturnable<ModifierFunction> cir) {
        if (parallels == 0) return;
        var function = cir.getReturnValue();
        int regular = parallels, subticks = subtickParallels, batches = batchParallels;
        cir.setReturnValue(recipe -> NativeModifierSupport.apply(function, recipe, regular, subticks, batches));
    }
}
