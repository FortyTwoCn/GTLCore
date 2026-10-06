package org.gtlcore.gtlcore.integration.ae2;

import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntProviderIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntProviderFluidIngredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import org.jetbrains.annotations.Nullable;

/** Resolve a recipe output on the server before acknowledging ownership or passing it to a worker. */
public final class AEOutputIngredients {
    private AEOutputIngredients() {}

    public static @Nullable GenericStack item(SizedIngredient ingredient, boolean simulate) {
        if (ingredient.ingredient().getCustomIngredient() instanceof IntProviderIngredient ranged) {
            if (simulate && ranged.getCountProvider().getMaxValue() == 0) return null;
            ingredient = simulate ? new SizedIngredient(ranged.getInner(), ranged.getCountProvider().getMaxValue()) :
                    ranged.copy().collapse();
        }
        long amount = LongIngredient.amount(ingredient);
        if (amount <= 0) return null;
        var stacks = ingredient.getItems();
        if (stacks.length == 0 || stacks[0].isEmpty()) return null;
        return new GenericStack(AEItemKey.of(stacks[0]), amount);
    }

    public static @Nullable GenericStack fluid(SizedFluidIngredient ingredient, boolean simulate) {
        if (ingredient.ingredient() instanceof IntProviderFluidIngredient ranged) {
            if (simulate && ranged.getCountProvider().getMaxValue() == 0) return null;
            ingredient = simulate ? new SizedFluidIngredient(ranged.getInner(), ranged.getCountProvider().getMaxValue()) :
                    ranged.copy().collapse();
        }
        long amount = LongFluidIngredient.amount(ingredient);
        if (amount <= 0) return null;
        var stacks = ingredient.getFluids();
        if (stacks.length == 0 || stacks[0].isEmpty()) return null;
        return new GenericStack(AEFluidKey.of(stacks[0]), amount);
    }
}
