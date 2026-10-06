package org.gtlcore.gtlcore.mixin.gtm.api.recipe;

import org.gtlcore.gtlcore.api.recipe.RecipeMultiplierTracker;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.modifier.*;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.*;

/** Apply the native modifier chain once, preserving Core's pre-overclock reduction information. */
@Mixin(RecipeModifierList.class)
public abstract class RecipeModifierListMixin {
    @Shadow(remap = false) @Final private RecipeModifier[] modifiers;
    /** @author GTLCore @reason Use native ModifierFunction and capture reduction before overclocking. Batch setup remains in native RecipeLogic. */
    @Overwrite(remap = false)
    public ModifierFunction getModifier(MetaMachine machine, GTRecipe recipe) {
        return new ModifierFunction() {
            private Component reason = DEFAULT_FAILURE;
            @Override public GTRecipe apply(GTRecipe input) {
                GTRecipe running = input;
                RecipeMultiplierTracker.begin(machine, input);
                try {
                    for (var modifier : modifiers) {
                        var before = running;
                        var function = modifier.getModifier(machine, before);
                        running = function.apply(before);
                        if (running == null) {
                            reason = function.getFailReason();
                            RecipeLogic.putFailureReason(machine, input, reason);
                            return null;
                        }
                        if (running.ocLevel > before.ocLevel) RecipeMultiplierTracker.captureBeforeOverclock(machine, before);
                    }
                    return running;
                } finally { RecipeMultiplierTracker.finish(machine, running); }
            }
            @Override public Component getFailReason() { return reason; }
        };
    }
}
