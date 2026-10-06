package org.gtlcore.gtlcore.api.recipe;

import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.ExtraCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.Map;

/** Core metadata absent from the native Content record, using native recipe serialization and copying. */
public interface NativeContentState {
    String KEY = "gtlcore:content_state";
    IO gtlcore$getTooltipIO();
    void gtlcore$setTooltipIO(IO io);
    int gtlcore$getTierChanceBoost();
    void gtlcore$setTierChanceBoost(int boost);
    @Nullable String gtlcore$getSlotName();
    void gtlcore$setSlotName(@Nullable String name);
    @Nullable String gtlcore$getUiName();
    void gtlcore$setUiName(@Nullable String name);

    static NativeContentState of(Content content) { return (NativeContentState) (Object) content; }
    static Content create(Object ingredient, int chance, int maxChance, int boost, String slot, String ui) {
        Content result = new Content(ingredient, chance, maxChance);
        of(result).gtlcore$setTierChanceBoost(boost);
        of(result).gtlcore$setSlotName(slot);
        of(result).gtlcore$setUiName(ui);
        return result;
    }
    static Content copy(Content source, Content target) {
        var from = of(source); var to = of(target);
        to.gtlcore$setTierChanceBoost(from.gtlcore$getTierChanceBoost());
        to.gtlcore$setSlotName(from.gtlcore$getSlotName());
        to.gtlcore$setUiName(from.gtlcore$getUiName());
        return target;
    }
    static int boostedChance(Content content, int recipeTier, int machineTier) {
        long tiers = Math.max(0L, (long) machineTier - recipeTier - (recipeTier == 0 ? 1L : 0L));
        long chance = content.chance() + (long) of(content).gtlcore$getTierChanceBoost() * tiers;
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, chance));
    }
    static <T> Codec<Content> codec(RecipeCapability<T> capability) {
        return RecordCodecBuilder.create(instance -> instance.group(
                capability.serializer.codec().fieldOf("content").forGetter(value -> capability.of(value.content())),
                ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("chance", ChanceLogic.getMaxChancedValue()).forGetter(Content::chance),
                ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("maxChance", ChanceLogic.getMaxChancedValue()).forGetter(Content::maxChance),
                Codec.INT.optionalFieldOf("tierChanceBoost", 0).forGetter(value -> of(value).gtlcore$getTierChanceBoost()),
                Codec.STRING.optionalFieldOf("slotName", "").forGetter(value -> nonNull(of(value).gtlcore$getSlotName())),
                Codec.STRING.optionalFieldOf("uiName", "").forGetter(value -> nonNull(of(value).gtlcore$getUiName())))
                .apply(instance, NativeContentState::create));
    }
    private static String nonNull(String value) { return value == null ? "" : value; }

    /** Native stream contents carry three fields; store extra metadata in the existing recipe data payload. */
    static void snapshot(GTRecipe recipe) {
        CompoundTag state = new CompoundTag();
        snapshot(state, "in", recipe.inputs); snapshot(state, "out", recipe.outputs);
        snapshot(state, "tick_in", recipe.tickInputs); snapshot(state, "tick_out", recipe.tickOutputs);
        if (state.isEmpty()) recipe.data.remove(KEY); else recipe.data.put(KEY, state);
    }
    private static void snapshot(CompoundTag state, String io, Map<RecipeCapability<?>, List<Content>> contents) {
        CompoundTag capabilities = new CompoundTag();
        contents.forEach((cap, list) -> {
            ListTag metadata = new ListTag();
            for (int index = 0; index < list.size(); index++) {
                var extension = of(list.get(index));
                if (extension.gtlcore$getTierChanceBoost() == 0 && extension.gtlcore$getSlotName() == null && extension.gtlcore$getUiName() == null) continue;
                CompoundTag entry = new CompoundTag(); entry.putInt("index", index);
                entry.putInt("boost", extension.gtlcore$getTierChanceBoost());
                if (extension.gtlcore$getSlotName() != null) entry.putString("slot", extension.gtlcore$getSlotName());
                if (extension.gtlcore$getUiName() != null) entry.putString("ui", extension.gtlcore$getUiName());
                metadata.add(entry);
            }
            if (!metadata.isEmpty()) capabilities.put(GTRegistries.RECIPE_CAPABILITIES.getKey(cap).toString(), metadata);
        });
        if (!capabilities.isEmpty()) state.put(io, capabilities);
    }
    static void restore(GTRecipe recipe) {
        if (!recipe.data.contains(KEY, Tag.TAG_COMPOUND)) return;
        CompoundTag state = recipe.data.getCompound(KEY);
        restore(state, "in", recipe.inputs); restore(state, "out", recipe.outputs);
        restore(state, "tick_in", recipe.tickInputs); restore(state, "tick_out", recipe.tickOutputs);
    }
    private static void restore(CompoundTag state, String io, Map<RecipeCapability<?>, List<Content>> contents) {
        CompoundTag capabilities = state.getCompound(io);
        contents.forEach((cap, list) -> {
            ListTag metadata = capabilities.getList(GTRegistries.RECIPE_CAPABILITIES.getKey(cap).toString(), Tag.TAG_COMPOUND);
            for (int index = 0; index < metadata.size(); index++) {
                CompoundTag entry = metadata.getCompound(index);
                int position = entry.getInt("index");
                if (position < 0 || position >= list.size()) throw new IllegalArgumentException("Recipe content metadata index out of range: " + position);
                var extension = of(list.get(position));
                extension.gtlcore$setTierChanceBoost(entry.getInt("boost"));
                extension.gtlcore$setSlotName(entry.contains("slot", Tag.TAG_STRING) ? entry.getString("slot") : null);
                extension.gtlcore$setUiName(entry.contains("ui", Tag.TAG_STRING) ? entry.getString("ui") : null);
            }
        });
    }
}
