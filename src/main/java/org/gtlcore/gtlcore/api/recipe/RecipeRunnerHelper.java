package org.gtlcore.gtlcore.api.recipe;

import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;

import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;

import it.unimi.dsi.fastutil.objects.Object2IntMap;

import java.util.*;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class RecipeRunnerHelper {

    public static boolean matchRecipe(IRecipeCapabilityHolder holder, GTRecipe recipe) {
        return matchRecipeInput(holder, recipe) && matchRecipeOutput(holder, recipe);
    }

    public static boolean matchRecipeInput(IRecipeCapabilityHolder holder, GTRecipe recipe) {
        if (recipe.inputs.isEmpty()) return true;
        return handleRecipe(IO.IN, holder, recipe.inputs, Collections.emptyMap(), false, recipe, true).isSuccess();
    }

    public static boolean matchRecipeOutput(IRecipeCapabilityHolder holder, GTRecipe recipe) {
        if (recipe.outputs.isEmpty()) return true;
        if (holder instanceof IRecipeCapabilityMachine machine && machine.isRecipeOutputAlwaysMatch(recipe)) return true;
        return handleRecipe(IO.OUT, holder, recipe.outputs, Collections.emptyMap(), false, recipe, true).isSuccess();
    }

    public static boolean handleRecipeInput(IRecipeLogicMachine holder, GTRecipe recipe) {
        return handleRecipe(IO.IN, holder, recipe.inputs, holder.getRecipeLogic().getChanceCaches(), false, recipe, false).isSuccess();
    }

    public static boolean handleRecipeOutput(IRecipeLogicMachine holder, GTRecipe recipe) {
        return handleRecipe(IO.OUT, holder, recipe.outputs, holder.getRecipeLogic().getChanceCaches(), false, recipe, false).isSuccess();
    }

    public static RecipeResult handleRecipe(IO io, IRecipeCapabilityHolder holder, Map<RecipeCapability<?>, List<Content>> contents,
                                            Map<RecipeCapability<?>, Object2IntMap<?>> chanceCaches, boolean isTick, GTRecipe recipe, boolean isSimulate) {
        return handleRecipe(io, holder, contents, chanceCaches, isTick, recipe, isSimulate, RecipeCacheStrategy.FULL_CACHE);
    }

    public static RecipeResult handleRecipe(IO io, IRecipeCapabilityHolder holder, Map<RecipeCapability<?>, List<Content>> contents,
                                            Map<RecipeCapability<?>, Object2IntMap<?>> chanceCaches, boolean isTick, GTRecipe recipe, boolean isSimulate, RecipeCacheStrategy cacheStrategy) {
        RecipeResult result;
        if (usesCoreRoutes(holder, contents, isTick)) {
            result = RecipeRunner.handle(recipe, io, holder, contents, chanceCaches, isTick, isSimulate, cacheStrategy);
        } else {
            var nativeResult = new com.gregtechceu.gtceu.api.recipe.RecipeRunner(recipe, io, isTick,
                    holder, chanceCaches, isSimulate).handle(contents);
            result = nativeResult.isSuccess() ? RecipeResult.SUCCESS : RecipeResult.fail(nativeResult.reason());
        }
        if (holder instanceof IRecipeLogicMachine machine) RecipeResult.of(machine, result);
        return result;
    }

    public static boolean usesCoreRoutes(IRecipeCapabilityHolder holder, Map<RecipeCapability<?>, List<Content>> contents, boolean isTick) {
        if (!(holder instanceof IRecipeCapabilityMachine machine) || !machine.usesCoreRecipeRouting()) return false;
        // Pure EU/CWU ticks keep native bypass groups and failure capabilities (including power-fail suspension).
        // They must not replace the selected item bus in the recipe cache every tick.
        return !isTick || contents.keySet().stream().anyMatch(cap -> cap == ItemRecipeCapability.CAP || cap == FluidRecipeCapability.CAP);
    }

    // ============================================
    // Custom-Cache versions (for DUMMY_RECIPES)
    // ============================================

    public static boolean matchRecipeInputNoMEInnerCache(IRecipeCapabilityHolder holder, GTRecipe recipe) {
        if (recipe.inputs.isEmpty()) return true;
        return handleRecipe(IO.IN, holder, recipe.inputs, Collections.emptyMap(), false, recipe, true, RecipeCacheStrategy.HANDLE_PART_CACHE_ONLY).isSuccess();
    }

    public static boolean matchRecipeInputNocache(IRecipeCapabilityHolder holder, GTRecipe recipe) {
        if (recipe.inputs.isEmpty()) return true;
        return handleRecipe(IO.IN, holder, recipe.inputs, Collections.emptyMap(), false, recipe, true, RecipeCacheStrategy.NO_CACHE).isSuccess();
    }

    public static boolean handleRecipeInputNocache(IRecipeLogicMachine holder, GTRecipe recipe) {
        return handleRecipe(IO.IN, holder, recipe.inputs, holder.getRecipeLogic().getChanceCaches(), false, recipe, false, RecipeCacheStrategy.NO_CACHE).isSuccess();
    }
}
