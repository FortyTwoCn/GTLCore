package org.gtlcore.gtlcore.mixin.gtm.api.recipe;

import org.gtlcore.gtlcore.api.recipe.IParallelLogic;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import net.minecraft.network.chat.Component;
import com.google.common.primitives.Ints;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.function.Predicate;

/** Native GT parallel entry points with Core's exact quantities and selected input route. */
@Mixin(ParallelLogic.class)
public abstract class ParallelLogicMixin {
    /** @author GTLCore @reason Preserve long input quantities, routing and native capability exclusions. */
    @Overwrite(remap = false)
    public static int getMaxByInput(IRecipeCapabilityHolder holder, GTRecipe recipe, int parallelLimit,
                                    List<RecipeCapability<?>> capsToSkip) {
        if (parallelLimit <= 0) return 0;
        int minimum = parallelLimit;
        var routeInputs = new LinkedHashMap<>(recipe.inputs);
        routeInputs.keySet().removeIf(cap -> !cap.doMatchInRecipe() || capsToSkip.contains(cap));
        boolean hasInput = false;
        for (var cap : recipe.inputs.keySet()) {
            if (!cap.doMatchInRecipe() || capsToSkip.contains(cap)) continue;
            hasInput = true;
            int possible;
            if (cap == ItemRecipeCapability.CAP) {
                possible = Ints.saturatedCast(IParallelLogic.getInputItemParallel(holder, recipe, minimum, routeInputs));
            } else if (cap == FluidRecipeCapability.CAP) {
                possible = Ints.saturatedCast(IParallelLogic.getInputFluidParallel(holder, recipe, minimum, routeInputs));
            } else {
                possible = cap.getMaxParallelByInput(holder, recipe, minimum, false);
            }
            if (possible == 0) return gtlcore$inputFailure(holder, recipe, cap);
            minimum = Math.min(minimum, possible);
        }
        for (var cap : recipe.tickInputs.keySet()) {
            if (!cap.doMatchInRecipe() || capsToSkip.contains(cap)) continue;
            hasInput = true;
            int possible = cap.getMaxParallelByInput(holder, recipe, minimum, true);
            if (possible == 0) return gtlcore$inputFailure(holder, recipe, cap);
            minimum = Math.min(minimum, possible);
        }
        if (!hasInput) {
            RecipeLogic.putFailureReason(holder, recipe, Component.translatable("gtceu.recipe_logic.no_capabilities")
                    .append(": ").append(Component.translatable(IO.IN.getTooltip())));
            return 0;
        }
        return minimum;
    }

    @Unique
    private static int gtlcore$inputFailure(IRecipeCapabilityHolder holder, GTRecipe recipe, RecipeCapability<?> cap) {
        RecipeLogic.putFailureReason(holder, recipe, Component.translatable("gtceu.recipe_logic.insufficient_in")
                .append(": ").append(cap.getName()));
        return 0;
    }

    /** @author GTLCore @reason Preserve exact output reservations, chance previews and native voiding/exclusions. */
    @Overwrite(remap = false)
    public static int limitByOutputMerging(IRecipeCapabilityHolder holder, GTRecipe recipe, int parallelLimit,
                                           Predicate<RecipeCapability<?>> canVoid,
                                           List<RecipeCapability<?>> capsToSkip) {
        int maximum = Math.max(0, parallelLimit);
        for (var cap : recipe.outputs.keySet()) {
            if (canVoid.test(cap) || !cap.doMatchInRecipe() || capsToSkip.contains(cap)) continue;
            int possible;
            if (cap == ItemRecipeCapability.CAP) {
                possible = Ints.saturatedCast(IParallelLogic.getOutputItemParallel(holder, recipe,
                        recipe.getOutputContents(cap), maximum));
            } else if (cap == FluidRecipeCapability.CAP) {
                possible = Ints.saturatedCast(IParallelLogic.getOutputFluidParallel(holder, recipe,
                        recipe.getOutputContents(cap), maximum));
            } else {
                possible = cap.limitMaxParallelByOutput(holder, recipe, maximum, false);
            }
            maximum = Math.min(maximum, possible);
            if (maximum == 0) return gtlcore$outputFailure(holder, recipe, cap);
        }
        for (var cap : recipe.tickOutputs.keySet()) {
            if (canVoid.test(cap) || !cap.doMatchInRecipe() || capsToSkip.contains(cap)) continue;
            maximum = Math.min(maximum, cap.limitMaxParallelByOutput(holder, recipe, maximum, true));
            if (maximum == 0) return gtlcore$outputFailure(holder, recipe, cap);
        }
        return maximum;
    }

    @Unique
    private static int gtlcore$outputFailure(IRecipeCapabilityHolder holder, GTRecipe recipe, RecipeCapability<?> cap) {
        RecipeLogic.putFailureReason(holder, recipe, Component.translatable("gtceu.recipe_logic.insufficient_out")
                .append(": ").append(cap.getName()));
        return 0;
    }
}
