package org.gtlcore.gtlcore.mixin.gtm.api.recipe;

import org.gtlcore.gtlcore.api.recipe.IAdvancedContentModifier;

import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native primitive overloads retain exact fractions used by very large batches. */
@Mixin(ContentModifier.class)
public abstract class ContentModifierMixin implements IAdvancedContentModifier {
    @Unique private long gtlcore$numerator;
    @Unique private long gtlcore$denominator;
    @Unique private boolean gtlcore$useFraction;

    @Inject(method = "apply(J)J", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void gtlcore$applyLong(long value, CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(IAdvancedContentModifier.applyLong((ContentModifier) (Object) this, value));
    }

    @Inject(method = "apply(I)I", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void gtlcore$applyInt(int value, CallbackInfoReturnable<Integer> cir) {
        long result = IAdvancedContentModifier.applyLong((ContentModifier) (Object) this, value);
        cir.setReturnValue((int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, result)));
    }

    @Override
    public void setDivision(long numerator, long denominator) {
        if (denominator == 0) throw new IllegalArgumentException("Recipe quantity denominator cannot be zero");
        gtlcore$numerator = numerator;
        gtlcore$denominator = denominator;
        gtlcore$useFraction = true;
    }

    @Override public boolean gtlcore$hasDivision() { return gtlcore$useFraction; }
    @Override public long gtlcore$getNumerator() { return gtlcore$numerator; }
    @Override public long gtlcore$getDenominator() { return gtlcore$denominator; }
}
