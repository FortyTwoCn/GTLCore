package org.gtlcore.gtlcore.api.recipe.ingredient;

import com.gregtechceu.gtceu.api.recipe.lookup.ingredient.AbstractMapIngredient;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.DataComponentFluidIngredient;
import java.util.*;

/** Native GT map-ingredient extension for component predicates and actual inventory query stacks. */
public final class ComponentFluidMapIngredient extends AbstractMapIngredient {
    // Database keys retain their predicates. The query catalogue must not retain old recipe databases after reload.
    private static final Map<Fluid, WeakHashMap<DataComponentFluidIngredient, Boolean>> PREDICATES = new IdentityHashMap<>();
    private final FluidStack stack;
    private final DataComponentFluidIngredient predicate;
    private ComponentFluidMapIngredient(FluidStack stack, DataComponentFluidIngredient predicate) {
        this.stack = stack.copyWithAmount(1); this.predicate = predicate;
    }
    public static List<AbstractMapIngredient> from(DataComponentFluidIngredient ingredient) {
        List<AbstractMapIngredient> result = new ArrayList<>();
        for (var stack : ingredient.getStacks()) {
            synchronized (PREDICATES) {
                PREDICATES.computeIfAbsent(stack.getFluid(), $ -> new WeakHashMap<>()).put(ingredient, Boolean.TRUE);
            }
            result.add(new ComponentFluidMapIngredient(stack, ingredient));
        }
        return result;
    }
    public static List<AbstractMapIngredient> from(FluidStack stack) {
        List<DataComponentFluidIngredient> predicates;
        synchronized (PREDICATES) {
            var registered = PREDICATES.get(stack.getFluid());
            if (registered == null) return List.of();
            predicates = new ArrayList<>(registered.keySet());
        }
        List<AbstractMapIngredient> result = new ArrayList<>();
        // Query every matching predicate separately: a single broad key would retrieve only one matching tree branch.
        for (var predicate : predicates) {
            if (predicate.test(stack)) result.add(new ComponentFluidMapIngredient(stack, predicate));
        }
        return result;
    }
    // HolderSet tags/direct sets and additional query components must not change an equal predicate's bucket.
    @Override protected int hash() { return stack.getFluid().hashCode(); }
    @Override public boolean isSpecialIngredient() { return true; }
    @Override public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof ComponentFluidMapIngredient other) || !FluidStack.isSameFluid(stack, other.stack)) return false;
        return predicate.isStrict() == other.predicate.isStrict() && predicate.components().equals(other.predicate.components()) &&
                predicate.fluids().stream().allMatch(other.predicate.fluids()::contains) &&
                other.predicate.fluids().stream().allMatch(predicate.fluids()::contains);
    }
    @Override public String toString() { return "Core component fluid index: " + stack; }
}
