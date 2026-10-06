package org.gtlcore.gtlcore.mixin.gtm.api.pattern;

import org.gtlcore.gtlcore.api.pattern.PatternMatchData;

import com.gregtechceu.gtceu.api.multiblock.PredicateContext;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PredicateContext.class, remap = false)
public abstract class PredicateContextMixin implements PatternMatchData.Access {
    @Unique
    private final PatternMatchData gtlcore$matchData = new PatternMatchData();

    @Override
    public PatternMatchData gtlcore$getPatternMatchData() {
        return gtlcore$matchData;
    }

    @Inject(method = "reset", at = @At("HEAD"), require = 1)
    private void gtlcore$resetMatchData(CallbackInfo ci) {
        gtlcore$matchData.reset();
    }

    @Inject(method = "pushSlice", at = @At("HEAD"), require = 1)
    private void gtlcore$pushMatchData(CallbackInfo ci) {
        gtlcore$matchData.pushSlice();
    }

    @Inject(method = "popSlice", at = @At("HEAD"), require = 1)
    private void gtlcore$popMatchData(CallbackInfo ci) {
        gtlcore$matchData.popSlice();
    }
}
