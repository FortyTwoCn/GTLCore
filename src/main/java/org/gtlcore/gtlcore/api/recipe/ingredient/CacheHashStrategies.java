package org.gtlcore.gtlcore.api.recipe.ingredient;

import com.gregtechceu.gtceu.api.recipe.ingredient.IntProviderIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntProviderFluidIngredient;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.Hash;
import java.util.*;

/** Stable predicate cache keys from native codecs, including tags and data component predicates. */
public final class CacheHashStrategies {
    private CacheHashStrategies() {}
    private static final Map<Ingredient, String> ITEM_KEYS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<FluidIngredient, String> FLUID_KEYS = Collections.synchronizedMap(new WeakHashMap<>());

    public static final class IngredientHashStrategy implements Hash.Strategy<Ingredient> {
        public static final IngredientHashStrategy INSTANCE = new IngredientHashStrategy();
        @Override public int hashCode(Ingredient ingredient) { return ingredient == null ? 0 : itemKey(ingredient).hashCode(); }
        @Override public boolean equals(Ingredient first, Ingredient second) {
            return first == second || first != null && second != null && itemKey(first).equals(itemKey(second));
        }
    }
    public static final class FluidIngredientHashStrategy implements Hash.Strategy<FluidIngredient> {
        public static final FluidIngredientHashStrategy INSTANCE = new FluidIngredientHashStrategy();
        @Override public int hashCode(FluidIngredient ingredient) { return ingredient == null ? 0 : fluidKey(ingredient).hashCode(); }
        @Override public boolean equals(FluidIngredient first, FluidIngredient second) {
            return first == second || first != null && second != null && fluidKey(first).equals(fluidKey(second));
        }
    }
    public static final class SizedIngredientHashStrategy implements Hash.Strategy<SizedIngredient> {
        public static final SizedIngredientHashStrategy INSTANCE = new SizedIngredientHashStrategy();
        @Override public int hashCode(SizedIngredient ingredient) {
            return ingredient == null ? 0 : IngredientHashStrategy.INSTANCE.hashCode(ingredient.ingredient());
        }
        @Override public boolean equals(SizedIngredient first, SizedIngredient second) {
            return first == second || first != null && second != null &&
                    IngredientHashStrategy.INSTANCE.equals(first.ingredient(), second.ingredient());
        }
    }
    public static final class SizedFluidIngredientHashStrategy implements Hash.Strategy<SizedFluidIngredient> {
        public static final SizedFluidIngredientHashStrategy INSTANCE = new SizedFluidIngredientHashStrategy();
        @Override public int hashCode(SizedFluidIngredient ingredient) {
            return ingredient == null ? 0 : FluidIngredientHashStrategy.INSTANCE.hashCode(ingredient.ingredient());
        }
        @Override public boolean equals(SizedFluidIngredient first, SizedFluidIngredient second) {
            return first == second || first != null && second != null &&
                    FluidIngredientHashStrategy.INSTANCE.equals(first.ingredient(), second.ingredient());
        }
    }

    public static final class IngredientEquality {
        public static final Comparator<Item> ITEM_COMPARATOR = Comparator.comparing(BuiltInRegistries.ITEM::getKey);
        public static final Comparator<Ingredient> INGREDIENT_COMPARATOR = Comparator.nullsFirst(
                Comparator.comparing(CacheHashStrategies::itemKey));
        private IngredientEquality() {}
        public static boolean ingredientEquals(Ingredient first, Ingredient second) {
            return IngredientHashStrategy.INSTANCE.equals(first, second);
        }
    }

    private static String itemKey(Ingredient ingredient) {
        while (true) {
            if (ingredient.getCustomIngredient() instanceof LongIngredient quantity) ingredient = quantity.getInner();
            else if (ingredient.getCustomIngredient() instanceof IntProviderIngredient quantity) ingredient = quantity.getInner();
            else break;
        }
        Ingredient predicate = ingredient;
        return ITEM_KEYS.computeIfAbsent(predicate, value -> canonicalPredicate(Ingredient.CODEC
                .encodeStart(registryOps(), value).getOrThrow()).toString());
    }
    private static String fluidKey(FluidIngredient ingredient) {
        while (true) {
            if (ingredient instanceof LongFluidIngredient quantity) ingredient = quantity.getInner();
            else if (ingredient instanceof IntProviderFluidIngredient quantity) ingredient = quantity.getInner();
            else break;
        }
        return FLUID_KEYS.computeIfAbsent(ingredient, value -> canonicalPredicate(FluidIngredient.CODEC
                .encodeStart(registryOps(), value).getOrThrow()).toString());
    }
    private static RegistryOps<JsonElement> registryOps() {
        return RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }
    private static JsonElement canonicalPredicate(JsonElement value) {
        if (value.isJsonArray()) {
            List<JsonElement> choices = new ArrayList<>();
            value.getAsJsonArray().forEach(child -> choices.add(canonicalPredicate(child)));
            return sortedArray(choices);
        }
        if (!value.isJsonObject()) return value.deepCopy();
        var source = value.getAsJsonObject();
        JsonObject result = new JsonObject();
        String type = source.has("type") ? source.get("type").getAsString() : "";
        for (var entry : new TreeMap<>(source.asMap()).entrySet()) {
            String name = entry.getKey();
            var child = entry.getValue();
            if ("children".equals(name) && (type.endsWith(":compound") || type.endsWith(":intersection"))) {
                List<JsonElement> children = new ArrayList<>();
                child.getAsJsonArray().forEach(element -> children.add(canonicalPredicate(element)));
                result.add(name, sortedArray(children));
            } else if ("items".equals(name) && "neoforge:components".equals(type) && child.isJsonArray()) {
                result.add(name, sortedArray(child.getAsJsonArray().asList()));
            } else result.add(name, canonicalData(child));
        }
        return result;
    }
    /** Component list order is meaningful (for example lore), so only object keys are normalized here. */
    private static JsonElement canonicalData(JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject result = new JsonObject();
            new TreeMap<>(value.getAsJsonObject().asMap()).forEach((key, child) -> result.add(key, canonicalData(child)));
            return result;
        }
        if (value.isJsonArray()) {
            JsonArray result = new JsonArray();
            value.getAsJsonArray().forEach(child -> result.add(canonicalData(child)));
            return result;
        }
        return value.deepCopy();
    }
    private static JsonArray sortedArray(List<JsonElement> elements) {
        JsonArray result = new JsonArray();
        elements.stream().sorted(Comparator.comparing(JsonElement::toString)).forEach(result::add);
        return result;
    }
}
