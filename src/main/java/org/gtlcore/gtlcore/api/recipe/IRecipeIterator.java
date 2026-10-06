package org.gtlcore.gtlcore.api.recipe;

import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;
import org.gtlcore.gtlcore.api.machine.trait.RecipeHandlePart;
import org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.lookup.RecipeDB;
import com.gregtechceu.gtceu.api.recipe.lookup.ingredient.AbstractMapIngredient;
import com.gregtechceu.gtceu.api.recipe.lookup.ingredient.MapIngredientTypeManager;
import com.gregtechceu.gtceu.common.machine.trait.ProgrammableCircuitSlotTrait;
import java.util.*;
import java.util.function.Predicate;

/** Core input groups use the pinned GT RecipeDB; native ingredient alternatives stay in separate lists. */
public interface IRecipeIterator {
    static NativeGroupedRecipeIterator search(GTRecipeType type, RecipeDB db, IRecipeCapabilityHolder holder,
                                               IRecipeCapabilityMachine machine, Predicate<GTRecipe> canHandle) {
        var groups = inputGroups(machine);
        var iterator = new NativeGroupedRecipeIterator(type, db, groups, canHandle, () -> {
            for (var logic : type.getCustomRecipeLogicRunners()) {
                var recipe = logic.createCustomRecipe(holder);
                if (recipe != null && canHandle.test(recipe)) return recipe;
            }
            return null;
        });
        List<GTRecipe> cached = new ArrayList<>();
        for (var part : machine.getMEPatternRecipeHandleParts()) cached.addAll(part.getCachedGTRecipe());
        iterator.setAdditionalRecipes(cached);
        iterator.setUseDiveIngredientTreeFind(holder instanceof IRecipeLogicMachine logicMachine &&
                logicMachine.getRecipeLogic() instanceof MultipleRecipesLogic);
        return iterator;
    }

    static List<List<List<AbstractMapIngredient>>> inputGroups(IRecipeCapabilityMachine machine) {
        List<List<List<AbstractMapIngredient>>> groups = new ArrayList<>();
        for (var part : machine.getMEPatternRecipeHandleParts()) {
            Map<Integer, Map<RecipeCapability<?>, List<Object>>> slots = new TreeMap<>();
            for (var handler : part.getMERecipeHandlers()) {
                if (!handler.getCapability().isRecipeSearchFilter()) continue;
                var contents = handler.getActiveAndUnCachedSlotsLimitContentsMap();
                for (var entry : contents.int2ObjectEntrySet()) {
                    slots.computeIfAbsent(entry.getIntKey(), $ -> new LinkedHashMap<>())
                            .computeIfAbsent(handler.getCapability(), $ -> new ArrayList<>()).addAll(entry.getValue());
                }
            }
            for (var contents : slots.values()) addGroup(groups, contents);
        }
        var parts = machine.getNormalRecipeHandlePart(IO.IN);
        var shared = machine.getSharedRecipeHandlePart();
        if (machine.isDistinct()) {
            for (var part : parts) {
                var inputs = contents(part, machine, null);
                if (part != shared) supplementControllerCircuits(inputs, shared, machine);
                addGroup(groups, inputs);
            }
        } else {
            var sharedContents = shared == null ? Map.<RecipeCapability<?>, List<Object>>of() : contents(shared, machine, null);
            if (shared != null) addGroup(groups, sharedContents);
            for (var part : parts) {
                if (part == shared) continue;
                var inputs = contents(part, machine, ItemRecipeCapability.CAP);
                var fluids = sharedContents.get(FluidRecipeCapability.CAP);
                if (fluids != null) inputs.put(FluidRecipeCapability.CAP, new ArrayList<>(fluids));
                supplementControllerCircuits(inputs, shared, machine);
                addGroup(groups, inputs);
            }
        }
        return groups;
    }

    private static void supplementControllerCircuits(Map<RecipeCapability<?>, List<Object>> inputs,
            RecipeHandlePart shared, IRecipeCapabilityMachine machine) {
        if (shared == null) return;
        for (var handler : shared.getCapability(ItemRecipeCapability.CAP)) {
            if (handler instanceof ProgrammableCircuitSlotTrait && !machine.isRecipeInputHandlerExcluded(handler))
                inputs.computeIfAbsent(ItemRecipeCapability.CAP, $ -> new ArrayList<>()).addAll(handler.getContents());
        }
    }

    private static Map<RecipeCapability<?>, List<Object>> contents(RecipeHandlePart part,
            IRecipeCapabilityMachine machine, RecipeCapability<?> only) {
        Map<RecipeCapability<?>, List<Object>> result = new LinkedHashMap<>();
        Set<IRecipeHandler<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var entry : part.getHandlerFastIterable()) {
            var cap = entry.getKey();
            if (!cap.isRecipeSearchFilter() || only != null && cap != only) continue;
            for (var handler : entry.getValue()) {
                if (!visited.add(handler) || machine.isRecipeInputHandlerExcluded(handler)) continue;
                result.computeIfAbsent(cap, $ -> new ArrayList<>()).addAll(handler.getContents());
            }
        }
        return result;
    }

    private static void addGroup(List<List<List<AbstractMapIngredient>>> groups,
                                  Map<RecipeCapability<?>, List<Object>> contents) {
        List<List<AbstractMapIngredient>> ingredients = new ArrayList<>();
        for (var entry : contents.entrySet()) {
            var cap = entry.getKey();
            if (!cap.isRecipeSearchFilter()) continue;
            for (var content : cap.compressIngredients(entry.getValue())) {
                var alternatives = MapIngredientTypeManager.getFrom(content, cap);
                if (!alternatives.isEmpty()) ingredients.add(List.copyOf(alternatives));
            }
        }
        if (!ingredients.isEmpty()) groups.add(List.copyOf(ingredients));
    }
}
