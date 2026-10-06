package org.gtlcore.gtlcore.mixin.gtm.gui;

import org.gtlcore.gtlcore.integration.ae2.gui.NativeAEAmounts;
import com.gregtechceu.gtceu.integration.ae2.gui.AEGuiHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AEGuiHelper.class, remap = false)
public abstract class AmountSetWidgetMixin {
    @Inject(method = "parseAmount", at = @At("HEAD"), cancellable = true, require = 1)
    private static void gtlcore$exactLongAmount(String text, CallbackInfoReturnable<Long> cir) { cir.setReturnValue(NativeAEAmounts.parse(text)); }
}
