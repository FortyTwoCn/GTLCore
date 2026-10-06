package org.gtlcore.gtlcore.api.recipe;

import org.gtlcore.gtlcore.api.machine.trait.*;
import org.gtlcore.gtlcore.api.recipe.chance.LongChanceLogic;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;

import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.machine.feature.IOverclockMachine;
import com.gregtechceu.gtceu.api.machine.feature.IVoidable;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.objects.*;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 部分代码参考自gto
 * &#064;line <a href="https://github.com/GregTech-Odyssey/GTOCore">...</a>
 */

public final class RecipeRunner {

    private static final Comparator<RecipeHandlePart> OUTPUT_HANDLER_COMPARATOR = RecipeHandlePart.COMPARATOR.reversed();

    private RecipeRunner() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    public static RecipeResult handle(GTRecipe recipe, IO io, IRecipeCapabilityHolder holder,
                                      Map<RecipeCapability<?>, List<Content>> contents,
                                      Map<RecipeCapability<?>, Object2IntMap<?>> chanceCaches,
                                      boolean isTick, boolean simulated, RecipeCacheStrategy strategy) {
        // Select an input inventory using every requirement, including NC ingredients. Chance rolls and
        // consumption happen only after selection, so a failed cached bus cannot spend part of the recipe.
        if (io == IO.IN) {
            var searchContent = fillContent(contents, recipe, holder, chanceCaches, io, isTick, true);
            if (searchContent.isEmpty()) return RecipeResult.SUCCESS;
            if (!(holder instanceof IRecipeCapabilityMachine machine) || machine.emptyHandlePart()) {
                return RecipeResult.FAIL_INPUT;
            }
            var plan = selectInputPlan(machine, recipe, searchContent, strategy);
            if (plan == null) return RecipeResult.FAIL_INPUT;
            if (simulated) return RecipeResult.SUCCESS;
            var consumedContent = fillContent(contents, recipe, holder, chanceCaches, io, isTick, false);
            return executeInputPlan(machine, recipe, plan, consumedContent) ? RecipeResult.SUCCESS : RecipeResult.FAIL_INPUT;
        }

        var recipeContent = fillContent(contents, recipe, holder, chanceCaches, io, isTick, simulated);
        if (recipeContent.isEmpty()) return RecipeResult.SUCCESS;
        if (!(holder instanceof IRecipeCapabilityMachine machine) || machine.emptyHandlePart()) {
            discardVoidedOutput(holder, recipeContent);
            return recipeContent.isEmpty() ? RecipeResult.SUCCESS : simulated ? generateOutputFailReason(recipeContent) : RecipeResult.FAIL_OUTPUT;
        }
        recipeContent = handleMEOutput(machine.getMEOutputRecipeHandleParts(), recipeContent, simulated);
        if (!recipeContent.isEmpty()) {
            recipeContent = handleNormalOutput(machine.getNormalRecipeHandlePart(IO.OUT), recipe, recipeContent, isTick, simulated);
        }
        discardVoidedOutput(holder, recipeContent);
        return recipeContent.isEmpty() ? RecipeResult.SUCCESS : simulated ? generateOutputFailReason(recipeContent) : RecipeResult.FAIL_OUTPUT;
    }

    private static Reference2ObjectOpenHashMap<RecipeCapability<?>, List<Object>> fillContent(
                                                                                              Map<RecipeCapability<?>, List<Content>> entries,
                                                                                              GTRecipe recipe,
                                                                                              IRecipeCapabilityHolder holder,
                                                                                              Map<RecipeCapability<?>, Object2IntMap<?>> chanceCaches,
                                                                                              IO io, boolean isTick, boolean simulated) {
        var recipeContent = new Reference2ObjectOpenHashMap<RecipeCapability<?>, List<Object>>();
        if (simulated && io == IO.OUT) entries = RecipeChancePreview.output(recipe, holder, entries, chanceCaches, isTick);

        for (var entry : entries.entrySet()) {
            RecipeCapability<?> cap = entry.getKey();
            if (!cap.doMatchInRecipe()) continue;
            if (simulated && io == IO.OUT && canVoid(holder, cap)) continue;

            List<Content> contents = entry.getValue();
            if (contents.isEmpty()) continue;

            List<Content> chancedContents = null;
            List<Object> contentList = null;
            for (Content cont : contents) {
                if (simulated) {
                    if (contentList == null) contentList = new ObjectArrayList<>();
                    contentList.add(cont.content());
                } else {
                    if (cont.chance() >= cont.maxChance()) {
                        if (contentList == null) contentList = new ObjectArrayList<>();
                        contentList.add(cont.content());
                    } else if (cont.chance() != 0) {
                        if (chancedContents == null) chancedContents = new ObjectArrayList<>();
                        chancedContents.add(cont);
                    }
                }
            }
            if (chancedContents != null && !chancedContents.isEmpty()) {
                int recipeTier = IGTRecipe.of(recipe).getEuTier();
                int holderTier = holder instanceof IOverclockMachine overclock ? overclock.getOverclockTier() : recipeTier;
                var cache = chanceCaches.get(cap);
                var logic = recipe.getChanceLogicForCapability(cap, io, isTick);
                long runs = IGTRecipe.of(recipe).getRealParallels();
                if (logic == ChanceLogic.OR.value() || logic instanceof LongChanceLogic) {
                    chancedContents = LongChanceLogic.OR.roll(cap, chancedContents, cache, runs, recipeTier, holderTier);
                } else if (logic == ChanceLogic.NONE.value()) {
                    chancedContents = List.of();
                } else {
                    if (runs > Integer.MAX_VALUE) throw new IllegalArgumentException("Chance logic " + logic + " does not support long run counts");
                    chancedContents = logic.roll(cap, chancedContents, cache, Math.toIntExact(runs));
                }
                if (chancedContents != null) {
                    if (contentList == null) contentList = new ObjectArrayList<>();
                    for (Content cont : chancedContents) {
                        contentList.add(cont.content());
                    }
                }
            }
            if (contentList != null && !contentList.isEmpty()) recipeContent.put(cap, contentList);
        }

        return recipeContent;
    }

    private record InputPlan(IRecipeHandlePart part, int slot) {}

    @Nullable
    private static InputPlan selectInputPlan(IRecipeCapabilityMachine machine, GTRecipe recipe,
                                             Reference2ObjectOpenHashMap<RecipeCapability<?>, List<Object>> search,
                                             RecipeCacheStrategy strategy) {
        // Temporary recipes retain their normal-before-ME order and do not modify either cache.
        if (strategy == RecipeCacheStrategy.NO_CACHE) {
            for (var part : machine.getNormalRecipeHandlePart(IO.IN)) {
                var plan = tryNormalInput(machine, recipe, search, part, strategy);
                if (plan != null) return plan;
            }
            var shared = machine.getSharedRecipeHandlePart();
            if (!machine.isDistinct() && shared != null) {
                var plan = tryNormalInput(machine, recipe, search, shared, strategy);
                if (plan != null) return plan;
            }
            for (var part : machine.getMEPatternRecipeHandleParts()) {
                var plan = tryMEInput(machine, recipe, search, part, strategy);
                if (plan != null) return plan;
            }
            return null;
        }

        for (var iterator = machine.getAllCachedRecipeHandlesIter(recipe); iterator.hasNext();) {
            var part = iterator.next();
            InputPlan plan = part instanceof MEPatternRecipeHandlePart me ?
                    tryMEInput(machine, recipe, search, me, strategy) :
                    part instanceof RecipeHandlePart normal ? tryNormalInput(machine, recipe, search, normal, strategy) : null;
            if (plan != null) return plan;
        }
        var cached = machine.getAllCachedRecipeHandles(recipe);
        for (var part : machine.getMEPatternRecipeHandleParts()) {
            if (cached.contains(part)) continue;
            var plan = tryMEInput(machine, recipe, search, part, strategy);
            if (plan != null) return plan;
        }
        for (var part : machine.getNormalRecipeHandlePart(IO.IN)) {
            if (cached.contains(part)) continue;
            var plan = tryNormalInput(machine, recipe, search, part, strategy);
            if (plan != null) return plan;
        }
        var shared = machine.getSharedRecipeHandlePart();
        if (!machine.isDistinct() && shared != null && !cached.contains(shared)) {
            return tryNormalInput(machine, recipe, search, shared, strategy);
        }
        return null;
    }

    @Nullable
    private static InputPlan tryMEInput(IRecipeCapabilityMachine machine, GTRecipe recipe,
                                        Reference2ObjectOpenHashMap<RecipeCapability<?>, List<Object>> search,
                                        MEPatternRecipeHandlePart part, RecipeCacheStrategy strategy) {
        // Pattern inventories implement item/fluid IO; other native capabilities belong to normal parts.
        for (var cap : search.keySet()) {
            if (cap != ItemRecipeCapability.CAP && cap != FluidRecipeCapability.CAP) return null;
        }
        int slot = part.handleRecipe(recipe, search, true, strategy.cacheToMEInternal);
        if (slot < 0) return null;
        if (strategy.cacheToHandlePartMap) machine.tryAddAndActiveMERhp(part, recipe, slot);
        return new InputPlan(part, slot);
    }

    @Nullable
    private static InputPlan tryNormalInput(IRecipeCapabilityMachine machine, GTRecipe recipe,
                                            Reference2ObjectOpenHashMap<RecipeCapability<?>, List<Object>> search,
                                            RecipeHandlePart part, RecipeCacheStrategy strategy) {
        if (machine.isDistinct()) {
            var remaining = part.handleRecipe(IO.IN, recipe, search, true);
            var shared = machine.getSharedRecipeHandlePart();
            if (!remaining.isEmpty() && shared != null && shared != part) {
                remaining = shared.handleRecipe(IO.IN, recipe, remaining, true);
            }
            if (!remaining.isEmpty()) return null;
        } else {
            var sharedContent = sharedInput(search);
            var shared = machine.getSharedRecipeHandlePart();
            if (!sharedContent.isEmpty() && (shared == null || !shared.handleRecipe(IO.IN, recipe, sharedContent, true).isEmpty())) {
                return null;
            }
            var items = search.get(ItemRecipeCapability.CAP);
            if (items != null && !items.isEmpty()) {
                var remaining = part.handleRecipe(IO.IN, recipe, ItemRecipeCapability.CAP, items, true);
                if (remaining != null && !remaining.isEmpty()) return null;
            }
        }
        if (strategy.cacheToHandlePartMap) machine.tryAddAndActiveRhp(recipe, part);
        return new InputPlan(part, -1);
    }

    private static Reference2ObjectOpenHashMap<RecipeCapability<?>, List<Object>> sharedInput(
            Reference2ObjectOpenHashMap<RecipeCapability<?>, List<Object>> contents) {
        var shared = new Reference2ObjectOpenHashMap<>(contents);
        shared.remove(ItemRecipeCapability.CAP);
        return shared;
    }

    private static boolean executeInputPlan(IRecipeCapabilityMachine machine, GTRecipe recipe, InputPlan plan,
                                            Reference2ObjectOpenHashMap<RecipeCapability<?>, List<Object>> contents) {
        if (contents.isEmpty()) return true;
        if (plan.part instanceof MEPatternRecipeHandlePart me) return me.handleRecipeInSlot(recipe, plan.slot, contents);
        var normal = (RecipeHandlePart) plan.part;
        if (machine.isDistinct()) {
            var remaining = normal.handleRecipe(IO.IN, recipe, contents, false);
            var shared = machine.getSharedRecipeHandlePart();
            if (!remaining.isEmpty() && shared != null && shared != normal) remaining = shared.handleRecipe(IO.IN, recipe, remaining, false);
            return remaining.isEmpty();
        }
        var sharedContent = sharedInput(contents);
        if (!sharedContent.isEmpty() && !machine.getSharedRecipeHandlePart().handleRecipe(IO.IN, recipe, sharedContent, false).isEmpty()) {
            return false;
        }
        var items = contents.get(ItemRecipeCapability.CAP);
        if (items == null || items.isEmpty()) return true;
        var remaining = normal.handleRecipe(IO.IN, recipe, ItemRecipeCapability.CAP, items, false);
        return remaining == null || remaining.isEmpty();
    }

    // ========================================
    // Output
    // ========================================

    private static Reference2ObjectOpenHashMap<RecipeCapability<?>, List<Object>> handleNormalOutput(
                                                                                                     List<RecipeHandlePart> handlers, GTRecipe recipe,
                                                                                                     Reference2ObjectOpenHashMap<RecipeCapability<?>, List<Object>> recipeContent,
                                                                                                     boolean isTick, boolean simulated) {
        if (handlers.isEmpty()) return recipeContent;

        // Sort only if not already sorted (assuming handlers list is stable)
        // Consider caching sorted handlers in machine if this becomes a bottleneck
        if (!isTick && handlers.size() > 1 && !isSortedByPriority(handlers)) {
            handlers.sort(OUTPUT_HANDLER_COMPARATOR);
        }

        for (var handler : handlers) {
            recipeContent = handler.handleRecipe(IO.OUT, recipe, recipeContent, simulated);
            if (recipeContent.isEmpty()) break;
        }

        return recipeContent;
    }

    private static boolean isSortedByPriority(List<RecipeHandlePart> handlers) {
        RecipeHandlePart previous = handlers.get(0);
        for (int i = 1; i < handlers.size(); i++) {
            RecipeHandlePart current = handlers.get(i);
            if (OUTPUT_HANDLER_COMPARATOR.compare(previous, current) > 0) return false;
            previous = current;
        }
        return true;
    }

    private static Reference2ObjectOpenHashMap<RecipeCapability<?>, List<Object>> handleMEOutput(
                                                                                                 List<MEIORecipeHandlePart<?>> meHandlers,
                                                                                                 Reference2ObjectOpenHashMap<RecipeCapability<?>, List<Object>> recipeContent,
                                                                                                 boolean simulated) {
        for (MEIORecipeHandlePart<?> meHandler : meHandlers) {
            recipeContent = meHandler.meHandleOutput(recipeContent, simulated);
            if (recipeContent.isEmpty()) break;
        }
        return recipeContent;
    }

    private static boolean canVoid(IRecipeCapabilityHolder holder, RecipeCapability<?> cap) {
        return holder instanceof IVoidable voidable && voidable.canVoidRecipeOutputs(cap);
    }

    private static void discardVoidedOutput(IRecipeCapabilityHolder holder, Map<RecipeCapability<?>, List<Object>> contents) {
        contents.keySet().removeIf(cap -> canVoid(holder, cap));
    }

    private static RecipeResult generateOutputFailReason(Reference2ObjectOpenHashMap<RecipeCapability<?>, List<Object>> recipeContent) {
        var builder = new StringBuilder();
        for (var entry : Reference2ObjectMaps.fastIterable(recipeContent)) {
            var cap = entry.getKey();
            for (var ing : entry.getValue()) {
                if (cap == ItemRecipeCapability.CAP) {
                    if (ing instanceof SizedIngredient si && si.getItems().length >= 1) {
                        builder.append(si.getItems()[0].getHoverName().getString()).append("x ").append(LongIngredient.amount(si)).append(" ");
                    }
                } else if (cap == FluidRecipeCapability.CAP) {
                    if (ing instanceof SizedFluidIngredient fi && fi.getFluids().length >= 1) {
                        builder.append(fi.getFluids()[0].getHoverName().getString()).append("x ").append(LongFluidIngredient.amount(fi)).append(" ");
                    }
                }
            }
        }
        return RecipeResult.fail(Component.translatable("gtceu.recipe.fail.Output.Content", builder.toString()));
    }

}
