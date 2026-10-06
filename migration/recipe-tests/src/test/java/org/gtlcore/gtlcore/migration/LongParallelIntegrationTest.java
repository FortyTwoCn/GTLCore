package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.machine.multiblock.ParallelMachine;
import org.gtlcore.gtlcore.api.machine.trait.LongStorageAdapterRegistry;
import org.gtlcore.gtlcore.api.recipe.IAdvancedContentModifier;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.StorageMachine;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeHandlerList;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Calls native GT capability methods with production mixins and native input/output traits. */
class LongParallelIntegrationTest {
    private static NotifiableItemStackHandler handler(IO io, ItemStack... stacks) {
        var definition = GTMultiMachines.VACUUM_FREEZER;
        var owner = new StorageMachine(new BlockEntityCreationInfo(definition.getBlockEntityType(), BlockPos.ZERO,
                definition.getBlock().defaultBlockState()), 64);
        var handler = owner.attachTrait(new NotifiableItemStackHandler(stacks.length, io, io));
        for (int i = 0; i < stacks.length; i++) handler.storage.setStackInSlot(i, stacks[i]);
        return handler;
    }

    private static class Holder implements IRecipeCapabilityHolder, ParallelMachine {
        private final Map<IO, List<RecipeHandlerList>> proxies = new EnumMap<>(IO.class);
        private final Map<IO, Map<RecipeCapability<?>, List<IRecipeHandler<?>>>> flat = new EnumMap<>(IO.class);
        @Override public Map<IO, List<RecipeHandlerList>> getCapabilitiesProxy() { return proxies; }
        @Override public Map<IO, Map<RecipeCapability<?>, List<IRecipeHandler<?>>>> getCapabilitiesFlat() { return flat; }
        @Override public int getMaxParallel() { return Integer.MAX_VALUE; }
        Holder add(RecipeHandlerList list) { addHandlerList(list); return this; }
    }

    private static GTRecipe input(SizedIngredient... ingredients) {
        return GTRecipeTypes.VACUUM_RECIPES.recipeBuilder("long_parallel_input").inputItems(ingredients).build();
    }

    @Test
    void primitiveModifiersAndItemCapabilityPreserveExactFractionsAboveTwoToThe53() {
        long value = 9_007_199_254_740_993L;
        assertEquals(value + 1, ContentModifier.addition(1).apply(value));
        var third = IAdvancedContentModifier.preciseDivision(1, 3);
        assertEquals(value / 3, third.apply(value));
        assertEquals(3, third.apply(10));
        var scaled = ItemRecipeCapability.CAP.copyWithModifier(
                LongIngredient.create(Ingredient.of(Items.IRON_INGOT), value), third);
        assertEquals(value / 3, LongIngredient.amount(scaled));
        assertEquals(3, LongIngredient.amount(ItemRecipeCapability.CAP.copyWithModifier(
                SizedIngredient.of(Items.IRON_INGOT, 10), third)));
    }

    @Test
    void integerOverflowSaturatesAndInvalidFractionsAreRejected() {
        var multiplier = IAdvancedContentModifier.preciseMultiplier(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, multiplier.apply(2L));
        assertEquals(Long.MIN_VALUE, multiplier.apply(-2L));
        assertEquals(Integer.MAX_VALUE, multiplier.apply(2));
        assertThrows(IllegalArgumentException.class, () -> IAdvancedContentModifier.preciseDivision(1, 0));
    }

    @Test
    void longBackedInputUsesItsRealQuantityAndDoesNotTruncateTheIngredient() {
        var input = handler(IO.IN, new ItemStack(Items.IRON_INGOT, 1));
        LongStorageAdapterRegistry.attachItemStorage(input.storage, slot -> 6_000_000_017L);
        var holder = new Holder().add(RecipeHandlerList.of(IO.IN, input));
        var recipe = input(LongIngredient.create(Ingredient.of(Items.IRON_INGOT), 3_000_000_000L));
        assertEquals(2, ItemRecipeCapability.CAP.getMaxParallelByInput(holder, recipe, Integer.MAX_VALUE, false));
        assertEquals(3_000_000_000L, LongIngredient.amount(ItemRecipeCapability.CAP.of(
                recipe.getInputContents(ItemRecipeCapability.CAP).getFirst().content())));
        assertEquals(1, input.storage.getStackInSlot(0).getCount());
    }

    @Test
    void separateDistinctBusesDoNotContributeToTheSameIngredient() {
        var first = RecipeHandlerList.of(IO.IN, handler(IO.IN, new ItemStack(Items.IRON_INGOT, 64)));
        var second = RecipeHandlerList.of(IO.IN, handler(IO.IN, new ItemStack(Items.IRON_INGOT, 64)));
        first.setDistinct(true);
        second.setDistinct(true);
        var holder = new Holder().add(first).add(second);
        var recipe = input(LongIngredient.create(Ingredient.of(Items.IRON_INGOT), 100));
        assertEquals(0, ItemRecipeCapability.CAP.getMaxParallelByInput(holder, recipe, 4, false));
        first.setDistinct(false);
        second.setDistinct(false);
        assertEquals(1, ItemRecipeCapability.CAP.getMaxParallelByInput(holder, recipe, 4, false));
    }

    @Test
    void coloredBusesCanUseUndyedInputButCannotMergeDifferentColors() {
        var shared = RecipeHandlerList.of(IO.IN, handler(IO.IN, new ItemStack(Items.IRON_INGOT, 64)));
        var red = RecipeHandlerList.of(IO.IN, 14, handler(IO.IN, new ItemStack(Items.DIAMOND, 32)));
        var blue = RecipeHandlerList.of(IO.IN, 11, handler(IO.IN, new ItemStack(Items.DIAMOND, 32)));
        var holder = new Holder().add(red).add(shared).add(blue);
        var impossible = input(LongIngredient.create(Ingredient.of(Items.IRON_INGOT), 10),
                LongIngredient.create(Ingredient.of(Items.DIAMOND), 50));
        assertEquals(0, ItemRecipeCapability.CAP.getMaxParallelByInput(holder, impossible, 4, false));
        var possible = input(LongIngredient.create(Ingredient.of(Items.IRON_INGOT), 10),
                LongIngredient.create(Ingredient.of(Items.DIAMOND), 16));
        assertEquals(2, ItemRecipeCapability.CAP.getMaxParallelByInput(holder, possible, 4, false));
    }

    @Test
    void overlappingRequirementsCannotSpendTheSameItemsTwice() {
        var source = handler(IO.IN, new ItemStack(Items.IRON_INGOT, 64));
        var holder = new Holder().add(RecipeHandlerList.of(IO.IN, source));
        var recipe = input(LongIngredient.create(Ingredient.of(Items.IRON_INGOT), 32),
                LongIngredient.create(Ingredient.of(Items.IRON_INGOT), 32));
        assertEquals(1, ItemRecipeCapability.CAP.getMaxParallelByInput(holder, recipe, 8, false));
        assertEquals(64, source.storage.getStackInSlot(0).getCount());
    }

    @Test
    void nonConsumablesAreReservedOnceAndComponentsAreMatchedStrictly() {
        var named = new ItemStack(Items.IRON_INGOT, 4);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("catalyst"));
        var source = handler(IO.IN, named, new ItemStack(Items.IRON_INGOT, 60));
        var holder = new Holder().add(RecipeHandlerList.of(IO.IN, source));
        var recipe = GTRecipeTypes.VACUUM_RECIPES.recipeBuilder("long_parallel_catalyst")
                .notConsumable(named)
                .inputItems(LongIngredient.create(Ingredient.of(Items.IRON_INGOT), 10)).build();
        assertEquals(6, ItemRecipeCapability.CAP.getMaxParallelByInput(holder, recipe, 100, false));
        source.storage.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 4));
        assertEquals(0, ItemRecipeCapability.CAP.getMaxParallelByInput(holder, recipe, 100, false));
    }

    @Test
    void ordinaryOutputSlotsRejectOversizedOutputsWithoutMutation() {
        var output = handler(IO.OUT, ItemStack.EMPTY, ItemStack.EMPTY);
        var holder = new Holder().add(RecipeHandlerList.of(IO.OUT, output));
        var recipe = GTRecipeTypes.VACUUM_RECIPES.recipeBuilder("long_parallel_output")
                .outputItems(LongIngredient.create(Ingredient.of(Items.DIAMOND), 100)).build();
        assertEquals(1, ItemRecipeCapability.CAP.limitMaxParallelByOutput(holder, recipe, Integer.MAX_VALUE, false));
        assertTrue(output.storage.getStackInSlot(0).isEmpty());
        var impossible = GTRecipeTypes.VACUUM_RECIPES.recipeBuilder("long_parallel_impossible")
                .outputItems(LongIngredient.create(Ingredient.of(Items.DIAMOND), 3_000_000_000L)).build();
        assertEquals(0, ItemRecipeCapability.CAP.limitMaxParallelByOutput(holder, impossible, 5, false));
    }

    @Test
    void tickInputsUseTheTickMapAndRespectTheRequestedLimit() {
        var source = handler(IO.IN, new ItemStack(Items.IRON_INGOT, 64));
        var holder = new Holder().add(RecipeHandlerList.of(IO.IN, source));
        var recipe = GTRecipeTypes.VACUUM_RECIPES.recipeBuilder("long_parallel_tick")
                .perTick(true).inputItems(LongIngredient.create(Ingredient.of(Items.IRON_INGOT), 16)).build();
        assertEquals(3, ItemRecipeCapability.CAP.getMaxParallelByInput(holder, recipe, 3, true));
        assertEquals(20, ItemRecipeCapability.CAP.getMaxParallelByInput(holder, recipe, 20, false));
    }
}
