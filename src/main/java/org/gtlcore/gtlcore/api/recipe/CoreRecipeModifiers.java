package org.gtlcore.gtlcore.api.recipe;

import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.mojang.datafixers.util.Pair;

/** Native equivalents of the Core parallel operations removed from GTCEu's public API. */
public final class CoreRecipeModifiers {
    private CoreRecipeModifiers() {}
    public static int parallelAmount(MetaMachine machine, GTRecipe recipe, int limit) {
        return parallelAmount(machine, recipe, limit, true);
    }
    /** Generator power comes from its own voltage/amperage budget instead of a one-amp OC tier. */
    public static int parallelAmount(MetaMachine machine, GTRecipe recipe, int limit, boolean limitTickEnergyTier) {
        if (!(machine instanceof IRecipeLogicMachine holder) || limit <= 0) return 0;
        limit = (int) Math.min(limit, Long.MAX_VALUE / IGTRecipe.of(recipe).getRealParallels());
        limit = (int) IParallelLogic.getParallel(holder, recipe, limit);
        for (var cap : recipe.inputs.keySet()) if (cap != ItemRecipeCapability.CAP && cap != FluidRecipeCapability.CAP && cap.doMatchInRecipe())
            limit = Math.min(limit, cap.getMaxParallelByInput(holder, recipe, limit, false));
        for (var cap : recipe.tickInputs.keySet()) if (cap.doMatchInRecipe() && (limitTickEnergyTier || cap != EURecipeCapability.CAP))
            limit = Math.min(limit, cap.getMaxParallelByInput(holder, recipe, limit, true));
        for (var cap : recipe.outputs.keySet()) if (cap != ItemRecipeCapability.CAP && cap != FluidRecipeCapability.CAP && cap.doMatchInRecipe())
            limit = Math.min(limit, cap.limitMaxParallelByOutput(holder, recipe, limit, false));
        for (var cap : recipe.tickOutputs.keySet()) if (cap.doMatchInRecipe() && (limitTickEnergyTier || cap != EURecipeCapability.CAP))
            limit = Math.min(limit, cap.limitMaxParallelByOutput(holder, recipe, limit, true));
        return limit;
    }
    public static Pair<GTRecipe, Integer> accurateParallel(MetaMachine machine, GTRecipe recipe, int limit, boolean modifyDuration) {
        int amount = parallelAmount(machine, recipe, limit);
        if (amount == 0) return Pair.of(null, 0);
        var result = recipe.copy(IAdvancedContentModifier.preciseMultiplier(amount), modifyDuration);
        IGTRecipe.of(result).setRealParallels(Math.multiplyExact(IGTRecipe.of(recipe).getRealParallels(), amount));
        return Pair.of(result, amount);
    }
    public static Pair<GTRecipe, Integer> fastParallel(MetaMachine machine, GTRecipe recipe, int limit, boolean modifyDuration) {
        if (!(machine instanceof IRecipeCapabilityHolder holder)) return Pair.of(null, 0);
        limit = (int) Math.min(limit, Long.MAX_VALUE / IGTRecipe.of(recipe).getRealParallels());
        while (limit > 0) {
            var result = recipe.copy(IAdvancedContentModifier.preciseMultiplier(limit), modifyDuration);
            IGTRecipe.of(result).setRealParallels(Math.multiplyExact(IGTRecipe.of(recipe).getRealParallels(), limit));
            if (RecipeRunnerHelper.matchRecipe(holder, result) && RecipeHelper.matchTickRecipe(holder, result).isSuccess()) return Pair.of(result, limit);
            limit /= 2;
        }
        return Pair.of(null, 0);
    }
    public static ModifierFunction hatchParallel(MetaMachine machine, GTRecipe recipe) {
        if (!(machine instanceof IRecipeCapabilityMachine core) || !(machine instanceof com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine controller) || !controller.isFormed())
            return ModifierFunction.IDENTITY;
        int maximum = core.getParallelHatch().map(hatch -> hatch.getCurrentParallel()).orElse(1);
        if (maximum <= 1) return ModifierFunction.IDENTITY;
        int amount = parallelAmount(machine, recipe, maximum);
        if (amount == 0) return ModifierFunction.NULL;
        return ModifierFunction.builder().modifyAllContents(IAdvancedContentModifier.preciseMultiplier(amount)).eutMultiplier(amount).parallels(amount).build();
    }
}
