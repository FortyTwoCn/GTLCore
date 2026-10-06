package org.gtlcore.gtlcore.api.item.tool.ae2.patternTool;

import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import java.util.ArrayList;
import java.util.List;

/** Representative stacks for diagnostics, and exact component-aware keys for AE processing patterns. */
public class RecipeStackHelper {
    final GTRecipe recipe;
    protected RecipeStackHelper(GTRecipe recipe) { this.recipe = recipe; }
    public List<ItemStack> getInputItemStacks() { return getInputItemStacksFromRecipe(recipe); }
    public List<FluidStack> getInputFluidStacks() { return getInputFluidStacksFromRecipe(recipe); }
    public List<ItemStack> getOutputItemStacks() { return getOutputItemStacksFromRecipe(recipe); }
    public List<FluidStack> getOutputFluidStacks() { return getOutputFluidStacksFromRecipe(recipe); }

    public static List<ItemStack> getInputItemStacksFromRecipe(GTRecipe recipe) {
        return recipe == null ? List.of() : recipe.getInputContents(ItemRecipeCapability.CAP).stream()
                .map(RecipeStackHelper::itemStack).filter(stack -> !stack.isEmpty()).toList();
    }
    public static List<FluidStack> getInputFluidStacksFromRecipe(GTRecipe recipe) {
        return recipe == null ? List.of() : recipe.getInputContents(FluidRecipeCapability.CAP).stream()
                .map(RecipeStackHelper::fluidStack).filter(stack -> !stack.isEmpty()).toList();
    }
    public static List<ItemStack> getOutputItemStacksFromRecipe(GTRecipe recipe) {
        return recipe == null ? List.of() : recipe.getOutputContents(ItemRecipeCapability.CAP).stream()
                .map(RecipeStackHelper::itemStack).filter(stack -> !stack.isEmpty()).toList();
    }
    public static List<FluidStack> getOutputFluidStacksFromRecipe(GTRecipe recipe) {
        return recipe == null ? List.of() : recipe.getOutputContents(FluidRecipeCapability.CAP).stream()
                .map(RecipeStackHelper::fluidStack).filter(stack -> !stack.isEmpty()).toList();
    }

    public static ItemStack itemStack(Content content) {
        if (content == null || content.content() == null) return ItemStack.EMPTY;
        var ingredient = ItemRecipeCapability.CAP.of(content.content());
        var stacks = LongIngredient.matchingIngredient(ingredient).getItems();
        long amount = LongIngredient.amount(ingredient);
        return stacks.length == 0 || amount <= 0 ? ItemStack.EMPTY :
                stacks[0].copyWithCount((int) Math.min(Integer.MAX_VALUE, amount));
    }
    public static FluidStack fluidStack(Content content) {
        if (content == null || content.content() == null) return FluidStack.EMPTY;
        var ingredient = FluidRecipeCapability.CAP.of(content.content());
        var stacks = LongFluidIngredient.matchingIngredient(ingredient).getStacks();
        long amount = LongFluidIngredient.amount(ingredient);
        return stacks.length == 0 || amount <= 0 ? FluidStack.EMPTY :
                stacks[0].copyWithAmount((int) Math.min(Integer.MAX_VALUE, amount));
    }
    public static List<GenericStack> getInputKeys(GTRecipe recipe) {
        return keys(recipe.getInputContents(ItemRecipeCapability.CAP), recipe.getInputContents(FluidRecipeCapability.CAP));
    }
    public static List<GenericStack> getOutputKeys(GTRecipe recipe) {
        return keys(recipe.getOutputContents(ItemRecipeCapability.CAP), recipe.getOutputContents(FluidRecipeCapability.CAP));
    }
    private static List<GenericStack> keys(List<Content> items, List<Content> fluids) {
        var result = new ArrayList<GenericStack>(items.size() + fluids.size());
        for (var content : items) {
            var stack = itemStack(content);
            if (stack.isEmpty()) throw new IllegalArgumentException("Recipe contains an unresolved item ingredient");
            result.add(new GenericStack(AEItemKey.of(stack), LongIngredient.amount(ItemRecipeCapability.CAP.of(content.content()))));
        }
        for (var content : fluids) {
            var stack = fluidStack(content);
            if (stack.isEmpty()) throw new IllegalArgumentException("Recipe contains an unresolved fluid ingredient");
            result.add(new GenericStack(AEFluidKey.of(stack), LongFluidIngredient.amount(FluidRecipeCapability.CAP.of(content.content()))));
        }
        return result;
    }
    public static String getItemTranslatedName(ItemStack stack) {
        return stack.getHoverName().getString() + "(" + BuiltInRegistries.ITEM.getKey(stack.getItem()) + " " + stack.getCount() + ") ";
    }
    public static String getFluidTranslatedName(FluidStack stack) {
        return stack.getHoverName().getString() + "(" + BuiltInRegistries.FLUID.getKey(stack.getFluid()) + " " + stack.getAmount() + ") ";
    }
    public static List<ItemStack> getOutputItems(GTRecipe recipe) { return getOutputItemStacksFromRecipe(recipe); }
}
