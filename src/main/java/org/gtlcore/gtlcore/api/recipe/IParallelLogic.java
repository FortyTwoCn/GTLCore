package org.gtlcore.gtlcore.api.recipe;

import org.gtlcore.gtlcore.api.machine.multiblock.ParallelMachine;
import org.gtlcore.gtlcore.api.machine.trait.*;
import org.gtlcore.gtlcore.api.recipe.chance.LongChanceLogic;
import org.gtlcore.gtlcore.api.recipe.ingredient.*;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.feature.IOverclockMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.IVoidable;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.utils.ItemStackHashStrategy;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import it.unimi.dsi.fastutil.objects.*;
import java.util.*;

/** Exact native quantities, bounded by the same selected Core route used to execute the recipe. */
public interface IParallelLogic {
    static GTRecipe getRecipeOutputChance(IRecipeCapabilityHolder holder, GTRecipe recipe) {
        var copy = recipe.copy();
        copy.outputs.clear();
        for (var entry : recipe.outputs.entrySet()) {
            var cap = entry.getKey();
            var outputs = new ArrayList<Content>();
            var chanced = new ArrayList<Content>();
            for (var content : entry.getValue()) {
                if (content.chance() >= content.maxChance()) outputs.add(content.copy(cap));
                else if (content.chance() > 0) chanced.add(content);
            }
            if (!chanced.isEmpty()) {
                var logic = recipe.getChanceLogicForCapability(cap, IO.OUT, false);
                var cache = holder instanceof IRecipeLogicMachine machine ? machine.getRecipeLogic().getChanceCaches().get(cap) : null;
                long runs = IGTRecipe.of(recipe).getRealParallels();
                List<Content> rolled;
                if (logic == ChanceLogic.OR.value() || logic instanceof LongChanceLogic) {
                    int recipeTier = IGTRecipe.of(recipe).getEuTier();
                    int holderTier = holder instanceof IOverclockMachine overclock ? overclock.getOverclockTier() : recipeTier;
                    rolled = LongChanceLogic.OR.roll(cap, chanced, cache, runs, recipeTier, holderTier);
                } else if (logic == ChanceLogic.NONE.value()) rolled = List.of();
                else {
                    if (runs > Integer.MAX_VALUE) throw new IllegalArgumentException("Chance logic " + logic + " does not support long run counts");
                    rolled = logic.roll(cap, chanced, cache, Math.toIntExact(runs));
                }
                if (rolled != null) for (var content : rolled) {
                    var guaranteed = NativeContentState.copy(content, new Content(cap.copyContent(content.content()), content.maxChance(), content.maxChance()));
                    NativeContentState.of(guaranteed).gtlcore$setTierChanceBoost(0);
                    outputs.add(guaranteed);
                }
            }
            if (!outputs.isEmpty()) copy.outputs.put(cap, outputs);
        }
        return copy;
    }

    static long getParallel(IRecipeCapabilityHolder holder, GTRecipe recipe, long limit) {
        return limit <= 0 ? 0 : getMinParallel(holder, recipe, getMaxParallel(holder, recipe, limit));
    }

    static long getMaxParallel(IRecipeCapabilityHolder holder, GTRecipe recipe, long limit) {
        if (limit <= 0) return 0;
        limit = getInputItemParallel(holder, recipe, limit);
        return limit == 0 ? 0 : getInputFluidParallel(holder, recipe, limit);
    }

    static long getMinParallel(IRecipeCapabilityHolder holder, GTRecipe recipe, long limit) {
        if (limit <= 0) return 0;
        for (var entry : recipe.outputs.entrySet()) {
            if (entry.getKey() == ItemRecipeCapability.CAP) limit = getOutputItemParallel(holder, recipe, entry.getValue(), limit);
            else if (entry.getKey() == FluidRecipeCapability.CAP) limit = getOutputFluidParallel(holder, recipe, entry.getValue(), limit);
            if (limit == 0) break;
        }
        return limit;
    }

    static long getInputItemParallel(IRecipeCapabilityHolder holder, GTRecipe recipe, long limit) {
        return getInputItemParallel(holder, recipe, limit, recipe.inputs);
    }

    static long getInputItemParallel(IRecipeCapabilityHolder holder, GTRecipe recipe, long limit,
                                     Map<RecipeCapability<?>, List<Content>> routeInputs) {
        var contents = recipe.getInputContents(ItemRecipeCapability.CAP);
        if (contents.isEmpty() || limit <= 0) return Math.max(0, limit);
        if (!(holder instanceof IRecipeCapabilityMachine machine) || !machine.usesCoreRecipeRouting()) {
            return LongItemParallelLogic.getMaxParallelByInput(holder, recipe, limit, false);
        }
        if (!selectRoute(holder, machine, recipe, routeInputs)) return 0;
        boolean confirm = holder instanceof ParallelMachine parallel && parallel.needConfirmMEStock();
        Object2LongMap<ItemStack> inventory = new Object2LongOpenCustomHashMap<>(ItemStackHashStrategy.comparingAllButCount());
        var active = machine.getActiveRecipeHandle(recipe);
        if (active instanceof MEPatternRecipeHandlePart pattern) merge(inventory, pattern.getMEContent(ItemRecipeCapability.CAP, recipe));
        else if (active instanceof RecipeHandlePart normal) {
            merge(inventory, normal.getSelfContent(ItemRecipeCapability.CAP, confirm));
            // Controller traits bypass the controller's global distinct mode, never another bus.
            var shared = machine.getSharedRecipeHandlePart();
            if (machine.isDistinct() && shared != null && shared != normal) merge(inventory, shared.getSelfContent(ItemRecipeCapability.CAP, confirm));
        }
        return LongItemParallelLogic.getMaxParallelByInput(inventory, contents, limit);
    }

    static long getInputFluidParallel(IRecipeCapabilityHolder holder, GTRecipe recipe, long limit) {
        return getInputFluidParallel(holder, recipe, limit, recipe.inputs);
    }

    static long getInputFluidParallel(IRecipeCapabilityHolder holder, GTRecipe recipe, long limit,
                                      Map<RecipeCapability<?>, List<Content>> routeInputs) {
        var contents = recipe.getInputContents(FluidRecipeCapability.CAP);
        if (contents.isEmpty() || limit <= 0) return Math.max(0, limit);
        if (!(holder instanceof IRecipeCapabilityMachine machine) || !machine.usesCoreRecipeRouting()) {
            return LongFluidParallelLogic.getMaxParallelByInput(holder, recipe, limit, false);
        }
        if (!selectRoute(holder, machine, recipe, routeInputs)) return 0;
        boolean confirm = holder instanceof ParallelMachine parallel && parallel.needConfirmMEStock();
        var inventory = LongFluidParallelLogic.newFluidAmountMap();
        var active = machine.getActiveRecipeHandle(recipe);
        if (active instanceof MEPatternRecipeHandlePart pattern) merge(inventory, pattern.getMEContent(FluidRecipeCapability.CAP, recipe));
        else if (active instanceof RecipeHandlePart normal) {
            merge(inventory, machine.isDistinct() ? normal.getSelfContent(FluidRecipeCapability.CAP, confirm) :
                    normal.getContentWithShared(FluidRecipeCapability.CAP, confirm));
            var shared = machine.getSharedRecipeHandlePart();
            if (machine.isDistinct() && shared != null && shared != normal) merge(inventory, shared.getSelfContent(FluidRecipeCapability.CAP, confirm));
        }
        return LongFluidParallelLogic.getMaxParallelByInput(inventory, contents, limit);
    }

    private static boolean selectRoute(IRecipeCapabilityHolder holder, IRecipeCapabilityMachine machine, GTRecipe recipe,
                                       Map<RecipeCapability<?>, List<Content>> routeInputs) {
        // Recheck cached requirements: a bus/ME slot may have changed since the last completed cycle.
        return RecipeRunnerHelper.handleRecipe(IO.IN, holder, routeInputs, Map.of(), false, recipe, true).isSuccess() &&
                machine.getActiveRecipeHandle(recipe) != null;
    }

    private static <T> void merge(Object2LongMap<T> target, Object2LongMap<T> source) {
        for (var entry : source.object2LongEntrySet()) {
            long first = target.getLong(entry.getKey()), second = entry.getLongValue();
            target.put(entry.getKey(), first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second);
        }
    }

    static long getOutputItemParallel(IRecipeCapabilityHolder holder, GTRecipe recipe, List<Content> contents, long limit) {
        return limitOutput(holder, recipe, ItemRecipeCapability.CAP, contents, limit);
    }

    static long getOutputFluidParallel(IRecipeCapabilityHolder holder, GTRecipe recipe, List<Content> contents, long limit) {
        return limitOutput(holder, recipe, FluidRecipeCapability.CAP, contents, limit);
    }

    private static long limitOutput(IRecipeCapabilityHolder holder, GTRecipe recipe, RecipeCapability<?> cap, List<Content> contents, long limit) {
        if (limit <= 0 || contents.isEmpty()) return Math.max(0, limit);
        long runs = Math.max(1, IGTRecipe.of(recipe).getRealParallels());
        limit = Math.min(limit, Long.MAX_VALUE / runs);
        for (var content : contents) {
            if (content.chance() < content.maxChance()) continue; // Chance quantities are bounded after the read-only preview.
            long amount = cap == ItemRecipeCapability.CAP ? LongIngredient.amount(ItemRecipeCapability.CAP.of(content.content())) :
                    LongFluidIngredient.amount(FluidRecipeCapability.CAP.of(content.content()));
            if (amount > 0) limit = Math.min(limit, Long.MAX_VALUE / amount);
        }
        boolean voidable = holder instanceof IVoidable machine && machine.canVoidRecipeOutputs(cap);
        long low = 0, high = limit;
        while (low < high) {
            long middle = low + (high - low) / 2 + (high - low) % 2;
            var candidate = recipe.copy();
            IGTRecipe.of(candidate).setRealParallels(Math.multiplyExact(runs, middle));
            var modifier = IAdvancedContentModifier.preciseMultiplier(middle);
            var scaled = new ArrayList<Content>(contents.size());
            for (var content : contents) scaled.add(content.copy(cap, modifier));
            boolean matches;
            try {
                // Guaranteed quantities scale in Content.copy; chance quantities scale by the complete run count.
                // Previewing reads the cache and never rolls it, including repeated binary-search probes.
                var preview = RecipeChancePreview.output(candidate, holder, Map.of(cap, scaled), Map.of(), false);
                if (voidable) matches = true;
                else if (holder instanceof IRecipeCapabilityMachine machine && machine.usesCoreRecipeRouting()) {
                    matches = RecipeRunner.handle(candidate, IO.OUT, holder, preview, Map.of(), false, true, RecipeCacheStrategy.NO_CACHE).isSuccess();
                } else matches = new com.gregtechceu.gtceu.api.recipe.RecipeRunner(candidate, IO.OUT, false, holder, Map.of(), true).handle(preview).isSuccess();
            } catch (ArithmeticException overflow) {
                matches = false;
            }
            if (matches) low = middle;
            else high = middle - 1;
        }
        return low;
    }
}
