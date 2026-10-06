package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.machine.trait.*;
import org.gtlcore.gtlcore.api.recipe.*;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.StorageMachine;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.feature.IOverclockMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeHandlerList;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import it.unimi.dsi.fastutil.objects.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Core dispatch/cache policy over actual native GT inventories; ME fixtures are bounded slots, not an AE grid. */
class NativeCoreRecipeRunnerIntegrationTest {
    private static BlockEntityCreationInfo info() {
        var definition = GTMultiMachines.VACUUM_FREEZER;
        return new BlockEntityCreationInfo(definition.getBlockEntityType(), BlockPos.ZERO, definition.getBlock().defaultBlockState());
    }
    static class NativeHolder extends StorageMachine implements IOverclockMachine {
        NativeHolder() { super(info(), 64); }
        final Map<IO, List<RecipeHandlerList>> proxies = new EnumMap<>(IO.class);
        final Map<IO, Map<RecipeCapability<?>, List<IRecipeHandler<?>>>> flat = new EnumMap<>(IO.class);
        @Override public Map<IO, List<RecipeHandlerList>> getCapabilitiesProxy() { return proxies; }
        @Override public Map<IO, Map<RecipeCapability<?>, List<IRecipeHandler<?>>>> getCapabilitiesFlat() { return flat; }
        @Override public int getOverclockTier() { return 1; }
        @Override public void setOverclockTier(int tier) {}
        @Override public int getMinOverclockTier() { return 1; }
        @Override public int getMaxOverclockTier() { return 1; }
    }
    static class CoreHolder extends NativeHolder implements IRecipeCapabilityMachine {
        final List<RecipeHandlePart> inputs = new ArrayList<>(), outputs = new ArrayList<>();
        final List<MEPatternRecipeHandlePart> patterns = new ArrayList<>();
        final List<MEIORecipeHandlePart<?>> meOutputs = new ArrayList<>();
        final IdentityHashMap<GTRecipe, ReferenceLinkedOpenHashSet<IRecipeHandlePart>> cached = new IdentityHashMap<>();
        final IdentityHashMap<GTRecipe, IRecipeHandlePart> active = new IdentityHashMap<>();
        RecipeHandlePart shared;
        boolean distinct = true;
        @Override public List<RecipeHandlePart> getNormalRecipeHandlePart(IO io) { return io == IO.IN ? inputs : outputs; }
        @Override public RecipeHandlePart getSharedRecipeHandlePart() { return shared; }
        @Override public boolean emptyRecipeHandlePart() { return inputs.isEmpty() && outputs.isEmpty() && shared == null; }
        @Override public boolean emptyHandlePart() { return emptyRecipeHandlePart() && patterns.isEmpty() && meOutputs.isEmpty(); }
        @Override public boolean usesCoreRecipeRouting() { return !emptyHandlePart(); }
        @Override public List<MEPatternRecipeHandlePart> getMEPatternRecipeHandleParts() { return patterns; }
        @Override public List<MEIORecipeHandlePart<?>> getMEOutputRecipeHandleParts() { return meOutputs; }
        @Override public IRecipeHandlePart getActiveRecipeHandle(GTRecipe recipe) { return active.get(recipe); }
        @Override public ReferenceSet<IRecipeHandlePart> getAllCachedRecipeHandles(GTRecipe recipe) {
            return cached.getOrDefault(recipe, new ReferenceLinkedOpenHashSet<>());
        }
        @Override public Iterator<IRecipeHandlePart> getAllCachedRecipeHandlesIter(GTRecipe recipe) {
            var ordered = new ReferenceLinkedOpenHashSet<IRecipeHandlePart>();
            if (active.containsKey(recipe)) ordered.add(active.get(recipe));
            ordered.addAll(getAllCachedRecipeHandles(recipe)); return ordered.iterator();
        }
        @Override public void tryAddAndActiveRhp(GTRecipe recipe, IRecipeHandlePart part) {
            cached.computeIfAbsent(recipe, $ -> new ReferenceLinkedOpenHashSet<>()).add(part); active.put(recipe, part);
        }
        @Override public void tryAddAndActiveMERhp(MEPatternRecipeHandlePart part, GTRecipe recipe, int slot) {
            part.setLastRecipe2Slot(recipe, slot); tryAddAndActiveRhp(recipe, part);
        }
        @Override public void sortMEOutput() { meOutputs.sort(MEIORecipeHandlePart.COMPARATOR); }
        @Override public void upDate() {}
        @Override public boolean isDistinct() { return distinct; }
        @Override public void setDistinct(boolean value) { distinct = value; }
    }
    static NotifiableItemStackHandler items(NativeHolder holder, IO io, ItemStack... stacks) {
        var handler = holder.attachTrait(new NotifiableItemStackHandler(stacks.length, io, io));
        for (int i = 0; i < stacks.length; i++) handler.storage.setStackInSlot(i, stacks[i]);
        return handler;
    }
    static NotifiableFluidTank fluids(NativeHolder holder, IO io, int capacity, int amount) {
        var tank = holder.attachTrait(new NotifiableFluidTank(1, capacity, io, io));
        if (amount > 0) tank.getStorages()[0].setFluid(new FluidStack(Fluids.WATER, amount)); return tank;
    }
    private static GTRecipe itemRecipe(int count) {
        return GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("core_input_plan").inputItems(Items.IRON_INGOT, count).EUt(32).build();
    }
    private static RecipeResult input(CoreHolder holder, GTRecipe recipe, boolean simulate, RecipeCacheStrategy strategy) {
        return RecipeRunnerHelper.handleRecipe(IO.IN, holder, recipe.inputs, Map.of(), false, recipe, simulate, strategy);
    }

    @Test void aFailedCachedDistinctBusDoesNotSpendMaterialsBeforeTheNextBusIsSelected() {
        var holder = new CoreHolder(); var recipe = itemRecipe(5);
        var first = items(holder, IO.IN, new ItemStack(Items.IRON_INGOT, 3));
        var second = items(holder, IO.IN, new ItemStack(Items.IRON_INGOT, 8));
        var firstPart = new RecipeHandlePart(List.of(first)); var secondPart = new RecipeHandlePart(List.of(second));
        holder.inputs.addAll(List.of(firstPart, secondPart)); holder.tryAddAndActiveRhp(recipe, firstPart);
        assertTrue(input(holder, recipe, false, RecipeCacheStrategy.FULL_CACHE).isSuccess());
        assertEquals(3, first.getStackInSlot(0).getCount()); assertEquals(3, second.getStackInSlot(0).getCount());
        assertSame(secondPart, holder.getActiveRecipeHandle(recipe));
    }
    @Test void distinctNormalBusesCannotCombineTheirInventoriesAndFailedExecutionDoesNotConsume() {
        var holder = new CoreHolder(); var recipe = itemRecipe(5);
        var first = items(holder, IO.IN, new ItemStack(Items.IRON_INGOT, 3));
        var second = items(holder, IO.IN, new ItemStack(Items.IRON_INGOT, 3));
        holder.inputs.addAll(List.of(new RecipeHandlePart(List.of(first)), new RecipeHandlePart(List.of(second))));
        assertFalse(input(holder, recipe, false, RecipeCacheStrategy.FULL_CACHE).isSuccess());
        assertEquals(3, first.getStackInSlot(0).getCount()); assertEquals(3, second.getStackInSlot(0).getCount());
        assertTrue(holder.cached.isEmpty());
    }
    @Test void sharedFluidIsConsumedOnceAfterAnIndependentItemBusHasBeenSelected() {
        var holder = new CoreHolder(); holder.distinct = false;
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("core_shared_fluid")
                .inputItems(Items.IRON_INGOT, 5).inputFluids(SizedFluidIngredient.of(Fluids.WATER, 300)).build();
        var first = items(holder, IO.IN, new ItemStack(Items.IRON_INGOT, 3));
        var second = items(holder, IO.IN, new ItemStack(Items.IRON_INGOT, 8)); var tank = fluids(holder, IO.IN, 1000, 800);
        var firstPart = new RecipeHandlePart(List.of(first)); var secondPart = new RecipeHandlePart(List.of(second));
        holder.inputs.addAll(List.of(firstPart, secondPart)); holder.shared = new RecipeHandlePart(List.of(tank));
        holder.tryAddAndActiveRhp(recipe, firstPart);
        assertTrue(input(holder, recipe, false, RecipeCacheStrategy.FULL_CACHE).isSuccess());
        assertEquals(500, tank.getStorages()[0].getFluidAmount());
        assertEquals(3, first.getStackInSlot(0).getCount()); assertEquals(3, second.getStackInSlot(0).getCount());
    }
    @Test void aMissingItemDoesNotDrainSharedFluidEvenInNoCacheMode() {
        var holder = new CoreHolder(); holder.distinct = false;
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("core_failed_shared_fluid")
                .inputItems(Items.IRON_INGOT, 5).inputFluids(SizedFluidIngredient.of(Fluids.WATER, 300)).build();
        var inventory = items(holder, IO.IN, new ItemStack(Items.IRON_INGOT, 3)); var tank = fluids(holder, IO.IN, 1000, 800);
        holder.inputs.add(new RecipeHandlePart(List.of(inventory))); holder.shared = new RecipeHandlePart(List.of(tank));
        assertFalse(input(holder, recipe, false, RecipeCacheStrategy.NO_CACHE).isSuccess());
        assertEquals(800, tank.getStorages()[0].getFluidAmount()); assertEquals(3, inventory.getStackInSlot(0).getCount());
        assertTrue(holder.cached.isEmpty());
    }
    @Test void consumedInputsStayInTheBusThatAlsoMatchedTheNonConsumedCatalyst() {
        var holder = new CoreHolder();
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("core_catalyst_plan")
                .inputItems(Items.IRON_INGOT, 2).notConsumable(Items.DIAMOND).build();
        var first = items(holder, IO.IN, new ItemStack(Items.IRON_INGOT, 9));
        var second = items(holder, IO.IN, new ItemStack(Items.IRON_INGOT, 9), new ItemStack(Items.DIAMOND));
        holder.inputs.addAll(List.of(new RecipeHandlePart(List.of(first)), new RecipeHandlePart(List.of(second))));
        assertTrue(input(holder, recipe, false, RecipeCacheStrategy.FULL_CACHE).isSuccess());
        assertEquals(9, first.getStackInSlot(0).getCount()); assertEquals(7, second.getStackInSlot(0).getCount());
        assertEquals(1, second.getStackInSlot(1).getCount());
    }
    @Test void executionStillRequiresANonConsumedIngredientWhenItIsTheOnlyInput() {
        var holder = new CoreHolder();
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("core_nc_only").notConsumable(Items.DIAMOND).build();
        var inventory = items(holder, IO.IN, new ItemStack(Items.IRON_INGOT, 9)); holder.inputs.add(new RecipeHandlePart(List.of(inventory)));
        assertFalse(input(holder, recipe, false, RecipeCacheStrategy.FULL_CACHE).isSuccess());
        assertEquals(9, inventory.getStackInSlot(0).getCount());
    }
    @Test void noCacheModeSkipsActiveHandlerPreferenceAndDoesNotWriteTheRecipeCache() {
        var holder = new CoreHolder(); var recipe = itemRecipe(2);
        var first = items(holder, IO.IN, new ItemStack(Items.IRON_INGOT, 9));
        var second = items(holder, IO.IN, new ItemStack(Items.IRON_INGOT, 9));
        var firstPart = new RecipeHandlePart(List.of(first)); var secondPart = new RecipeHandlePart(List.of(second));
        holder.inputs.addAll(List.of(firstPart, secondPart)); holder.tryAddAndActiveRhp(recipe, secondPart);
        assertTrue(input(holder, recipe, false, RecipeCacheStrategy.NO_CACHE).isSuccess());
        assertEquals(7, first.getStackInSlot(0).getCount()); assertEquals(9, second.getStackInSlot(0).getCount());
        assertSame(secondPart, holder.getActiveRecipeHandle(recipe)); assertEquals(1, holder.getAllCachedRecipeHandles(recipe).size());
    }
    @Test void missingInputDoesNotAdvanceItsChanceCache() {
        var holder = new CoreHolder(); var inventory = items(holder, IO.IN, new ItemStack(Items.DIAMOND));
        holder.inputs.add(new RecipeHandlePart(List.of(inventory)));
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("core_failed_chance")
                .chancedInput(new ItemStack(Items.IRON_INGOT), 5000).build();
        var content = recipe.inputs.get(ItemRecipeCapability.CAP).getFirst(); var cache = new Object2IntOpenHashMap<Object>();
        cache.put(content.content(), 0);
        var result = RecipeRunnerHelper.handleRecipe(IO.IN, holder, recipe.inputs, Map.of(ItemRecipeCapability.CAP, cache), false, recipe, false);
        assertFalse(result.isSuccess()); assertEquals(0, cache.getInt(content.content()));
    }
    @Test void outputChanceFillsTheActualNativeTankWithAnExactLongRunCount() {
        var holder = new CoreHolder(); var tank = fluids(holder, IO.OUT, Integer.MAX_VALUE, 0);
        holder.outputs.add(new RecipeHandlePart(List.of(tank)));
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("core_long_chance_output")
                .chancedOutput(SizedFluidIngredient.of(Fluids.WATER, 1), 5000).EUt(32).build();
        IGTRecipe.of(recipe).setRealParallels(3_000_000_002L);
        var content = recipe.outputs.get(FluidRecipeCapability.CAP).getFirst(); var cache = new Object2IntOpenHashMap<Object>();
        cache.put(content.content(), 0); Map<RecipeCapability<?>, Object2IntMap<?>> caches = Map.of(FluidRecipeCapability.CAP, cache);
        assertTrue(RecipeRunnerHelper.handleRecipe(IO.OUT, holder, recipe.outputs, caches, false, recipe, true).isSuccess());
        assertEquals(0, cache.getInt(content.content())); assertEquals(0, tank.getStorages()[0].getFluidAmount());
        assertTrue(RecipeRunnerHelper.handleRecipe(IO.OUT, holder, recipe.outputs, caches, false, recipe, false).isSuccess());
        assertEquals(1_500_000_001, tank.getStorages()[0].getFluidAmount());
    }
    @Test void nativeHoldersUseTheActualNativeRunnerWithNonConsumedAndOrdinaryInputs() {
        var holder = new NativeHolder();
        var inventory = items(holder, IO.IN, new ItemStack(Items.IRON_INGOT, 9), new ItemStack(Items.DIAMOND));
        holder.addHandlerList(RecipeHandlerList.of(IO.IN, inventory));
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_fallback")
                .inputItems(Items.IRON_INGOT, 2).notConsumable(Items.DIAMOND).build();
        assertTrue(RecipeRunnerHelper.handleRecipe(IO.IN, holder, recipe.inputs, Map.of(), false, recipe, true).isSuccess());
        assertEquals(9, inventory.getStackInSlot(0).getCount());
        assertTrue(RecipeRunnerHelper.handleRecipe(IO.IN, holder, recipe.inputs, Map.of(), false, recipe, false).isSuccess());
        assertEquals(7, inventory.getStackInSlot(0).getCount()); assertEquals(1, inventory.getStackInSlot(1).getCount());
    }
    @Test void blockedOutputReasonKeepsExactItemAndFluidAmountsInsteadOfClampingToInt() {
        var holder = new CoreHolder();
        var inventory = items(holder, IO.OUT, new ItemStack(Items.DIAMOND, 64)); var tank = fluids(holder, IO.OUT, 1000, 1000);
        holder.outputs.add(new RecipeHandlePart(List.of(inventory, tank)));
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("core_output_failure")
                .outputItems(LongIngredient.create(Ingredient.of(Items.IRON_INGOT), 9_007_199_254_740_993L))
                .outputFluids(LongFluidIngredient.create(Fluids.WATER, 3_000_000_003L)).build();
        var result = RecipeRunnerHelper.handleRecipe(IO.OUT, holder, recipe.outputs, Map.of(), false, recipe, true);
        assertFalse(result.isSuccess()); assertNotNull(result.reason());
        var text = assertInstanceOf(TranslatableContents.class, result.reason().getContents());
        assertEquals("gtceu.recipe.fail.Output.Content", text.getKey());
        var amounts = assertInstanceOf(String.class, text.getArgs()[0]);
        assertTrue(amounts.contains("9007199254740993")); assertTrue(amounts.contains("3000000003"));
        assertEquals(64, inventory.getStackInSlot(0).getCount()); assertEquals(1000, tank.getStorages()[0].getFluidAmount());
    }
    @Test void meExecutionUsesThePatternSlotThatMatchedTheNonConsumedIngredient() {
        var holder = new CoreHolder(); var me = MEPatternRecipeHandleIntegrationTest.fixture(); holder.patterns.add(me.part());
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("core_me_catalyst_plan")
                .inputItems(Items.IRON_INGOT, 2).notConsumable(Items.DIAMOND).build();
        me.items().stock(0, new ItemStack(Items.IRON_INGOT), 9).stock(1, new ItemStack(Items.IRON_INGOT), 9)
                .stock(1, new ItemStack(Items.DIAMOND), 1);
        assertTrue(input(holder, recipe, false, RecipeCacheStrategy.FULL_CACHE).isSuccess());
        assertEquals(9, me.items().stored(0)); assertEquals(8, me.items().stored(1)); // seven iron plus the NC diamond
        assertTrue(me.trait().cache.containsKey(1)); assertFalse(me.trait().cache.containsKey(0));
        assertSame(me.part(), holder.getActiveRecipeHandle(recipe));
    }
    @Test void partialAndNoCacheStrategiesDoNotPopulateMeInternalRecipeQueues() {
        for (var strategy : List.of(RecipeCacheStrategy.HANDLE_PART_CACHE_ONLY, RecipeCacheStrategy.NO_CACHE)) {
            var holder = new CoreHolder(); var me = MEPatternRecipeHandleIntegrationTest.fixture(); holder.patterns.add(me.part());
            me.items().stock(0, new ItemStack(Items.IRON_INGOT), 9); var recipe = itemRecipe(2);
            assertTrue(input(holder, recipe, true, strategy).isSuccess()); assertEquals(9, me.items().stored(0));
            assertTrue(me.trait().cache.isEmpty()); assertEquals(strategy.cacheToHandlePartMap, holder.active.containsKey(recipe));
            assertTrue(input(holder, recipe, false, strategy).isSuccess()); assertEquals(7, me.items().stored(0));
            assertTrue(me.trait().cache.isEmpty());
        }
    }

    @Test void directNativeRunnerSelectsTheCoreBusBeforeSpendingInputs() {
        var holder = new CoreHolder(); var recipe = itemRecipe(5);
        var first = items(holder, IO.IN, new ItemStack(Items.IRON_INGOT, 3));
        var second = items(holder, IO.IN, new ItemStack(Items.IRON_INGOT, 8));
        holder.inputs.addAll(List.of(new RecipeHandlePart(List.of(first)), new RecipeHandlePart(List.of(second))));
        var result = new com.gregtechceu.gtceu.api.recipe.RecipeRunner(recipe, IO.IN, false, holder, Map.of(), false).handle(recipe.inputs);
        assertTrue(result.isSuccess()); assertEquals(3, first.getStackInSlot(0).getCount());
        assertEquals(3, second.getStackInSlot(0).getCount()); assertSame(holder.inputs.get(1), holder.getActiveRecipeHandle(recipe));
        assertTrue(holder.proxies.isEmpty(), "The direct native runner must actually use Core routes, not the empty native proxy map");
    }

    @Test void controllerInputsCanSupplementOneDistinctBusWithoutCombiningOtherBuses() {
        var holder = new CoreHolder();
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("core_controller_bypass")
                .inputItems(Items.IRON_INGOT, 5).notConsumable(Items.DIAMOND)
                .inputFluids(SizedFluidIngredient.of(Fluids.WATER, 300)).build();
        var first = items(holder, IO.IN, new ItemStack(Items.IRON_INGOT, 3));
        var second = items(holder, IO.IN, new ItemStack(Items.IRON_INGOT, 3));
        var controller = items(holder, IO.IN, new ItemStack(Items.DIAMOND)); var tank = fluids(holder, IO.IN, 1000, 800);
        holder.inputs.addAll(List.of(new RecipeHandlePart(List.of(first)), new RecipeHandlePart(List.of(second))));
        holder.shared = new RecipeHandlePart(List.of(controller, tank));
        assertFalse(input(holder, recipe, false, RecipeCacheStrategy.FULL_CACHE).isSuccess());
        assertEquals(800, tank.getStorages()[0].getFluidAmount());
        second.storage.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 8));
        assertTrue(input(holder, recipe, false, RecipeCacheStrategy.FULL_CACHE).isSuccess());
        assertEquals(3, first.getStackInSlot(0).getCount()); assertEquals(3, second.getStackInSlot(0).getCount());
        assertEquals(1, controller.getStackInSlot(0).getCount()); assertEquals(500, tank.getStorages()[0].getFluidAmount());
    }

    @Test void voidingAllowsSimulationAndKeepsTheRealOutputThatStillFits() {
        var holder = new CoreHolder() {
            @Override public boolean canVoidRecipeOutputs(RecipeCapability<?> cap) { return cap == FluidRecipeCapability.CAP; }
        };
        var tank = fluids(holder, IO.OUT, 1000, 950); holder.outputs.add(new RecipeHandlePart(List.of(tank)));
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("core_void_excess")
                .outputFluids(SizedFluidIngredient.of(Fluids.WATER, 300)).build();
        assertTrue(RecipeRunnerHelper.handleRecipe(IO.OUT, holder, recipe.outputs, Map.of(), false, recipe, true).isSuccess());
        assertEquals(950, tank.getStorages()[0].getFluidAmount());
        assertTrue(RecipeRunnerHelper.handleRecipe(IO.OUT, holder, recipe.outputs, Map.of(), false, recipe, false).isSuccess());
        assertEquals(1000, tank.getStorages()[0].getFluidAmount());
        var itemRecipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("core_do_not_void_items").outputItems(Items.IRON_INGOT).build();
        assertFalse(RecipeRunnerHelper.handleRecipe(IO.OUT, holder, itemRecipe.outputs, Map.of(), false, itemRecipe, true).isSuccess());
    }

    @Test void tickOutputsKeepHandlerOrderWhileRegularOutputsUsePriority() {
        for (boolean tick : List.of(true, false)) {
            var holder = new CoreHolder();
            var low = holder.attachTrait(new NotifiableItemStackHandler(1, IO.OUT, IO.OUT) {
                @Override public int getPriority() { return -100; }
            });
            var high = holder.attachTrait(new NotifiableItemStackHandler(1, IO.OUT, IO.OUT) {
                @Override public int getPriority() { return 100; }
            });
            holder.outputs.addAll(List.of(new RecipeHandlePart(List.of(low)), new RecipeHandlePart(List.of(high))));
            var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("core_output_priority_" + tick).outputItems(Items.IRON_INGOT).build();
            assertTrue(RecipeRunnerHelper.handleRecipe(IO.OUT, holder, recipe.outputs, Map.of(), tick, recipe, false).isSuccess());
            assertEquals(tick ? 1 : 0, low.getStackInSlot(0).getCount()); assertEquals(tick ? 0 : 1, high.getStackInSlot(0).getCount());
        }
    }
}
