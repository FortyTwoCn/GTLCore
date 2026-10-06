package org.gtlcore.gtlcore.api.recipe.chance;

import org.gtlcore.gtlcore.api.recipe.IAdvancedContentModifier;
import org.gtlcore.gtlcore.api.recipe.NativeContentState;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.registry.GTRegistration;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.registries.DeferredHolder;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.jetbrains.annotations.Nullable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/** Native OR logic with exact long run counts and the original deterministic chance cache. */
public final class LongChanceLogic extends ChanceLogic {
    public static final LongChanceLogic OR = new LongChanceLogic();
    public static final DeferredHolder<ChanceLogic, LongChanceLogic> ENTRY = GTRegistration.REGISTRATE
            .simple("long_or", GTRegistries.Keys.CHANCE_LOGIC, () -> OR);
    private LongChanceLogic() {}
    public static void init() {}

    @Override public List<Content> roll(RecipeCapability<?> cap, List<Content> entries, @Nullable Object2IntMap<?> cache, int times) {
        return roll(cap, entries, cache, (long) times);
    }
    public List<Content> roll(RecipeCapability<?> cap, List<Content> entries, @Nullable Object2IntMap<?> cache, long times) {
        return roll(cap, entries, cache, times, 0, 0);
    }
    public List<Content> roll(RecipeCapability<?> cap, List<Content> entries, @Nullable Object2IntMap<?> cache,
                              long times, int recipeTier, int machineTier) {
        if (times < 0) throw new IllegalArgumentException("Chance run count cannot be negative");
        if (times == 0 || entries.isEmpty()) return List.of();
        List<Content> outputs = new ArrayList<>(entries.size() * 2);
        for (Content entry : entries) {
            int chance = Math.max(0, NativeContentState.boostedChance(entry, recipeTier, machineTier));
            modifyByChanceSafe(cache, times, cap, outputs, entry, entry.maxChance(), chance);
        }
        return List.copyOf(outputs);
    }
    public static int getCachedChance(Content entry, @Nullable Object2IntMap<?> cache) {
        return cache != null && cache.containsKey(entry.content()) ? cache.getInt(entry.content()) : GTValues.RNG.nextInt(entry.maxChance());
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void updateCachedChance(Object ingredient, @Nullable Object2IntMap<?> cache, int chance) {
        if (cache != null) ((Object2IntMap) cache).put(ingredient, chance);
    }
    public static void modifyByChanceSafe(@Nullable Object2IntMap<?> cache, long times, RecipeCapability<?> cap,
                                          List<Content> outputs, Content entry, int maxChance, int chance) {
        if (maxChance < 1) throw new IllegalArgumentException("Chance denominator must be positive");
        if (times < 0) throw new IllegalArgumentException("Chance run count cannot be negative");
        if (chance < 0) throw new IllegalArgumentException("Chance must be nonnegative");
        if (times == 0 || chance == 0) return;
        var portions = BigInteger.valueOf(times).multiply(BigInteger.valueOf(chance))
                .divideAndRemainder(BigInteger.valueOf(maxChance));
        long guaranteed = portions[0].min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
        if (guaranteed > 0) outputs.add(entry.copyChanced(cap, IAdvancedContentModifier.preciseMultiplier(guaranteed)));
        long remainder = portions[1].longValueExact();
        int cached = getCachedChance(entry, cache);
        long bonus = Math.max(0, (remainder + cached) / maxChance);
        if (bonus > 0) {
            outputs.add(entry.copyChanced(cap, IAdvancedContentModifier.preciseMultiplier(bonus)));
            remainder -= bonus * maxChance;
        }
        updateCachedChance(entry.content(), cache, Math.toIntExact(remainder / 2 + cached));
    }
    @Override public Component getTranslation() { return Component.translatable("gtceu.chance_logic.or"); }
    @Override public String toString() { return "LongChanceLogic{OR}"; }
}
