package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.machine.trait.RecipeHandlePart;
import org.gtlcore.gtlcore.api.recipe.*;
import org.gtlcore.gtlcore.api.recipe.ingredient.*;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeHandlerList;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.gtlcore.gtlcore.migration.NativeCoreRecipeRunnerIntegrationTest.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeCoreParallelIntegrationTest {
    @Test void coreParallelUsesOneSelectedBusAndReservesNonConsumedInputs() {
        var holder = new CoreHolder();
        var first = items(holder, IO.IN, new ItemStack(Items.IRON_INGOT, 3));
        var second = items(holder, IO.IN, new ItemStack(Items.IRON_INGOT, 9), new ItemStack(Items.DIAMOND));
        holder.inputs.addAll(List.of(new RecipeHandlePart(List.of(first)), new RecipeHandlePart(List.of(second))));
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("core_parallel_route")
                .inputItems(Items.IRON_INGOT, 2).notConsumable(Items.DIAMOND).build();
        assertEquals(4, IParallelLogic.getMaxParallel(holder, recipe, 100));
        assertSame(holder.inputs.get(1), holder.getActiveRecipeHandle(recipe));
        assertEquals(3, first.getStackInSlot(0).getCount()); assertEquals(9, second.getStackInSlot(0).getCount());
        second.storage.setStackInSlot(1, ItemStack.EMPTY);
        assertEquals(0, IParallelLogic.getMaxParallel(holder, recipe, 1));
        assertEquals(9, second.getStackInSlot(0).getCount());
    }

    @Test void oneSizedIngredientCanUseSeveralMatchingStacksInTheSelectedBus() {
        var holder = new CoreHolder();
        var inventory = items(holder, IO.IN, new ItemStack(Items.IRON_INGOT, 3), new ItemStack(Items.GOLD_INGOT, 4));
        holder.inputs.add(new RecipeHandlePart(List.of(inventory)));
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("core_parallel_alternatives")
                .inputItems(new SizedIngredient(Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT), 2)).build();
        assertEquals(3, IParallelLogic.getMaxParallel(holder, recipe, 100));
        assertEquals(3, inventory.getStackInSlot(0).getCount()); assertEquals(4, inventory.getStackInSlot(1).getCount());
    }

    @Test void repeatedConsumableRequirementsCannotCountTheSameStockTwice() {
        var holder = new CoreHolder(); var inventory = items(holder, IO.IN, new ItemStack(Items.IRON_INGOT, 21));
        holder.inputs.add(new RecipeHandlePart(List.of(inventory)));
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("core_parallel_overlap")
                .inputItems(Items.IRON_INGOT, 3).inputItems(Items.IRON_INGOT, 2).build();
        assertEquals(4, IParallelLogic.getMaxParallel(holder, recipe, 100));
        assertEquals(21, inventory.getStackInSlot(0).getCount());
    }

    @Test void sharedNativeFluidLimitsTheSameSelectedItemBus() {
        var holder = new CoreHolder(); holder.distinct = false;
        var inventory = items(holder, IO.IN, new ItemStack(Items.IRON_INGOT, 64)); var tank = fluids(holder, IO.IN, 1000, 850);
        var part = new RecipeHandlePart(List.of(inventory)); holder.inputs.add(part);
        holder.shared = new RecipeHandlePart(List.of(tank)); part.addSharedFluidHandlers(List.of(tank));
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("core_parallel_shared_fluid")
                .inputItems(Items.IRON_INGOT, 2).inputFluids(SizedFluidIngredient.of(Fluids.WATER, 200)).build();
        assertEquals(4, IParallelLogic.getMaxParallel(holder, recipe, 100));
        assertEquals(850, tank.getStorages()[0].getFluidAmount()); assertEquals(64, inventory.getStackInSlot(0).getCount());
    }

    @Test void nativeHoldersKeepTheirNativeInventoryGroupingForParallelLimits() {
        var holder = new NativeHolder();
        var first = items(holder, IO.IN, new ItemStack(Items.IRON_INGOT, 9));
        var second = items(holder, IO.IN, new ItemStack(Items.IRON_INGOT, 9));
        holder.addHandlerList(RecipeHandlerList.of(IO.IN, 0xFF0000, first));
        holder.addHandlerList(RecipeHandlerList.of(IO.IN, 0x0000FF, second));
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_parallel_colors").inputItems(Items.IRON_INGOT, 2).build();
        assertEquals(4, IParallelLogic.getMaxParallel(holder, recipe, 100));
        assertEquals(9, first.getStackInSlot(0).getCount()); assertEquals(9, second.getStackInSlot(0).getCount());
    }

    @Test void coreOutputLimitChecksScaledContentsAcrossAllOutputHandlers() {
        var holder = new CoreHolder(); var first = fluids(holder, IO.OUT, 350, 0); var second = fluids(holder, IO.OUT, 550, 0);
        holder.outputs.addAll(List.of(new RecipeHandlePart(List.of(first)), new RecipeHandlePart(List.of(second))));
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("core_parallel_output").outputFluids(SizedFluidIngredient.of(Fluids.WATER, 200)).build();
        assertEquals(4, IParallelLogic.getMinParallel(holder, recipe, 100));
        assertEquals(0, first.getStorages()[0].getFluidAmount()); assertEquals(0, second.getStorages()[0].getFluidAmount());
        first.getStorages()[0].setFluid(new net.neoforged.neoforge.fluids.FluidStack(Fluids.WATER, 350));
        second.getStorages()[0].setFluid(new net.neoforged.neoforge.fluids.FluidStack(Fluids.WATER, 550));
        assertEquals(0, IParallelLogic.getMinParallel(holder, recipe, 1));
    }

    @Test void outputVoidingStillBoundsQuantitiesBeforeLongMultiplication() {
        var holder = new CoreHolder() {
            @Override public boolean canVoidRecipeOutputs(RecipeCapability<?> cap) { return cap == FluidRecipeCapability.CAP; }
        };
        holder.outputs.add(new RecipeHandlePart(List.of(fluids(holder, IO.OUT, 1, 0))));
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("core_parallel_overflow")
                .outputFluids(LongFluidIngredient.create(Fluids.WATER, 9_007_199_254_740_993L)).build();
        assertEquals(Long.MAX_VALUE / 9_007_199_254_740_993L, IParallelLogic.getMinParallel(holder, recipe, Long.MAX_VALUE));
    }

    @Test void longOutputChancePrerollRetainsExactQuantityAndMetadataWithoutChangingTheSource() {
        var holder = new CoreHolder();
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("core_parallel_chance")
                .chancedOutput(SizedFluidIngredient.of(Fluids.WATER, 1), 5000).EUt(32).build();
        IGTRecipe.of(recipe).setRealParallels(9_007_199_254_740_994L); IGTRecipe.of(recipe).setBatchSize(37);
        var source = recipe.outputs.get(FluidRecipeCapability.CAP).getFirst();
        NativeContentState.of(source).gtlcore$setSlotName("long_output");
        var cache = holder.getRecipeLogic().getChanceCaches().get(FluidRecipeCapability.CAP);
        org.gtlcore.gtlcore.api.recipe.chance.LongChanceLogic.updateCachedChance(source.content(), cache, 0);
        var rolled = IParallelLogic.getRecipeOutputChance(holder, recipe);
        long quantity = rolled.outputs.get(FluidRecipeCapability.CAP).stream().mapToLong(content -> LongFluidIngredient.amount(FluidRecipeCapability.CAP.of(content.content()))).sum();
        assertEquals(4_503_599_627_370_497L, quantity);
        assertEquals(5000, source.chance()); assertEquals(1, LongFluidIngredient.amount(FluidRecipeCapability.CAP.of(source.content())));
        assertEquals(37, IGTRecipe.of(rolled).getBatchSize());
        assertTrue(rolled.outputs.get(FluidRecipeCapability.CAP).stream().allMatch(content -> content.chance() == content.maxChance() && "long_output".equals(NativeContentState.of(content).gtlcore$getSlotName())));
    }

    @Test void coreRunnerHonorsNativeNoneChanceLogicInsteadOfForcingOr() {
        var holder = new CoreHolder(); var tank = fluids(holder, IO.OUT, 1000, 0); holder.outputs.add(new RecipeHandlePart(List.of(tank)));
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("core_chance_none")
                .chancedOutput(SizedFluidIngredient.of(Fluids.WATER, 1), 5000).chancedOutputLogic(FluidRecipeCapability.CAP, ChanceLogic.NONE.value()).build();
        assertTrue(RecipeRunnerHelper.handleRecipe(IO.OUT, holder, recipe.outputs, Map.of(), false, recipe, false).isSuccess());
        assertEquals(0, tank.getStorages()[0].getFluidAmount());
        assertTrue(IParallelLogic.getRecipeOutputChance(holder, recipe).outputs.isEmpty());
    }
    @Test void chanceOutputLimitIncludesExistingParallelRunsAndLeavesTheChanceCacheUntouched() {
        var holder = new CoreHolder(); var tank = fluids(holder, IO.OUT, 1100, 0);
        holder.outputs.add(new RecipeHandlePart(List.of(tank)));
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("parallel_chance_capacity")
                .chancedOutput(SizedFluidIngredient.of(Fluids.WATER, 100), 5000).build();
        IGTRecipe.of(recipe).setRealParallels(10);
        var content = recipe.outputs.get(FluidRecipeCapability.CAP).getFirst();
        var cache = holder.getRecipeLogic().getChanceCaches().get(FluidRecipeCapability.CAP);
        org.gtlcore.gtlcore.api.recipe.chance.LongChanceLogic.updateCachedChance(content.content(), cache, 0);
        assertEquals(2, IParallelLogic.getMinParallel(holder, recipe, 20));
        assertEquals(0, cache.getInt(content.content())); assertEquals(10, IGTRecipe.of(recipe).getRealParallels());
        assertEquals(100, LongFluidIngredient.amount(FluidRecipeCapability.CAP.of(content.content())));
        assertEquals(0, tank.getStorages()[0].getFluidAmount());
    }

    @Test void disabledChanceOutputsDoNotReserveCapacityOrAdvanceCaches() {
        var holder = new CoreHolder(); holder.outputs.add(new RecipeHandlePart(List.of(fluids(holder, IO.OUT, 1, 0))));
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("parallel_none_capacity")
                .chancedOutput(SizedFluidIngredient.of(Fluids.WATER, 100), 5000)
                .chancedOutputLogic(FluidRecipeCapability.CAP, ChanceLogic.NONE.value()).build();
        IGTRecipe.of(recipe).setRealParallels(10);
        assertEquals(20, IParallelLogic.getMinParallel(holder, recipe, 20));
    }

}
