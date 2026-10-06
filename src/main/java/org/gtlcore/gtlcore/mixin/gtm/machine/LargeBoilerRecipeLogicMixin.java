package org.gtlcore.gtlcore.mixin.gtm.machine;

import org.gtlcore.gtlcore.api.recipe.NativeLargeBoilerFuel;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.common.machine.multiblock.steam.LargeBoilerMachine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = LargeBoilerMachine.LargeBoilerRecipeLogic.class, remap = false)
public abstract class LargeBoilerRecipeLogicMixin extends RecipeLogic {
    @Shadow int currentThrottle;

    @ModifyVariable(method = "setCurrentThrottle", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 1)
    private int gtlcore$validThrottle(int value) { return NativeLargeBoilerFuel.throttle(value); }

    /** @author GTLCore @reason Preserve native throttle accounting with exact, bounded integer arithmetic. */
    @Overwrite public void modifyFuelBurnTime(int newThrottle) {
        if (lastRecipe == null) return;
        duration = NativeLargeBoilerFuel.burnTicks(lastRecipe.duration, newThrottle);
        progress = NativeLargeBoilerFuel.progress(progress, currentThrottle, newThrottle, duration);
    }
}
