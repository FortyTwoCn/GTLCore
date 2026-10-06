package org.gtlcore.gtlcore.api.recipe;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.lookup.RecipeDB;
import com.gregtechceu.gtceu.api.recipe.lookup.ingredient.AbstractMapIngredient;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** One native database iterator per physical/ME input group; no shared mutable holder on the recipe type. */
public final class NativeGroupedRecipeIterator implements Iterator<GTRecipe>, IAdditionalRecipeIterator {
    private final GTRecipeType type;
    private final RecipeDB db;
    private final List<List<List<AbstractMapIngredient>>> groups;
    private final Predicate<GTRecipe> predicate;
    private final Supplier<@Nullable GTRecipe> fallback;
    private final Set<ResourceLocation> emitted = new HashSet<>();
    private List<GTRecipe> additional = List.of();
    private Iterator<GTRecipe> current = Collections.emptyIterator();
    private int groupIndex, additionalIndex;
    private boolean allPerGroup, fallbackTried;
    private @Nullable GTRecipe next;

    public NativeGroupedRecipeIterator(GTRecipeType type, RecipeDB db,
            List<List<List<AbstractMapIngredient>>> groups, Predicate<GTRecipe> predicate,
            Supplier<@Nullable GTRecipe> fallback) {
        this.type = type; this.db = db; this.groups = List.copyOf(groups); this.predicate = predicate; this.fallback = fallback;
    }
    @Override public void setUseDiveIngredientTreeFind(boolean enabled) { allPerGroup = enabled; }
    @Override public void setAdditionalRecipes(List<GTRecipe> recipes) { additional = List.copyOf(recipes); additionalIndex = 0; }
    @Override public List<GTRecipe> getAdditionalRecipes() { return additional; }
    @Override public boolean hasNext() {
        if (next != null) return true;
        while (true) {
            while (current.hasNext()) {
                var candidate = current.next();
                if (!emitted.add(candidate.getId())) continue;
                next = candidate;
                if (!allPerGroup) current = Collections.emptyIterator();
                return true;
            }
            if (groupIndex < groups.size()) {
                current = new RecipeDB.RecipeIterator(db, groups.get(groupIndex++), recipe ->
                        recipe.recipeType == type && !emitted.contains(recipe.getId()) && predicate.test(recipe));
                continue;
            }
            while (additionalIndex < additional.size()) {
                var candidate = additional.get(additionalIndex++);
                if (candidate.recipeType != type || emitted.contains(candidate.getId()) || !predicate.test(candidate)) continue;
                emitted.add(candidate.getId()); next = candidate; return true;
            }
            if (!fallbackTried && emitted.isEmpty()) {
                fallbackTried = true; next = fallback.get();
                if (next != null) { emitted.add(next.getId()); return true; }
            }
            return false;
        }
    }
    @Override public GTRecipe next() {
        if (!hasNext()) throw new NoSuchElementException();
        var result = next; next = null; return result;
    }
    public void reset() {
        groupIndex = 0; additionalIndex = 0; current = Collections.emptyIterator(); emitted.clear(); next = null; fallbackTried = false;
    }
}
