package org.gtlcore.gtlcore.api.recipe;

import org.gtlcore.gtlcore.api.recipe.chance.LongChanceLogic;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.feature.IOverclockMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import it.unimi.dsi.fastutil.objects.*;
import java.math.BigInteger;
import java.util.*;

/** Read-only output capacity preview: existing chance caches are exact; absent entries reserve the possible bonus. */
public final class RecipeChancePreview {
    private RecipeChancePreview() {}
    public static Map<RecipeCapability<?>, List<Content>> output(GTRecipe recipe, IRecipeCapabilityHolder holder,
            Map<RecipeCapability<?>, List<Content>> contents, Map<RecipeCapability<?>, Object2IntMap<?>> caches, boolean tick) {
        var preview = new Reference2ObjectOpenHashMap<RecipeCapability<?>, List<Content>>();
        int recipeTier = IGTRecipe.of(recipe).getEuTier();
        int holderTier = holder instanceof IOverclockMachine overclock ? overclock.getOverclockTier() : recipeTier;
        long runs = IGTRecipe.of(recipe).getRealParallels();
        for (var entry : contents.entrySet()) {
            var cap = entry.getKey(); var list = new ArrayList<Content>();
            var logic = recipe.getChanceLogicForCapability(cap, IO.OUT, tick);
            var cache = caches.get(cap);
            if (cache == null && holder instanceof IRecipeLogicMachine machine) cache = machine.getRecipeLogic().getChanceCaches().get(cap);
            for (var content : entry.getValue()) {
                if (content.chance() >= content.maxChance()) { list.add(content); continue; }
                if (content.chance() == 0 || logic == ChanceLogic.NONE.value()) continue;
                long quantity;
                if (logic == ChanceLogic.OR.value() || logic instanceof LongChanceLogic) {
                    int chance = Math.max(0, NativeContentState.boostedChance(content, recipeTier, holderTier));
                    if (chance == 0) continue;
                    var portions = BigInteger.valueOf(runs).multiply(BigInteger.valueOf(chance))
                            .divideAndRemainder(BigInteger.valueOf(content.maxChance()));
                    int cached = cache != null && cache.containsKey(content.content()) ? cache.getInt(content.content()) : content.maxChance() - 1;
                    quantity = portions[0].add(BigInteger.valueOf((portions[1].longValueExact() + cached) / content.maxChance())).longValueExact();
                } else {
                    if (runs > Integer.MAX_VALUE) throw new IllegalArgumentException("Chance logic " + logic + " does not support long run counts");
                    quantity = runs;
                }
                if (quantity == 0) continue;
                long baseAmount = cap == ItemRecipeCapability.CAP ? LongIngredient.amount(ItemRecipeCapability.CAP.of(content.content())) :
                        cap == FluidRecipeCapability.CAP ? LongFluidIngredient.amount(FluidRecipeCapability.CAP.of(content.content())) : 0;
                if (baseAmount > 0) Math.multiplyExact(baseAmount, quantity);
                var scaled = content.copyChanced(cap, IAdvancedContentModifier.preciseMultiplier(quantity));
                list.add(NativeContentState.copy(scaled, new Content(scaled.content(), scaled.maxChance(), scaled.maxChance())));
            }
            if (!list.isEmpty()) preview.put(cap, list);
        }
        return preview;
    }
}
