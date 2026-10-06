package org.gtlcore.gtlcore.api.recipe;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.*;
import com.gregtechceu.gtceu.api.recipe.OverclockingLogic.*;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.utils.GTUtil;

/** Core overclock behavior expressed as native factors, with input-bounded partial sub-tick parallels. */
public final class CoreOverclockingLogic {
    private CoreOverclockingLogic() {}
    public static OCResult calculate(OCParams params, long maxVoltage, double durationFactor, double voltageFactor, boolean subtick) {
        if (!(durationFactor > 0 && durationFactor <= 1) || !(voltageFactor >= 1) || !Double.isFinite(voltageFactor))
            throw new IllegalArgumentException("Invalid overclock factors");
        double duration = params.duration(), eut = params.eut(), parallel = 1, energyMultiplier = 1, timeMultiplier = 1;
        int baseLevel = 0, level = 0;
        boolean available = false, parallelizing = false;
        int limit = Math.max(1, params.maxParallels());
        for (int remaining = params.ocAmount(); remaining > 0; remaining--) {
            double potentialEUt = eut * voltageFactor;
            if (!Double.isFinite(potentialEUt) || potentialEUt > maxVoltage) break;
            if (parallelizing || duration * durationFactor < 1) {
                available = true;
                if (!subtick) break;
                double requested = parallel / durationFactor;
                int applied = (int) Math.min(requested, limit);
                if (applied <= parallel) break;
                double ratio = applied / requested;
                energyMultiplier *= voltageFactor * ratio;
                eut = potentialEUt * ratio;
                parallel = applied; level++; parallelizing = true;
                if (applied < requested) break;
            } else {
                duration *= durationFactor; timeMultiplier *= durationFactor;
                eut = potentialEUt; energyMultiplier *= voltageFactor; baseLevel++; level++;
            }
        }
        var result = new OCResult(energyMultiplier, timeMultiplier, level, (int) parallel);
        ((IAdvancedOCResult) (Object) result).gtlcore$setOverclockInfo(baseLevel, durationFactor, voltageFactor, available);
        return result;
    }

    public static ModifierFunction modifier(OverclockingLogic logic, MetaMachine machine, GTRecipe recipe, long maxVoltage, boolean shouldParallel) {
        long eut = RecipeHelper.getRealEUt(recipe).getTotalEU();
        if (eut == 0 || recipe.duration <= 0) return ModifierFunction.IDENTITY;
        int recipeTier = GTUtil.getTierByVoltage(eut);
        int maxTier = maxVoltage < 32 ? 0 : 1 + ((Long.SIZE - 1 - Long.numberOfLeadingZeros(maxVoltage >> 5)) >> 1);
        int amount = maxTier - recipeTier - (recipeTier == GTValues.ULV ? 1 : 0);
        if (amount <= 0) return ModifierFunction.IDENTITY;
        boolean allowParallel = shouldParallel && logic != OverclockingLogic.PERFECT_OVERCLOCK && logic != OverclockingLogic.NON_PERFECT_OVERCLOCK;
        var unbounded = logic.runOverclockingLogic(new OCParams(eut, recipe.duration, amount, allowParallel ? Integer.MAX_VALUE : 1), maxVoltage);
        int limit = unbounded.parallels();
        if (limit > 1 && machine instanceof IRecipeLogicMachine holder) {
            limit = (int) IParallelLogic.getParallel(holder, recipe, limit);
            for (var cap : recipe.inputs.keySet()) if (cap != ItemRecipeCapability.CAP && cap != FluidRecipeCapability.CAP && cap.doMatchInRecipe())
                limit = Math.min(limit, cap.getMaxParallelByInput(holder, recipe, limit, false));
            for (var cap : recipe.tickInputs.keySet()) if (cap != EURecipeCapability.CAP && cap.doMatchInRecipe())
                limit = Math.min(limit, cap.getMaxParallelByInput(holder, recipe, limit, true));
            if (limit == 0) return ModifierFunction.NULL;
        }
        var result = logic.runOverclockingLogic(new OCParams(eut, recipe.duration, amount, Math.max(1, limit)), maxVoltage);
        return result.toModifier();
    }
}
