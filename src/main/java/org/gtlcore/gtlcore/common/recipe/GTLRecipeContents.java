package org.gtlcore.gtlcore.common.recipe;

import org.gtlcore.gtlcore.api.recipe.NativeContentState;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.DataComponentFluidIngredient;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.util.List;
import java.util.function.Supplier;

/** Register Core's per-tier chances through native GT builders and native content metadata. */
public final class GTLRecipeContents {
    private GTLRecipeContents() {}

    private static GTRecipeBuilder boosted(GTRecipeBuilder builder, RecipeCapability<?> capability,
            boolean input, int chance, int boost, Supplier<GTRecipeBuilder> add) {
        if (chance <= 0 || chance > ChanceLogic.getMaxChancedValue()) {
            throw new IllegalArgumentException("Invalid recipe chance " + chance + " for " + builder.id);
        }
        return added(builder, capability, input, boost, add);
    }

    private static GTRecipeBuilder added(GTRecipeBuilder builder, RecipeCapability<?> capability,
            boolean input, int boost, Supplier<GTRecipeBuilder> add) {
        var contents = input ? builder.perTick ? builder.tickInput : builder.input :
                builder.perTick ? builder.tickOutput : builder.output;
        int size = contents.getOrDefault(capability, List.of()).size();
        add.get();
        var entries = contents.getOrDefault(capability, List.of());
        if (entries.size() != size + 1) {
            throw new IllegalStateException("Native builder did not add the chanced ingredient for " + builder.id);
        }
        NativeContentState.of(entries.get(size)).gtlcore$setTierChanceBoost(boost);
        return builder;
    }

    public static GTRecipeBuilder chancedOutput(GTRecipeBuilder builder, ItemStack stack, int chance, int boost) {
        if (stack.isEmpty()) return builder;
        return chancedOutput(builder, RecipeHelper.makeSizedIngredient(stack), chance, boost);
    }

    /** Native fraction parsing retains its denominator, rather than rounding to a percentage. */
    public static GTRecipeBuilder chancedOutput(GTRecipeBuilder builder, ItemStack stack, String fraction, int boost) {
        if (stack.isEmpty()) return builder;
        return added(builder, ItemRecipeCapability.CAP, false, boost, () -> builder.chancedOutput(stack, fraction));
    }

    public static GTRecipeBuilder chancedOutput(GTRecipeBuilder builder, TagPrefix prefix, Material material,
            int count, String fraction, int boost) {
        return chancedOutput(builder, ChemicalHelper.get(prefix, material, count), fraction, boost);
    }

    public static GTRecipeBuilder chancedOutput(GTRecipeBuilder builder, TagPrefix prefix, Material material,
            String fraction, int boost) {
        return chancedOutput(builder, prefix, material, 1, fraction, boost);
    }

    /** Recipe quantities use a native sized ingredient with an exact long payload when necessary. */
    public static SizedFluidIngredient fluid(Material material, long amount) {
        return LongFluidIngredient.withAmount(material.asSingleFluidIngredient(1), amount);
    }

    public static GTRecipeBuilder chancedOutput(GTRecipeBuilder builder, Ingredient ingredient, int chance, int boost) {
        return chancedOutput(builder, new SizedIngredient(ingredient, 1), chance, boost);
    }

    public static GTRecipeBuilder chancedOutput(GTRecipeBuilder builder, SizedIngredient ingredient, int chance, int boost) {
        return boosted(builder, ItemRecipeCapability.CAP, false, chance, boost, () -> builder.chancedOutput(ingredient, chance));
    }

    public static GTRecipeBuilder chancedOutput(GTRecipeBuilder builder, TagPrefix prefix, Material material, int chance, int boost) {
        return chancedOutput(builder, prefix, material, 1, chance, boost);
    }

    public static GTRecipeBuilder chancedOutput(GTRecipeBuilder builder, TagPrefix prefix, Material material, int count, int chance, int boost) {
        return chancedOutput(builder, ChemicalHelper.get(prefix, material, count), chance, boost);
    }

    public static GTRecipeBuilder chancedOutput(GTRecipeBuilder builder, FluidStack stack, int chance, int boost) {
        if (stack.isEmpty()) return builder;
        var ingredient = stack.isComponentsPatchEmpty() ? FluidIngredient.single(stack) : DataComponentFluidIngredient.of(true, stack);
        return chancedOutput(builder, new SizedFluidIngredient(ingredient, stack.getAmount()), chance, boost);
    }

    public static GTRecipeBuilder chancedOutput(GTRecipeBuilder builder, SizedFluidIngredient ingredient, int chance, int boost) {
        return boosted(builder, FluidRecipeCapability.CAP, false, chance, boost, () -> builder.chancedOutput(ingredient, chance));
    }

    public static GTRecipeBuilder chancedInput(GTRecipeBuilder builder, ItemStack stack, int chance, int boost) {
        if (stack.isEmpty()) return builder;
        return chancedInput(builder, RecipeHelper.makeSizedIngredient(stack), chance, boost);
    }

    public static GTRecipeBuilder chancedInput(GTRecipeBuilder builder, Ingredient ingredient, int chance, int boost) {
        return chancedInput(builder, new SizedIngredient(ingredient, 1), chance, boost);
    }

    public static GTRecipeBuilder chancedInput(GTRecipeBuilder builder, SizedIngredient ingredient, int chance, int boost) {
        return boosted(builder, ItemRecipeCapability.CAP, true, chance, boost, () -> builder.chancedInput(ingredient, chance));
    }

    public static GTRecipeBuilder chancedInput(GTRecipeBuilder builder, FluidStack stack, int chance, int boost) {
        if (stack.isEmpty()) return builder;
        var ingredient = stack.isComponentsPatchEmpty() ? FluidIngredient.single(stack) : DataComponentFluidIngredient.of(true, stack);
        return chancedInput(builder, new SizedFluidIngredient(ingredient, stack.getAmount()), chance, boost);
    }

    public static GTRecipeBuilder chancedInput(GTRecipeBuilder builder, SizedFluidIngredient ingredient, int chance, int boost) {
        return boosted(builder, FluidRecipeCapability.CAP, true, chance, boost, () -> builder.chancedInput(ingredient, chance));
    }
}
