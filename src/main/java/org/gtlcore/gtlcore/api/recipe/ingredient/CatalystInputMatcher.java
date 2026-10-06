package org.gtlcore.gtlcore.api.recipe.ingredient;

import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import it.unimi.dsi.fastutil.objects.*;
import it.unimi.dsi.fastutil.Hash;
import java.util.List;

/** Shared catalysts satisfy individual non-consumed demands without allocating or consuming their reusable stock. */
public final class CatalystInputMatcher {
    private CatalystInputMatcher() {}
    public static final Hash.Strategy<FluidStack> FLUID_COMPONENT_KEYS = new Hash.Strategy<>() {
        @Override public int hashCode(FluidStack stack) { return stack == null ? 0 : FluidStack.hashFluidAndComponents(stack); }
        @Override public boolean equals(FluidStack first, FluidStack second) {
            return first == second || first != null && second != null && FluidStack.isSameFluidSameComponents(first, second);
        }
    };
    public static List<SizedIngredient> items(IO io, GTRecipe recipe, List<SizedIngredient> left, boolean simulate, Object2LongMap<ItemStack> owned) {
        if (io != IO.IN || !simulate || recipe == null) return left;
        var nonConsumed = new ObjectOpenCustomHashSet<Ingredient>(CacheHashStrategies.IngredientHashStrategy.INSTANCE);
        for (var content : recipe.getInputContents(ItemRecipeCapability.CAP)) {
            var input = ItemRecipeCapability.CAP.of(content.content());
            if (content.chance() > 0) {
                if (owned.keySet().stream().anyMatch(input.ingredient()::test)) return left;
            } else nonConsumed.add(input.ingredient());
        }
        left.removeIf(input -> {
            if (!nonConsumed.contains(input.ingredient())) return false;
            long required = LongIngredient.amount(input);
            for (var entry : owned.object2LongEntrySet()) {
                if (!input.ingredient().test(entry.getKey())) continue;
                required -= Math.min(required, entry.getLongValue());
                if (required == 0) return true;
            }
            return required == 0;
        });
        return left;
    }
    public static List<SizedFluidIngredient> fluids(IO io, GTRecipe recipe, List<SizedFluidIngredient> left, boolean simulate, Object2LongMap<FluidStack> owned) {
        if (io != IO.IN || !simulate || recipe == null) return left;
        var nonConsumed = new ObjectOpenCustomHashSet<FluidIngredient>(CacheHashStrategies.FluidIngredientHashStrategy.INSTANCE);
        for (var content : recipe.getInputContents(FluidRecipeCapability.CAP)) {
            var input = FluidRecipeCapability.CAP.of(content.content());
            if (content.chance() > 0) {
                if (owned.keySet().stream().anyMatch(input.ingredient()::test)) return left;
            } else nonConsumed.add(input.ingredient());
        }
        left.removeIf(input -> {
            if (!nonConsumed.contains(input.ingredient())) return false;
            long required = LongFluidIngredient.amount(input);
            for (var entry : owned.object2LongEntrySet()) {
                if (!input.ingredient().test(entry.getKey())) continue;
                required -= Math.min(required, entry.getLongValue());
                if (required == 0) return true;
            }
            return required == 0;
        });
        return left;
    }
}
