package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;
import org.gtlcore.gtlcore.api.machine.trait.MEPatternRecipeHandlePart;
import org.gtlcore.gtlcore.api.machine.trait.RecipeHandlePart;
import org.gtlcore.gtlcore.api.recipe.*;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.lookup.RecipeDB;
import com.gregtechceu.gtceu.api.recipe.lookup.StagingRecipeDB;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.StorageMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.Test;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;

/** Uses native database staging, ingredient conversion, GT traits and strict production search Mixin. Holder routing is a fixture. */
class NativeGroupedRecipeSearchIntegrationTest {
    private static StorageMachine owner() {
        var definition = GTMultiMachines.VACUUM_FREEZER;
        return new StorageMachine(new BlockEntityCreationInfo(definition.getBlockEntityType(), BlockPos.ZERO,
                definition.getBlock().defaultBlockState()), 64);
    }
    private static RecipeHandlePart items(ItemStack... stacks) {
        var handler = owner().attachTrait(new NotifiableItemStackHandler(stacks.length, IO.IN, IO.IN));
        for (int i = 0; i < stacks.length; i++) handler.storage.setStackInSlot(i, stacks[i]);
        return new RecipeHandlePart(List.of(handler));
    }
    private static RecipeHandlePart water() {
        var handler = owner().attachTrait(new NotifiableFluidTank(1, 1000, IO.IN, IO.IN));
        handler.getStorages()[0].setFluid(new FluidStack(Fluids.WATER, 1000)); return new RecipeHandlePart(List.of(handler));
    }
    private static IRecipeCapabilityHolder holder(boolean distinct, List<RecipeHandlePart> groups,
                                                   RecipeHandlePart shared, List<MEPatternRecipeHandlePart> patterns) {
        return holder(distinct, groups, shared, patterns, null);
    }
    private static IRecipeCapabilityHolder holder(boolean distinct, List<RecipeHandlePart> groups,
            RecipeHandlePart shared, List<MEPatternRecipeHandlePart> patterns,
            com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic logic) {
        return (IRecipeCapabilityHolder) Proxy.newProxyInstance(NativeGroupedRecipeSearchIntegrationTest.class.getClassLoader(),
                logic == null ? new Class<?>[]{IRecipeCapabilityHolder.class, IRecipeCapabilityMachine.class} :
                        new Class<?>[]{IRecipeCapabilityHolder.class, IRecipeCapabilityMachine.class, com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine.class}, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "getNormalRecipeHandlePart" -> args[0] == IO.IN ? groups : List.of();
                case "getSharedRecipeHandlePart" -> shared;
                case "getMEPatternRecipeHandleParts" -> patterns;
                case "getMEOutputRecipeHandleParts" -> List.of();
                case "isDistinct" -> distinct;
                case "emptyHandlePart", "emptyRecipeHandlePart" -> groups.isEmpty() && shared == null && patterns.isEmpty();
                case "getCapabilitiesProxy", "getCapabilitiesFlat" -> Map.of();
                case "getRecipeLogic" -> logic;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> "Scoped native recipe routing fixture";
                default -> {
                    if (method.isDefault()) yield InvocationHandler.invokeDefault(proxy, method, args);
                    throw new UnsupportedOperationException(method.toString());
                }
            };
        });
    }
    private static final class MutableMode extends com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic implements IMultipleRecipeSearch {
        boolean multiple;
        @Override public boolean isMultipleRecipeMode() { return multiple; }
    }
    private static GTRecipe recipe(String id, net.minecraft.world.level.ItemLike... inputs) {
        var builder = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_grouped_" + id).duration(20);
        for (var item : inputs) builder.inputItems(item); return builder.build();
    }
    private static RecipeDB db(GTRecipe... recipes) {
        var staging = new StagingRecipeDB(); for (var recipe : recipes) staging.add(recipe);
        var db = new RecipeDB(); staging.populateDB(db); return db;
    }
    private static NativeGroupedRecipeIterator search(RecipeDB db, IRecipeCapabilityHolder holder, Predicate<GTRecipe> predicate) {
        return IRecipeIterator.search(GTRecipeTypes.CHEMICAL_RECIPES.value(), db, holder, (IRecipeCapabilityMachine) holder, predicate);
    }
    private static List<GTRecipe> all(Iterator<GTRecipe> iterator) { var list = new ArrayList<GTRecipe>(); iterator.forEachRemaining(list::add); return list; }
    @Test void distinctInputsCannotCombineDifferentBusesButColocatedInputsFindTheNativeRecipe() {
        var recipe = recipe("separate", Items.IRON_INGOT, Items.GOLD_INGOT); var database = db(recipe);
        var separated = holder(true, List.of(items(new ItemStack(Items.IRON_INGOT)), items(new ItemStack(Items.GOLD_INGOT))), null, List.of());
        assertFalse(search(database, separated, r -> true).hasNext());
        var combined = holder(true, List.of(items(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT))), null, List.of());
        assertEquals(List.of(recipe), all(search(database, combined, r -> true)));
    }
    @Test void sharedFluidsJoinEachItemBusOnlyInTheSharedMode() {
        // Unit registries do not load datapack fluid tags. Use the native single-fluid ingredient here.
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_grouped_shared_water").inputItems(Items.IRON_INGOT)
                .inputFluids(new net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient(
                        net.neoforged.neoforge.fluids.crafting.FluidIngredient.single(Fluids.WATER), 100)).duration(20).build();
        var database = db(recipe); var bus = items(new ItemStack(Items.IRON_INGOT)); var shared = water();
        var sharedHolder = holder(false, List.of(bus), shared, List.of());
        assertEquals(List.of(recipe), all(search(database, sharedHolder, r -> true)),
                () -> "Groups: " + IRecipeIterator.inputGroups((IRecipeCapabilityMachine) sharedHolder) + "; Fluid: " + shared.getCapability(FluidRecipeCapability.CAP).getFirst().getContents() + "; Recipe: " + recipe.inputs);
        assertFalse(search(database, holder(true, List.of(bus, shared), shared, List.of()), r -> true).hasNext());
    }
    @Test void controllerGhostCircuitSupplementsEachDistinctBusWithoutMergingPhysicalItems() {
        var circuit = owner().attachTrait(new com.gregtechceu.gtceu.common.machine.trait.ProgrammableCircuitSlotTrait());
        circuit.setCurrentCircuit(17); var shared = new RecipeHandlePart(List.of(circuit));
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_grouped_circuit").inputItems(Items.IRON_INGOT).circuitMeta(17).duration(20).build();
        var holder = holder(true, List.of(items(new ItemStack(Items.IRON_INGOT)), shared), shared, List.of());
        assertEquals(List.of(recipe), all(search(db(recipe), holder, r -> true)));
        circuit.setCurrentCircuit(16); assertFalse(search(db(recipe), holder, r -> true).hasNext());
    }
    @Test void multiRecipeModeFindsEveryNativeCandidateWhileSingleModeKeepsOnePerGroup() {
        var first = recipe("first", Items.IRON_INGOT); var second = recipe("second", Items.GOLD_INGOT); var database = db(first, second);
        var holder = holder(true, List.of(items(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT))), null, List.of());
        assertEquals(1, all(search(database, holder, r -> true)).size());
        var iterator = search(database, holder, r -> true); iterator.setUseDiveIngredientTreeFind(true);
        assertEquals(Set.of(first, second), new HashSet<>(all(iterator)));
    }
    @Test void overlappingGroupsAndMeCachesEmitEachRecipeOnceAndResetClearsEveryCursor() {
        var recipe = recipe("deduplicated", Items.IRON_INGOT); var holder = holder(true, List.of(items(new ItemStack(Items.IRON_INGOT)), items(new ItemStack(Items.IRON_INGOT))), null, List.of());
        var iterator = search(db(recipe), holder, r -> true); iterator.setAdditionalRecipes(List.of(recipe, recipe.copy()));
        assertTrue(iterator.hasNext()); assertTrue(iterator.hasNext()); assertEquals(recipe, iterator.next()); assertFalse(iterator.hasNext()); assertThrows(NoSuchElementException.class, iterator::next);
        iterator.reset(); assertEquals(List.of(recipe), all(iterator));
    }
    @Test void cachedOnlyPatternsStillHonorRecipeTypeAndTheHandlingPredicate() {
        var fixture = MEPatternRecipeHandleIntegrationTest.fixture(); var accepted = recipe("accepted_cache", Items.IRON_INGOT); var rejected = recipe("rejected_cache", Items.GOLD_INGOT);
        var wrongType = GTRecipeTypes.VACUUM_RECIPES.recipeBuilder("native_grouped_wrong_type").inputItems(Items.IRON_INGOT).duration(20).build();
        fixture.trait().setSlotCacheRecipe(0, rejected); fixture.trait().setSlotCacheRecipe(1, wrongType); fixture.trait().setSlotCacheRecipe(2, accepted);
        var holder = holder(true, List.of(), null, List.of(fixture.part())); var calls = new AtomicInteger();
        var iterator = search(db(), holder, r -> { calls.incrementAndGet(); return r == accepted; });
        assertEquals(List.of(accepted), all(iterator)); assertEquals(2, calls.get());
    }
    @Test void uncachedMeSlotsCannotBorrowItemInputsFromAnotherPatternSlot() {
        var recipe = recipe("separate_me_slots", Items.IRON_INGOT, Items.GOLD_INGOT); var fixture = MEPatternRecipeHandleIntegrationTest.fixture();
        fixture.items().stock(0, new ItemStack(Items.IRON_INGOT), 1).stock(1, new ItemStack(Items.GOLD_INGOT), 1);
        var holder = holder(true, List.of(), null, List.of(fixture.part())); assertFalse(search(db(recipe), holder, r -> true).hasNext());
        fixture.items().stock(0, new ItemStack(Items.GOLD_INGOT), 1); assertEquals(List.of(recipe), all(search(db(recipe), holder, r -> true)));
    }
    @Test void theActualNativeSearchEntryPointKeepsIndependentHoldersAndPredicates() {
        var first = recipe("native_hook_first", Items.IRON_INGOT); var second = recipe("native_hook_second", Items.GOLD_INGOT); var type = first.recipeType;
        var staging = type.getAdditionHandler(); staging.beginStaging(); staging.addStaging(first); staging.addStaging(second); staging.completeStaging();
        var iron = holder(true, List.of(items(new ItemStack(Items.IRON_INGOT))), null, List.of());
        var gold = holder(true, List.of(items(new ItemStack(Items.GOLD_INGOT))), null, List.of());
        var ironSearch = type.searchRecipe(iron, r -> r == first); var goldSearch = type.searchRecipe(gold, r -> r == second);
        assertEquals(List.of(second), all(goldSearch)); assertEquals(List.of(first), all(ironSearch)); assertFalse(type.searchRecipe(iron, r -> false).hasNext());
    }
    @Test void customLogicFallbackRunsOnceOnlyWhenNoDatabaseOrCacheCandidateExists() {
        var recipe = recipe("fallback", Items.IRON_INGOT); var calls = new AtomicInteger();
        var empty = new NativeGroupedRecipeIterator(recipe.recipeType, db(), List.of(), r -> true, () -> { calls.incrementAndGet(); return recipe; });
        assertTrue(empty.hasNext()); assertTrue(empty.hasNext()); assertEquals(List.of(recipe), all(empty)); assertEquals(1, calls.get());
        var cached = new NativeGroupedRecipeIterator(recipe.recipeType, db(), List.of(), r -> true, () -> { fail("Custom logic ran after a valid cached recipe"); return null; });
        cached.setAdditionalRecipes(List.of(recipe)); assertEquals(List.of(recipe), all(cached));
    }
    @Test void additionsNativeWrapComposesMutableModeWithCoreGroupedSearch() {
        var first = recipe("additions_mode_first", Items.IRON_INGOT); var second = recipe("additions_mode_second", Items.GOLD_INGOT); var type = first.recipeType;
        var staging = type.getAdditionHandler(); staging.beginStaging(); staging.addStaging(first); staging.addStaging(second); staging.completeStaging();
        var logic = new MutableMode(); var holder = holder(true, List.of(items(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT))), null, List.of(), logic);
        assertEquals(1, all(type.searchRecipe(holder, r -> true)).size());
        logic.multiple = true; assertEquals(Set.of(first, second), new HashSet<>(all(type.searchRecipe(holder, r -> true))));
        assertFalse(type.searchRecipe(holder, r -> false).hasNext());
    }
    @Test void overlappingPartialFluidPredicatesFindEveryNativeCandidateWithAdditionalQueryComponents() {
        var named = new FluidStack(Fluids.WATER, 1); named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("component index name"));
        var data = new FluidStack(Fluids.WATER, 1); var tag = new net.minecraft.nbt.CompoundTag(); tag.putString("purity", "high");
        data.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
        var firstPredicate = com.gregtechceu.gtceu.api.recipe.ingredient.ExDataComponentFluidIngredient.of(false, named.getComponents(), net.minecraft.core.HolderSet.direct(named.getFluidHolder()));
        var secondPredicate = com.gregtechceu.gtceu.api.recipe.ingredient.ExDataComponentFluidIngredient.of(false, data.getComponents(), net.minecraft.core.HolderSet.direct(data.getFluidHolder()));
        var first = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_grouped_partial_name").inputFluids(new net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient(firstPredicate, 1)).duration(20).build();
        var second = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_grouped_partial_data").inputFluids(new net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient(secondPredicate, 1)).duration(20).build();
        var strictPredicate = com.gregtechceu.gtceu.api.recipe.ingredient.ExDataComponentFluidIngredient.of(true, named.getComponents(), net.minecraft.core.HolderSet.direct(named.getFluidHolder()));
        var strict = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_grouped_strict_name").inputFluids(new net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient(strictPredicate, 1)).duration(20).build();
        var tank = owner().attachTrait(new NotifiableFluidTank(1, 1000, IO.IN, IO.IN)); var query = named.copyWithAmount(1000); query.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, data.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA)); tank.getStorages()[0].setFluid(query);
        var holder = holder(true, List.of(new RecipeHandlePart(List.of(tank))), null, List.of());
        var iterator = search(db(first, second, strict), holder, r -> true); iterator.setUseDiveIngredientTreeFind(true);
        assertEquals(Set.of(first, second), new HashSet<>(all(iterator)));
        tank.getStorages()[0].setFluid(named.copyWithAmount(1000)); iterator = search(db(first, second, strict), holder, r -> true); iterator.setUseDiveIngredientTreeFind(true);
        assertEquals(Set.of(first, strict), new HashSet<>(all(iterator)));
        tank.getStorages()[0].setFluid(new FluidStack(Fluids.WATER, 1000)); assertFalse(search(db(first, second), holder, r -> true).hasNext());
    }
    @Test void longComponentIngredientsUseTheirMatchingPredicateInTheActualNativeDatabase() {
        var named = new ItemStack(Items.IRON_INGOT); named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("long search item"));
        var fluid = new FluidStack(Fluids.WATER, 1000); fluid.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("long search fluid"));
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_grouped_long_components")
                .inputItems(org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient.create(named, 9_007_199_254_740_993L))
                .inputFluids(org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient.create(fluid, 9_007_199_254_741_000L)).duration(20).build();
        var itemHandler = owner().attachTrait(new NotifiableItemStackHandler(1, IO.IN, IO.IN)); itemHandler.storage.setStackInSlot(0, named);
        var tank = owner().attachTrait(new NotifiableFluidTank(1, 1000, IO.IN, IO.IN)); tank.getStorages()[0].setFluid(fluid);
        var holder = holder(true, List.of(new RecipeHandlePart(List.of(itemHandler, tank))), null, List.of());
        assertEquals(List.of(recipe), all(search(db(recipe), holder, r -> true)));
        tank.getStorages()[0].setFluid(new FluidStack(Fluids.WATER, 1000)); assertFalse(search(db(recipe), holder, r -> true).hasNext());
    }
}
