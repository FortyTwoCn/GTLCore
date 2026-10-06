package org.gtlcore.gtlcore.api.item.tool.ae2.patternTool;

import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.common.data.GTItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import java.util.*;

public class ConflictAnalysisObj {
    public GTRecipe recipe;
    public List<ItemStack> inputItemStacks = new ArrayList<>();
    public List<FluidStack> inputFluidStacks = new ArrayList<>();
    public List<ItemStack> outputItemStacks = new ArrayList<>();
    public List<FluidStack> outputFluidStacks = new ArrayList<>();
    public List<String> inputItemIds = new ArrayList<>();
    public List<String> inputFluidIds = new ArrayList<>();
    public List<String> outputItemIds = new ArrayList<>();
    public List<String> outputFluidIds = new ArrayList<>();
    public Map<String, Long> inputItemIdsMap = new HashMap<>();
    public Map<String, Long> inputFluidIdsMap = new HashMap<>();
    public Map<String, Long> outputItemIdsMap = new HashMap<>();
    public Map<String, Long> outputFluidIdsMap = new HashMap<>();
    public Number CIRCUIT;

    public ConflictAnalysisObj(GTRecipe recipe) {
        this.recipe = recipe;
        CIRCUIT = GTRecipeManager.circuit(recipe);
        populateItems(recipe.getInputContents(ItemRecipeCapability.CAP), inputItemStacks, inputItemIds, inputItemIdsMap);
        populateFluids(recipe.getInputContents(FluidRecipeCapability.CAP), inputFluidStacks, inputFluidIds, inputFluidIdsMap);
        populateItems(recipe.getOutputContents(ItemRecipeCapability.CAP), outputItemStacks, outputItemIds, outputItemIdsMap);
        populateFluids(recipe.getOutputContents(FluidRecipeCapability.CAP), outputFluidStacks, outputFluidIds, outputFluidIdsMap);
    }
    private void populateItems(List<Content> contents, List<ItemStack> stacks, List<String> ids, Map<String, Long> amounts) {
        for (var content : contents) {
            var stack = RecipeStackHelper.itemStack(content);
            if (stack.isEmpty() || GTItems.PROGRAMMED_CIRCUIT.isIn(stack)) continue;
            var id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            stacks.add(stack); ids.add(id);
            amounts.put(id, LongIngredient.amount(ItemRecipeCapability.CAP.of(content.content())));
        }
    }
    private void populateFluids(List<Content> contents, List<FluidStack> stacks, List<String> ids, Map<String, Long> amounts) {
        for (var content : contents) {
            var stack = RecipeStackHelper.fluidStack(content);
            if (stack.isEmpty()) continue;
            var id = BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString();
            stacks.add(stack); ids.add(id);
            amounts.put(id, LongFluidIngredient.amount(FluidRecipeCapability.CAP.of(content.content())));
        }
    }
    @Override public String toString() {
        return "Recipe " + recipe.id + " CIRCUIT: " + CIRCUIT + "\ninputs: " + inputItemIdsMap + " " + inputFluidIdsMap +
                "\noutputs: " + outputItemIdsMap + " " + outputFluidIdsMap;
    }
}
