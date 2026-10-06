package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.machine.multiblock.ParallelMachine;
import org.gtlcore.gtlcore.api.machine.trait.LongStorageAdapterRegistry;
import org.gtlcore.gtlcore.api.recipe.IAdvancedContentModifier;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.StorageMachine;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeHandlerList;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.content.SerializerFluidIngredient;
import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;
import org.gtlcore.gtlcore.common.data.GTLRecipeTypes;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Actual GT/NeoForge serializers, traits and transformed capability calls, without legacy fluid classes. */
class LongFluidIntegrationTest {
    private static final long LARGE = 3_000_000_000L;

    private static class Tank extends NotifiableFluidTank {
        Tank(int slots, int capacity, IO io, boolean sameFluids) {
            super(slots, capacity, io);
            this.allowSameFluids = sameFluids;
        }
    }

    private static Tank tank(int slots, int capacity, IO io, boolean sameFluids) {
        var definition = GTMultiMachines.VACUUM_FREEZER;
        var owner = new StorageMachine(new BlockEntityCreationInfo(definition.getBlockEntityType(), BlockPos.ZERO,
                definition.getBlock().defaultBlockState()), 64);
        return owner.attachTrait(new Tank(slots, capacity, io, sameFluids));
    }

    private static List<SizedFluidIngredient> left(SizedFluidIngredient... ingredients) {
        return new ArrayList<>(List.of(ingredients));
    }

    private static class Holder implements IRecipeCapabilityHolder, ParallelMachine {
        private final Map<IO, List<RecipeHandlerList>> proxies = new EnumMap<>(IO.class);
        private final Map<IO, Map<RecipeCapability<?>, List<IRecipeHandler<?>>>> flat = new EnumMap<>(IO.class);
        @Override public Map<IO, List<RecipeHandlerList>> getCapabilitiesProxy() { return proxies; }
        @Override public Map<IO, Map<RecipeCapability<?>, List<IRecipeHandler<?>>>> getCapabilitiesFlat() { return flat; }
        @Override public int getMaxParallel() { return Integer.MAX_VALUE; }
        Holder add(RecipeHandlerList list) { addHandlerList(list); return this; }
    }

    @Test
    void nativeJsonNbtAndNetworkKeepTheLongQuantityAndFluidComponents() {
        var stack = new FluidStack(Fluids.WATER, 1);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("purified"));
        var ingredient = LongFluidIngredient.create(stack, 9_007_199_254_740_993L);
        var provider = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var serializer = SerializerFluidIngredient.INSTANCE;
        var json = serializer.toJson(ingredient, provider);
        assertTrue(json.toString().contains("gtlcore:long_fluid"));
        var jsonCopy = serializer.fromJson(json, provider);
        assertEquals(LongFluidIngredient.amount(ingredient), LongFluidIngredient.amount(jsonCopy));
        assertTrue(LongFluidIngredient.matchingIngredient(jsonCopy).test(stack));
        assertFalse(LongFluidIngredient.matchingIngredient(jsonCopy).test(new FluidStack(Fluids.WATER, 1)));
        var nbtCopy = serializer.fromNbt(serializer.toNbt(ingredient, provider), provider);
        assertEquals(LongFluidIngredient.amount(ingredient), LongFluidIngredient.amount(nbtCopy));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), provider);
        try {
            serializer.toNetwork(buffer, ingredient);
            var networkCopy = serializer.fromNetwork(buffer);
            assertEquals(LongFluidIngredient.amount(ingredient), LongFluidIngredient.amount(networkCopy));
            assertTrue(LongFluidIngredient.matchingIngredient(networkCopy).test(stack));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @Test
    void invalidAmountsAreRejectedAndNestedWrappersAreFlattened() {
        assertThrows(IllegalArgumentException.class, () -> LongFluidIngredient.create(Fluids.WATER, 0));
        assertThrows(IllegalArgumentException.class, () -> LongFluidIngredient.create(Fluids.WATER, -1));
        var original = LongFluidIngredient.create(Fluids.WATER, LARGE);
        var nested = LongFluidIngredient.create(original.ingredient(), 17);
        assertEquals(17, LongFluidIngredient.amount(nested));
        assertFalse(LongFluidIngredient.matchingIngredient(nested) instanceof LongFluidIngredient);
        assertEquals(LARGE, LongFluidIngredient.amount(FluidRecipeCapability.CAP.copyInner(original)));
    }

    @Test
    void nativeCapabilityModifiersKeepExactFractionsAndPromoteOverflow() {
        long original = 9_007_199_254_740_993L;
        var ingredient = LongFluidIngredient.create(Fluids.WATER, original);
        assertEquals(original + 1, LongFluidIngredient.amount(FluidRecipeCapability.CAP.copyWithModifier(
                ingredient, ContentModifier.addition(1))));
        assertEquals(original / 3, LongFluidIngredient.amount(FluidRecipeCapability.CAP.copyWithModifier(
                ingredient, IAdvancedContentModifier.preciseDivision(1, 3))));
        assertEquals(4_000_000_000L, LongFluidIngredient.amount(FluidRecipeCapability.CAP.copyWithModifier(
                SizedFluidIngredient.of(Fluids.WATER, 2_000_000_000), ContentModifier.multiplier(2))));
        assertEquals(original, LongFluidIngredient.amount(ingredient));
    }

    @Test
    void inputAcrossNativeTanksConsumesThreeBillionWithoutChangingTheRecipe() {
        var input = tank(3, 1_500_000_000, IO.IN, true);
        for (var storage : input.getStorages()) storage.setFluid(new FluidStack(Fluids.WATER, 1_500_000_000));
        var notifications = new AtomicInteger();
        Runnable listener = notifications::incrementAndGet;
        for (var storage : input.getStorages()) storage.setOnContentsChanged(listener);
        var original = LongFluidIngredient.create(Fluids.WATER, LARGE);
        assertTrue(input.handleRecipeInner(IO.IN, null, left(original), false).isEmpty());
        assertEquals(0, input.getFluidInTank(0).getAmount());
        assertEquals(0, input.getFluidInTank(1).getAmount());
        assertEquals(1_500_000_000, input.getFluidInTank(2).getAmount());
        assertEquals(LARGE, LongFluidIngredient.amount(original));
        assertEquals(1, notifications.get());
    }

    @Test
    void inputSimulationDoesNotSpendTheSameFluidTwice() {
        var input = tank(1, 1000, IO.IN, true);
        input.setFluidInTank(0, new FluidStack(Fluids.WATER, 1000));
        var remaining = left(LongFluidIngredient.create(Fluids.WATER, 1200), SizedFluidIngredient.of(Fluids.WATER, 500));
        input.handleRecipeInner(IO.IN, null, remaining, true);
        assertEquals(200, LongFluidIngredient.amount(remaining.get(0)));
        assertEquals(500, LongFluidIngredient.amount(remaining.get(1)));
        assertEquals(1000, input.getFluidInTank(0).getAmount());
    }

    @Test
    void outputAcrossNativeTanksKeepsLongRemaindersAndOneNotification() {
        var output = tank(2, 1_000_000_000, IO.OUT, true);
        var notifications = new AtomicInteger();
        Runnable listener = notifications::incrementAndGet;
        for (var storage : output.getStorages()) storage.setOnContentsChanged(listener);
        var remaining = left(LongFluidIngredient.create(Fluids.WATER, LARGE));
        output.handleRecipeInner(IO.OUT, null, remaining, false);
        assertEquals(1_000_000_000L, LongFluidIngredient.amount(remaining.getFirst()));
        assertEquals(1_000_000_000, output.getFluidInTank(0).getAmount());
        assertEquals(1_000_000_000, output.getFluidInTank(1).getAmount());
        assertEquals(1, notifications.get());
    }

    @Test
    void outputSimulationAccountsForEarlierIngredientsAndDifferentFluids() {
        var output = tank(1, 1000, IO.OUT, true);
        var remaining = left(LongFluidIngredient.create(Fluids.WATER, 600), SizedFluidIngredient.of(Fluids.WATER, 600));
        output.handleRecipeInner(IO.OUT, null, remaining, true);
        assertEquals(200, LongFluidIngredient.amount(remaining.getFirst()));
        assertTrue(output.getFluidInTank(0).isEmpty());
        remaining = left(LongFluidIngredient.create(Fluids.WATER, 500), SizedFluidIngredient.of(Fluids.LAVA, 500));
        output.handleRecipeInner(IO.OUT, null, remaining, true);
        assertEquals(500, remaining.getFirst().amount());
        assertTrue(remaining.getFirst().ingredient().test(new FluidStack(Fluids.LAVA, 1)));
    }

    @Test
    void disallowSameFluidsKeepsOneTankPerFluidEvenDuringSimulation() {
        var output = tank(2, 1000, IO.OUT, false);
        var remaining = left(LongFluidIngredient.create(Fluids.WATER, 1200));
        output.handleRecipeInner(IO.OUT, null, remaining, true);
        assertEquals(200, LongFluidIngredient.amount(remaining.getFirst()));
        remaining = left(LongFluidIngredient.create(Fluids.WATER, 600), SizedFluidIngredient.of(Fluids.WATER, 600));
        output.handleRecipeInner(IO.OUT, null, remaining, true);
        assertEquals(200, LongFluidIngredient.amount(remaining.getFirst()));
        assertTrue(output.isEmpty());
    }

    @Test
    void componentMatchingAndTankValidatorsRemainEffective() {
        var named = new FluidStack(Fluids.WATER, 1000);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("purified"));
        var input = tank(1, 1000, IO.IN, true);
        input.setFluidInTank(0, new FluidStack(Fluids.WATER, 1000));
        var remaining = left(LongFluidIngredient.create(named, 500));
        input.handleRecipeInner(IO.IN, null, remaining, false);
        assertEquals(500, LongFluidIngredient.amount(remaining.getFirst()));
        assertEquals(1000, input.getFluidInTank(0).getAmount());
        var output = tank(1, 1000, IO.OUT, true);
        output.setFilter(stack -> stack.getFluid() == Fluids.LAVA);
        remaining = left(LongFluidIngredient.create(Fluids.WATER, 500));
        output.handleRecipeInner(IO.OUT, null, remaining, false);
        assertEquals(500, LongFluidIngredient.amount(remaining.getFirst()));
        assertTrue(output.isEmpty());
    }

    @Test
    void listenersAreRestoredWhenAFluidHandlerThrows() {
        var owner = tank(1, 1000, IO.IN, true).getMachine();
        var broken = new CustomFluidTank(1000) {
            @Override public FluidStack drain(int request, FluidAction action) { throw new IllegalStateException("drain"); }
        };
        var input = owner.attachTrait(new NotifiableFluidTank(List.of(broken), IO.IN));
        broken.setFluid(new FluidStack(Fluids.WATER, 1000));
        var notifications = new AtomicInteger();
        Runnable listener = notifications::incrementAndGet;
        broken.setOnContentsChanged(listener);
        assertThrows(IllegalStateException.class, () -> input.handleRecipeInner(IO.IN, null,
                left(LongFluidIngredient.create(Fluids.WATER, 500)), true));
        assertSame(listener, broken.getOnContentsChanged());
        assertEquals(0, notifications.get());
    }

    @Test
    void nativeParallelInputUsesRealLongAdapterQuantities() {
        var input = tank(1, 1000, IO.IN, true);
        input.setFluidInTank(0, new FluidStack(Fluids.WATER, 1));
        LongStorageAdapterRegistry.attachFluidStorage(input.getStorages()[0], slot -> 6_000_000_017L);
        var holder = new Holder().add(RecipeHandlerList.of(IO.IN, input));
        var recipe = GTLRecipeTypes.HEAT_EXCHANGER_RECIPES.recipeBuilder("long_fluid_adapter")
                .inputFluids(LongFluidIngredient.create(Fluids.WATER, LARGE)).build();
        assertEquals(2, FluidRecipeCapability.CAP.getMaxParallelByInput(holder, recipe, Integer.MAX_VALUE, false));
        assertEquals(1, input.getFluidInTank(0).getAmount());
    }

    @Test
    void separateBusesAndOverlappingFluidRequirementsCannotDoubleCountStock() {
        var input = tank(1, 1000, IO.IN, true);
        input.setFluidInTank(0, new FluidStack(Fluids.WATER, 1000));
        var second = tank(1, 1000, IO.IN, true);
        second.setFluidInTank(0, new FluidStack(Fluids.WATER, 1000));
        var firstList = RecipeHandlerList.of(IO.IN, input);
        var secondList = RecipeHandlerList.of(IO.IN, second);
        firstList.setDistinct(true); secondList.setDistinct(true);
        var holder = new Holder().add(firstList).add(secondList);
        var recipe = GTLRecipeTypes.HEAT_EXCHANGER_RECIPES.recipeBuilder("long_fluid_distinct")
                .inputFluids(LongFluidIngredient.create(Fluids.WATER, 1500)).build();
        assertEquals(0, FluidRecipeCapability.CAP.getMaxParallelByInput(holder, recipe, 5, false));
        firstList.setDistinct(false); secondList.setDistinct(false);
        assertEquals(1, FluidRecipeCapability.CAP.getMaxParallelByInput(holder, recipe, 5, false));
        recipe = GTLRecipeTypes.HEAT_EXCHANGER_RECIPES.recipeBuilder("long_fluid_overlap")
                .inputFluids(LongFluidIngredient.create(Fluids.WATER, 600), LongFluidIngredient.create(Fluids.WATER, 600)).build();
        holder = new Holder().add(RecipeHandlerList.of(IO.IN, input));
        assertEquals(0, FluidRecipeCapability.CAP.getMaxParallelByInput(holder, recipe, 5, false));
    }

    @Test
    void outputParallelCanUseCombinedCapacityBeyondTheIntBoundary() {
        var output = tank(2, 2_000_000_000, IO.OUT, true);
        var holder = new Holder().add(RecipeHandlerList.of(IO.OUT, output));
        var recipe = GTLRecipeTypes.HEAT_EXCHANGER_RECIPES.recipeBuilder("long_fluid_output")
                .outputFluids(SizedFluidIngredient.of(Fluids.WATER, 1_500_000_000)).build();
        assertEquals(2, FluidRecipeCapability.CAP.limitMaxParallelByOutput(holder, recipe, 10, false));
        assertTrue(output.isEmpty());
        recipe = GTLRecipeTypes.HEAT_EXCHANGER_RECIPES.recipeBuilder("long_fluid_impossible")
                .outputFluids(LongFluidIngredient.create(Fluids.WATER, 5_000_000_000L)).build();
        assertEquals(0, FluidRecipeCapability.CAP.limitMaxParallelByOutput(holder, recipe, 10, false));
    }
}
