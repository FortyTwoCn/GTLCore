package org.gtlcore.gtlcore.mixin.gtm;

import org.gtlcore.gtlcore.api.recipe.CoreOverclockingLogic;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.OverclockingLogic;
import com.gregtechceu.gtceu.api.recipe.OverclockingLogic.*;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(OverclockingLogic.class)
public interface OverclockingLogicMixin {
    /** @author GTLCore @reason Preserve Core voltage tiers and selected-bus sub-tick limits using native records. */
    @Overwrite(remap = false)
    default ModifierFunction getModifier(MetaMachine machine, GTRecipe recipe, long maxVoltage, boolean shouldParallel) {
        return CoreOverclockingLogic.modifier((OverclockingLogic) (Object) this, machine, recipe, maxVoltage, shouldParallel);
    }
    /** @author GTLCore @reason Track unused sub-tick headroom for the Core batch policy. */
    @Overwrite(remap = false)
    static OCResult standardOC(OCParams params, long maxVoltage, double durationFactor, double voltageFactor) {
        return CoreOverclockingLogic.calculate(params, maxVoltage, durationFactor, voltageFactor, false);
    }
    /** @author GTLCore @reason Preserve partial sub-tick parallel EU and tier calculation. */
    @Overwrite(remap = false)
    static OCResult subTickParallelOC(OCParams params, long maxVoltage, double durationFactor, double voltageFactor) {
        return CoreOverclockingLogic.calculate(params, maxVoltage, durationFactor, voltageFactor, true);
    }
    /** @author GTLCore @reason Preserve Core's perfect heating-coil overclocks. Temperature checking and discounts remain native. */
    @Overwrite(remap = false)
    static OCResult heatingCoilOC(OCParams params, long maxVoltage, int recipeTemp, int machineTemp) {
        return CoreOverclockingLogic.calculate(params, maxVoltage, OverclockingLogic.PERFECT_DURATION_FACTOR, OverclockingLogic.STD_VOLTAGE_FACTOR, true);
    }
}
