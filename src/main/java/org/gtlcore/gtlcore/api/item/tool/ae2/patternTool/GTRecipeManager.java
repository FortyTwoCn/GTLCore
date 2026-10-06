package org.gtlcore.gtlcore.api.item.tool.ae2.patternTool;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class GTRecipeManager {
    private List<GTRecipe> recipes;
    public GTRecipeManager() {
        this(Objects.requireNonNull(ServerLifecycleHooks.getCurrentServer(), "Recipe tools require an active server")
                .getRecipeManager().getRecipes());
    }
    public GTRecipeManager(Collection<? extends RecipeHolder<?>> holders) {
        recipes = new ArrayList<>();
        for (var holder : holders) if (holder.value() instanceof GTRecipe recipe) recipes.add(recipe);
    }
    public List<GTRecipe> getRecipes() { return List.copyOf(recipes); }
    public void filterRecipesByType(GTRecipeType type) {
        recipes = recipes.stream().filter(recipe -> recipe.getType() == type).toList();
    }
    public boolean isItemStackMatchByStringArray(String[] matches, ItemStack stack) {
        return matches(matches, BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }
    public boolean isFluidStackMatchByStringArray(String[] matches, FluidStack stack) {
        return matches(matches, BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString());
    }
    private static boolean matches(String[] matches, String id) {
        for (String match : matches) if (!match.isEmpty() && id.contains(match)) return true;
        return false;
    }
    public void filterRecipesByInputsIdArray(String[] matches, boolean white) {
        recipes = recipes.stream().filter(recipe -> white ==
                (RecipeStackHelper.getInputItemStacksFromRecipe(recipe).stream().anyMatch(stack -> isItemStackMatchByStringArray(matches, stack)) ||
                 RecipeStackHelper.getInputFluidStacksFromRecipe(recipe).stream().anyMatch(stack -> isFluidStackMatchByStringArray(matches, stack)))).toList();
    }
    public void filterRecipesByOutputsIdArray(String[] matches, boolean white) {
        recipes = recipes.stream().filter(recipe -> white ==
                (RecipeStackHelper.getOutputItemStacksFromRecipe(recipe).stream().anyMatch(stack -> isItemStackMatchByStringArray(matches, stack)) ||
                 RecipeStackHelper.getOutputFluidStacksFromRecipe(recipe).stream().anyMatch(stack -> isFluidStackMatchByStringArray(matches, stack)))).toList();
    }
    public static int circuit(GTRecipe recipe) {
        for (var content : recipe.getInputContents(ItemRecipeCapability.CAP)) {
            var stack = RecipeStackHelper.itemStack(content);
            if (GTItems.PROGRAMMED_CIRCUIT.isIn(stack)) return IntCircuitBehaviour.getCircuitConfiguration(stack);
        }
        return 0;
    }
    public void filterRecipesByCircuit(int circuit) {
        recipes = recipes.stream().filter(recipe -> circuit(recipe) == circuit).toList();
    }
}
