package org.gtlcore.gtlcore.mixin.gtm.api.pattern;

import org.gtlcore.gtlcore.api.pattern.NativeLimitedPredicateMatches;
import com.gregtechceu.gtceu.api.multiblock.MultiPredicate;
import com.gregtechceu.gtceu.api.multiblock.PredicateContext;
import com.gregtechceu.gtceu.api.multiblock.predicates.BasePredicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MultiPredicate.class, remap = false)
public abstract class TraceabilityPredicateMixin {
    @Shadow protected abstract void onError(PredicateContext context);

    @Inject(method = "getPredicateAtPos", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtlcore$countAllMatchingLimits(PredicateContext context, CallbackInfoReturnable<BasePredicate> cir) {
        var owner = (MultiPredicate) (Object) this;
        if (!NativeLimitedPredicateMatches.appliesTo(owner)) return;
        var predicate = NativeLimitedPredicateMatches.atPosition(owner, context);
        if (predicate == null) onError(context);
        cir.setReturnValue(predicate);
    }
}
