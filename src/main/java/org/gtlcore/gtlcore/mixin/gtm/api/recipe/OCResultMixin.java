package org.gtlcore.gtlcore.mixin.gtm.api.recipe;

import org.gtlcore.gtlcore.api.recipe.IAdvancedOCResult;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import com.gregtechceu.gtceu.api.recipe.OverclockingLogic.OCResult;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(OCResult.class)
public abstract class OCResultMixin implements IAdvancedOCResult {
    @Shadow(remap = false) @Final private int ocLevel;
    @Unique private int gtlcore$baseOCLevel;
    @Unique private double gtlcore$durationFactor, gtlcore$voltageFactor;
    @Unique private boolean gtlcore$hasInfo, gtlcore$subTickAvailable;
    @Override public void gtlcore$setOverclockInfo(int base, double duration, double voltage, boolean available) {
        gtlcore$baseOCLevel = base; gtlcore$durationFactor = duration; gtlcore$voltageFactor = voltage;
        gtlcore$subTickAvailable = available; gtlcore$hasInfo = true;
    }
    @Override public int getBaseOCLevel() { return gtlcore$hasInfo ? gtlcore$baseOCLevel : ocLevel; }
    @Override public double getDurationFactor() { return gtlcore$durationFactor; }
    @Override public double getVoltageFactor() { return gtlcore$voltageFactor; }
    @Override public boolean isSubTickOverclockAvailable() { return gtlcore$subTickAvailable; }
    @Inject(method = "toModifier", at = @At("RETURN"), remap = false, require = 1, cancellable = true)
    private void gtlcore$markHeadroom(CallbackInfoReturnable<ModifierFunction> cir) {
        if (!gtlcore$subTickAvailable) return;
        var nativeModifier = cir.getReturnValue();
        cir.setReturnValue(recipe -> {
            var result = nativeModifier.apply(recipe);
            if (result != null) IGTRecipe.of(result).setSubTickParallelized(true);
            return result;
        });
    }
}
