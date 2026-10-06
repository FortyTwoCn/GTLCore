package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.machine.multiblock.ParallelMachine;
import org.gtlcore.gtlcore.api.machine.trait.MEStock.IOptimizedMEList;
import org.gtlcore.gtlcore.api.machine.trait.MEStock.IMESlot;
import org.gtlcore.gtlcore.api.recipe.ingredient.AEFluidRecipeHandler;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import org.gtlcore.gtlcore.common.data.GTLRecipeTypes;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.StorageMachine;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeHandlerList;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEFluidList;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEFluidSlot;
import com.gregtechceu.gtceu.integration.ae2.machine.MEStockingHatchPartMachine;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Real GT buffered slots; network transaction tests use a bounded in-memory MEStorage fixture, not a grid. */
class AEFluidIntegrationTest {
    private static StorageMachine owner() {
        var definition = GTMultiMachines.VACUUM_FREEZER;
        return new StorageMachine(new BlockEntityCreationInfo(definition.getBlockEntityType(), BlockPos.ZERO,
                definition.getBlock().defaultBlockState()), 64);
    }

    private static ExportOnlyAEFluidList buffered(StorageMachine owner, GenericStack... stocks) {
        var list = owner.attachTrait(new ExportOnlyAEFluidList(owner, stocks.length));
        for (int i = 0; i < stocks.length; i++) list.getInventory()[i].setStock(stocks[i]);
        return list;
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
    }

    private static class Network implements MEStorage {
        final KeyCounter quantities = new KeyCounter();
        final List<Long> modulatedRequests = new ArrayList<>();
        Network(AEFluidKey key, long amount) { quantities.set(key, amount); }
        @Override public Component getDescription() { return Component.literal("bounded MEStorage fixture"); }
        @Override public void getAvailableStacks(KeyCounter output) { output.addAll(quantities); }
        @Override public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            MEStorage.checkPreconditions(key, amount, mode, source);
            long extracted = Math.min(quantities.get(key), amount);
            if (mode == Actionable.MODULATE) {
                modulatedRequests.add(amount);
                quantities.remove(key, extracted);
            }
            return extracted;
        }
    }

    @Test
    void actualBufferedAeSlotCanConsumeThreeBillionInOneTransaction() {
        var key = AEFluidKey.of(new FluidStack(Fluids.WATER, 1));
        var owner = owner();
        var list = buffered(owner, new GenericStack(key, 6_000_000_017L));
        var notifications = new AtomicInteger();
        list.getInventory()[0].setOnContentsChanged(notifications::incrementAndGet);
        var ingredient = LongFluidIngredient.create(Fluids.WATER, 3_000_000_000L);
        assertTrue(list.handleRecipeInner(IO.IN, null, left(ingredient), false).isEmpty());
        assertEquals(3_000_000_017L, list.getInventory()[0].getStock().amount());
        assertEquals(3_000_000_000L, LongFluidIngredient.amount(ingredient));
        assertEquals(1, notifications.get());
    }

    @Test
    void bufferedSimulationReservesLongAmountsAcrossOverlappingIngredients() {
        var key = AEFluidKey.of(new FluidStack(Fluids.WATER, 1));
        var list = buffered(owner(), new GenericStack(key, 5_000_000_000L));
        var remaining = left(LongFluidIngredient.create(Fluids.WATER, 3_000_000_000L),
                LongFluidIngredient.create(Fluids.WATER, 3_000_000_000L));
        list.handleRecipeInner(IO.IN, null, remaining, true);
        assertEquals(1, remaining.size());
        assertEquals(1_000_000_000L, LongFluidIngredient.amount(remaining.getFirst()));
        assertEquals(5_000_000_000L, list.getInventory()[0].getStock().amount());
    }

    @Test
    void bufferedQuantityCacheInvalidatesAfterSlotChangesAndPreservesComponents() {
        var plain = new FluidStack(Fluids.WATER, 1);
        var named = plain.copy(); named.set(DataComponents.CUSTOM_NAME, Component.literal("purified"));
        var plainKey = AEFluidKey.of(plain);
        var namedKey = AEFluidKey.of(named);
        var list = buffered(owner(), new GenericStack(plainKey, 4_000_000_000L), new GenericStack(namedKey, 5_000_000_000L));
        var amounts = ((IOptimizedMEList) list).getMEFluidMap();
        assertEquals(2, amounts.size());
        assertEquals(4_000_000_000L, amounts.getLong(plain));
        assertEquals(5_000_000_000L, amounts.getLong(named));
        list.getInventory()[0].setStock(new GenericStack(plainKey, 7));
        assertEquals(7, ((IOptimizedMEList) list).getMEFluidMap().getLong(plain));
        assertEquals(5_000_000_000L, ((IOptimizedMEList) list).getMEFluidMap().getLong(named));
    }

    @Test
    void nativeAeSlotNbtRoundTripPreservesLongStockAndFluidComponents() {
        var named = new FluidStack(Fluids.WATER, 1);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("purified"));
        var key = AEFluidKey.of(named);
        var slot = new ExportOnlyAEFluidSlot(new GenericStack(key, 1000), new GenericStack(key, 9_007_199_254_740_993L));
        var provider = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var restored = new ExportOnlyAEFluidSlot();
        restored.deserializeNBT(provider, slot.serializeNBT(provider));
        assertEquals(9_007_199_254_740_993L, restored.getStock().amount());
        assertEquals(key, restored.getStock().what());
        assertEquals(1000, restored.getConfig().amount());
    }

    @Test
    void nativeParallelCapabilitySeesActualAeQuantityBeyondDisplayLimit() {
        var key = AEFluidKey.of(new FluidStack(Fluids.WATER, 1));
        var list = buffered(owner(), new GenericStack(key, 6_000_000_017L));
        var holder = new Holder(); holder.addHandlerList(RecipeHandlerList.of(IO.IN, list));
        var recipe = GTLRecipeTypes.HEAT_EXCHANGER_RECIPES.recipeBuilder("ae_long_parallel")
                .inputFluids(LongFluidIngredient.create(Fluids.WATER, 3_000_000_000L)).build();
        assertEquals(2, FluidRecipeCapability.CAP.getMaxParallelByInput(holder, recipe, Integer.MAX_VALUE, false));
        assertEquals(6_000_000_017L, list.getInventory()[0].getStock().amount());
    }

    @Test
    void networkExtractReceivesLongRequestsAndUpdatesTheDisplayedStock() {
        var key = AEFluidKey.of(new FluidStack(Fluids.WATER, 1));
        var network = new Network(key, 6_000_000_017L);
        var owner = owner();
        var slot = new ExportOnlyAEFluidSlot(new GenericStack(key, 1000), new GenericStack(key, 6_000_000_017L));
        var remaining = left(LongFluidIngredient.create(Fluids.WATER, 3_000_000_000L));
        AEFluidRecipeHandler.handleNetwork(IO.IN, null, remaining, false, new ExportOnlyAEFluidSlot[] { slot },
                owner, network, IActionSource.empty());
        assertTrue(remaining.isEmpty());
        assertEquals(List.of(3_000_000_000L), network.modulatedRequests);
        assertEquals(3_000_000_017L, network.quantities.get(key));
        assertEquals(3_000_000_017L, slot.getStock().amount());
    }

    @Test
    void duplicateNetworkConfigurationsAndIngredientsCannotDoubleSpendSimulation() {
        var key = AEFluidKey.of(new FluidStack(Fluids.WATER, 1));
        var network = new Network(key, 5_000_000_000L);
        var slots = new ExportOnlyAEFluidSlot[] {
                new ExportOnlyAEFluidSlot(new GenericStack(key, 1000), new GenericStack(key, 5_000_000_000L)),
                new ExportOnlyAEFluidSlot(new GenericStack(key, 1000), new GenericStack(key, 5_000_000_000L)) };
        var remaining = left(LongFluidIngredient.create(Fluids.WATER, 3_000_000_000L),
                LongFluidIngredient.create(Fluids.WATER, 3_000_000_000L));
        AEFluidRecipeHandler.handleNetwork(IO.IN, null, remaining, true, slots, owner(), network, IActionSource.empty());
        assertEquals(1, remaining.size());
        assertEquals(1_000_000_000L, LongFluidIngredient.amount(remaining.getFirst()));
        assertEquals(5_000_000_000L, network.quantities.get(key));
        assertTrue(network.modulatedRequests.isEmpty());
    }

    @Test
    void networkIngredientChecksRetainExactFluidComponents() {
        var plain = new FluidStack(Fluids.WATER, 1);
        var named = plain.copy(); named.set(DataComponents.CUSTOM_NAME, Component.literal("purified"));
        var key = AEFluidKey.of(plain);
        var network = new Network(key, 5_000_000_000L);
        var slot = new ExportOnlyAEFluidSlot(new GenericStack(key, 1000), new GenericStack(key, 5_000_000_000L));
        var remaining = left(LongFluidIngredient.create(named, 3_000_000_000L));
        AEFluidRecipeHandler.handleNetwork(IO.IN, null, remaining, false, new ExportOnlyAEFluidSlot[] { slot },
                owner(), network, IActionSource.empty());
        assertEquals(3_000_000_000L, LongFluidIngredient.amount(remaining.getFirst()));
        assertEquals(5_000_000_000L, network.quantities.get(key));
        assertTrue(network.modulatedRequests.isEmpty());
    }

    @Test
    void actualNativeStockingHatchConstructsAndInvalidatesItsConfigurationCache() {
        var definition = GTMultiMachines.VACUUM_FREEZER;
        var machine = new MEStockingHatchPartMachine(new BlockEntityCreationInfo(definition.getBlockEntityType(),
                BlockPos.ZERO, definition.getBlock().defaultBlockState()));
        var list = machine.getSlotList();
        var optimized = (IOptimizedMEList) list;
        optimized.setChanged(false);
        var key = AEFluidKey.of(new FluidStack(Fluids.WATER, 1));
        list.getConfigurableSlot(0).setConfig(new GenericStack(key, 1000));
        assertTrue(optimized.getChanged());
        optimized.setChanged(false);
        ((IMESlot) list.getConfigurableSlot(0)).setConfigWithoutNotify(null);
        assertFalse(optimized.getChanged());
        list.clearInventory(0);
        assertTrue(optimized.getChanged());
        assertTrue(optimized.getMEFluidMap().isEmpty());
    }

    @Test
    void actualNativeStockingSlotRespectsTheHatchWorkingState() {
        var definition = GTMultiMachines.VACUUM_FREEZER;
        var machine = new MEStockingHatchPartMachine(new BlockEntityCreationInfo(definition.getBlockEntityType(),
                BlockPos.ZERO, definition.getBlock().defaultBlockState()));
        var slot = (ExportOnlyAEFluidSlot) machine.getSlotList().getConfigurableSlot(0);
        var key = AEFluidKey.of(new FluidStack(Fluids.WATER, 1));
        slot.setConfig(new GenericStack(key, 1000)); slot.setStock(new GenericStack(key, 5_000_000_000L));
        machine.setWorkingEnabled(false);
        assertTrue(slot.drain(1000, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE).isEmpty());
        assertEquals(5_000_000_000L, slot.getStock().amount());
    }
}
