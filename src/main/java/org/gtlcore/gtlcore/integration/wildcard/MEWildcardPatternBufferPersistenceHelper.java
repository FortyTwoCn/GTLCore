package org.gtlcore.gtlcore.integration.wildcard;

import org.gtlcore.gtlcore.api.item.tool.ae2.patternTool.RecipeStackHelper;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import org.gtlcore.gtlcore.integration.ae2.AEUtils;
import org.gtlcore.gtlcore.utils.Registries;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.*;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import java.util.*;
import java.util.function.*;
import java.util.function.IntPredicate;

/** Native component-key persistence; expanded slot order and duplicate outputs cannot merge stored inventories. */
final class MEWildcardPatternBufferPersistenceHelper {
    private static final String DYNAMIC_SLOTS_KEY = "wildcardDynamicSlots";
    private static final String RECIPE_ID_KEY = "recipeId";
    private static final String PATTERN_INPUTS_KEY = "patternInputs";
    private static final String PATTERN_OUTPUTS_KEY = "patternOutputs";
    private static final String SLOT_DATA_KEY = "slot";
    private static final String RECIPE_INPUTS_KEY = "recipeInputs";
    private static final String RECIPE_OUTPUTS_KEY = "recipeOutputs";
    private final Map<PatternSignature, List<PendingSlotData>> pending = new LinkedHashMap<>();
    private final Map<PatternSignature, List<Integer>> signatureSlots = new HashMap<>();
    private final Int2ObjectMap<PatternSignature> slotSignatures = new Int2ObjectOpenHashMap<>();

    void clearPatternIndex() { signatureSlots.clear(); slotSignatures.clear(); }
    void clearPendingRestoreData() { pending.clear(); }
    List<CompoundTag> pendingInventories() {
        return pending.values().stream().flatMap(List::stream).map(PendingSlotData::data)
                .filter(Objects::nonNull).map(CompoundTag::copy).toList();
    }
    void indexPattern(IPatternDetails pattern, int slot) {
        var signature = PatternSignature.of(pattern);
        signatureSlots.computeIfAbsent(signature, ignored -> new ArrayList<>()).add(slot);
        slotSignatures.put(slot, signature);
    }

    void save(CompoundTag tag, HolderLookup.Provider registries, int slotCount, IntPredicate isSlotActive,
              IntFunction<CompoundTag> serializeSlot, IntPredicate hasRecipeCache, Int2ReferenceMap<GTRecipe> recipes) {
        var entries = new ListTag();
        for (int slot = 0; slot < slotCount; slot++) {
            boolean hasData = isSlotActive.test(slot), hasRecipe = hasRecipeCache.test(slot);
            if (!hasData && !hasRecipe) continue;
            var signature = slotSignatures.get(slot); if (signature == null) continue;
            var data = hasData ? serializeSlot.apply(slot) : null;
            var recipe = hasRecipe ? recipes.get(slot) : null;
            if ((data == null || data.isEmpty()) && recipe == null) continue;
            var entry = new CompoundTag();
            entry.put(PATTERN_INPUTS_KEY, AEUtils.createListTag(key -> key.toTagGeneric(registries), signature.inputs()));
            entry.put(PATTERN_OUTPUTS_KEY, AEUtils.createListTag(key -> key.toTagGeneric(registries), signature.outputs()));
            if (data != null && !data.isEmpty()) entry.put(SLOT_DATA_KEY, data.copy());
            if (recipe != null) {
                entry.putString(RECIPE_ID_KEY, recipe.id.toString());
                entry.put(RECIPE_INPUTS_KEY, AEUtils.createListTag(key -> key.toTagGeneric(registries), toRecipeInputMap(recipe)));
                entry.put(RECIPE_OUTPUTS_KEY, AEUtils.createListTag(key -> key.toTagGeneric(registries), toRecipeOutputMap(recipe)));
            }
            entries.add(entry);
        }
        if (entries.isEmpty()) tag.remove(DYNAMIC_SLOTS_KEY); else tag.put(DYNAMIC_SLOTS_KEY, entries);
    }

    void load(CompoundTag tag, HolderLookup.Provider registries) {
        pending.clear();
        for (var value : tag.getList(DYNAMIC_SLOTS_KEY, Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) value;
            var inputs = readStackMap(entry, PATTERN_INPUTS_KEY, registries);
            var outputs = readStackMap(entry, PATTERN_OUTPUTS_KEY, registries);
            if (outputs.isEmpty()) continue;
            var data = entry.contains(SLOT_DATA_KEY, Tag.TAG_COMPOUND) ? entry.getCompound(SLOT_DATA_KEY).copy() : null;
            var recipeId = entry.contains(RECIPE_ID_KEY, Tag.TAG_STRING) ? ResourceLocation.tryParse(entry.getString(RECIPE_ID_KEY)) : null;
            var recipeSignature = recipeId != null && entry.contains(RECIPE_INPUTS_KEY, Tag.TAG_LIST) && entry.contains(RECIPE_OUTPUTS_KEY, Tag.TAG_LIST) ?
                    new RecipeSignature(readStackMap(entry, RECIPE_INPUTS_KEY, registries), readStackMap(entry, RECIPE_OUTPUTS_KEY, registries)) : null;
            if (data == null && recipeId == null) continue;
            pending.computeIfAbsent(new PatternSignature(inputs, outputs), ignored -> new ArrayList<>()).add(new PendingSlotData(data, recipeId, recipeSignature));
        }
    }

    void restore(Int2ReferenceMap<GTRecipe> recipes, BiConsumer<Integer, CompoundTag> deserializeSlot, Consumer<List<CompoundTag>> refundSlots) {
        restore(recipes, deserializeSlot, refundSlots, id -> {
            var holder = Registries.getRecipeManager().byKey(id).orElse(null);
            return holder != null && holder.value() instanceof GTRecipe recipe ? recipe : null;
        });
    }
    void restore(Int2ReferenceMap<GTRecipe> recipes, BiConsumer<Integer, CompoundTag> deserializeSlot,
                 Consumer<List<CompoundTag>> refundSlots, Function<ResourceLocation, GTRecipe> resolver) {
        if (pending.isEmpty()) return;
        var refunds = new ArrayList<CompoundTag>();
        for (var entry : pending.entrySet()) {
            var slots = signatureSlots.getOrDefault(entry.getKey(), List.of());
            int index = 0;
            for (var saved : entry.getValue()) {
                if (index >= slots.size()) { if (saved.data != null) refunds.add(saved.data); continue; }
                int slot = slots.get(index++);
                if (saved.data != null) deserializeSlot.accept(slot, saved.data.copy());
                if (saved.recipeId == null) continue;
                var recipe = resolver.apply(saved.recipeId);
                if (recipe != null && saved.recipeSignature != null && saved.recipeSignature.matches(recipe)) recipes.put(slot, recipe);
            }
        }
        if (!refunds.isEmpty()) refundSlots.accept(List.copyOf(refunds));
        pending.clear();
    }

    private static Object2LongMap<AEKey> readStackMap(CompoundTag tag, String key, HolderLookup.Provider registries) {
        var map = new Object2LongOpenHashMap<AEKey>();
        AEUtils.loadInventory(tag.getList(key, Tag.TAG_COMPOUND), value -> AEKey.fromTagGeneric(registries, value), map);
        return map;
    }
    private static void add(Object2LongMap<AEKey> map, AEKey key, long amount) {
        if (key == null || amount <= 0) throw new IllegalArgumentException("Invalid wildcard signature quantity");
        map.put(key, Math.addExact(map.getLong(key), amount));
    }
    private static Object2LongMap<AEKey> toPatternInputMap(IPatternDetails pattern) {
        var inputs = new Object2LongOpenHashMap<AEKey>();
        for (var input : pattern.getInputs()) {
            var possible = input.getPossibleInputs(); if (possible.length == 0) continue;
            add(inputs, possible[0].what(), Math.multiplyExact(possible[0].amount(), input.getMultiplier()));
        }
        return inputs;
    }
    private static Object2LongMap<AEKey> toPatternOutputMap(IPatternDetails pattern) {
        var outputs = new Object2LongOpenHashMap<AEKey>();
        for (var stack : pattern.getOutputs()) add(outputs, stack.what(), stack.amount());
        return outputs;
    }
    private static Object2LongMap<AEKey> toRecipeInputMap(GTRecipe recipe) {
        var inputs = new Object2LongOpenHashMap<AEKey>();
        for (var content : recipe.getInputContents(ItemRecipeCapability.CAP)) {
            var ingredient = ItemRecipeCapability.CAP.of(content.content());
            var stacks = LongIngredient.matchingIngredient(ingredient).getItems();
            if (stacks.length > 0 && !stacks[0].isEmpty()) add(inputs, AEItemKey.of(stacks[0]), LongIngredient.amount(ingredient));
        }
        for (var content : recipe.getInputContents(FluidRecipeCapability.CAP)) {
            var ingredient = FluidRecipeCapability.CAP.of(content.content());
            var stacks = LongFluidIngredient.matchingIngredient(ingredient).getStacks();
            if (stacks.length > 0 && !stacks[0].isEmpty()) add(inputs, AEFluidKey.of(stacks[0]), LongFluidIngredient.amount(ingredient));
        }
        return inputs;
    }
    private static Object2LongMap<AEKey> toRecipeOutputMap(GTRecipe recipe) {
        var outputs = new Object2LongOpenHashMap<AEKey>();
        for (var stack : RecipeStackHelper.getOutputKeys(recipe)) add(outputs, stack.what(), stack.amount());
        return outputs;
    }
    private record PatternSignature(Object2LongMap<AEKey> inputs, Object2LongMap<AEKey> outputs) {
        private PatternSignature { inputs = Object2LongMaps.unmodifiable(new Object2LongOpenHashMap<>(inputs)); outputs = Object2LongMaps.unmodifiable(new Object2LongOpenHashMap<>(outputs)); }
        static PatternSignature of(IPatternDetails pattern) { return new PatternSignature(toPatternInputMap(pattern), toPatternOutputMap(pattern)); }
    }
    /** Stocking inputs and reusable circuits need not be physical inputs in the dispatched AE pattern. */
    private record RecipeSignature(Object2LongMap<AEKey> inputs, Object2LongMap<AEKey> outputs) {
        boolean matches(GTRecipe recipe) { return inputs.equals(toRecipeInputMap(recipe)) && outputs.equals(toRecipeOutputMap(recipe)); }
    }
    private record PendingSlotData(@Nullable CompoundTag data, @Nullable ResourceLocation recipeId, @Nullable RecipeSignature recipeSignature) {}
}
