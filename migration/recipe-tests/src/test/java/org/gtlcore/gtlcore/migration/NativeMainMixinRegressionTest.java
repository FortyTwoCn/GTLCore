package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.machine.trait.RecipeHandlePart;
import org.gtlcore.gtlcore.common.data.GTLDataComponents;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.items.ItemStackHandler;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.gtlcore.gtlcore.migration.NativeCoreRecipeRunnerIntegrationTest.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeMainMixinRegressionTest {
    @Test void nativeParallelEntryPointSelectsOneBusAndHonorsExcludedFluidCapabilities() {
        // The native chemical recipe type defaults to 30 EU/t; budget four runs as well as their item inputs.
        var holder = new CoreHolder() {
            @Override public long getOverclockVoltage() { return 120; }
        };
        var first = items(holder, IO.IN, new ItemStack(Items.IRON_INGOT, 3));
        var second = items(holder, IO.IN, new ItemStack(Items.IRON_INGOT, 9), new ItemStack(Items.DIAMOND));
        holder.inputs.addAll(List.of(new RecipeHandlePart(List.of(first)), new RecipeHandlePart(List.of(second))));
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_main_parallel_input")
                .inputItems(Items.IRON_INGOT, 2).notConsumable(Items.DIAMOND)
                .inputFluids(new net.neoforged.neoforge.fluids.FluidStack(Fluids.WATER, 100)).build();
        var selectedInputs = java.util.Map.<RecipeCapability<?>, List<com.gregtechceu.gtceu.api.recipe.content.Content>>of(
                ItemRecipeCapability.CAP, recipe.getInputContents(ItemRecipeCapability.CAP));
        assertEquals(4, org.gtlcore.gtlcore.api.recipe.IParallelLogic.getInputItemParallel(holder, recipe, 100, selectedInputs));
        assertEquals(4, ParallelLogic.getMaxByInput(holder, recipe, 100, List.of(FluidRecipeCapability.CAP)),
                () -> "inputs=" + recipe.inputs + "; tickInputs=" + recipe.tickInputs);
        assertSame(holder.inputs.get(1), holder.getActiveRecipeHandle(recipe));
        var moreEnergy = recipe.copy();
        EURecipeCapability.putEUContent(moreEnergy.tickInputs,
                new com.gregtechceu.gtceu.api.recipe.ingredient.EnergyStack(60));
        assertEquals(2, ParallelLogic.getMaxByInput(holder, moreEnergy, 100, List.of(FluidRecipeCapability.CAP)));
        assertEquals(0, ParallelLogic.getMaxByInput(holder, recipe, 100, List.of()));
        assertFalse(org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper.matchRecipeInput(holder, recipe));
        assertEquals(3, first.getStackInSlot(0).getCount()); assertEquals(9, second.getStackInSlot(0).getCount());
    }

    @Test void nativeOutputEntryPointReservesCapacityAndHonorsVoidAndSkipForEachCapability() {
        var holder = new CoreHolder();
        var full = items(holder, IO.OUT, new ItemStack(Items.DIAMOND, 64));
        var tank = fluids(holder, IO.OUT, 650, 0);
        holder.outputs.add(new RecipeHandlePart(List.of(full, tank)));
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_main_parallel_output")
                .outputItems(Items.IRON_INGOT).outputFluids(new net.neoforged.neoforge.fluids.FluidStack(Fluids.WATER, 200)).build();
        assertEquals(0, ParallelLogic.limitByOutputMerging(holder, recipe, 10, c -> false, List.of()));
        assertEquals(3, ParallelLogic.limitByOutputMerging(holder, recipe, 10, c -> c == ItemRecipeCapability.CAP, List.of()));
        assertEquals(3, ParallelLogic.limitByOutputMerging(holder, recipe, 10, c -> false, List.of(ItemRecipeCapability.CAP)));
        assertEquals(10, ParallelLogic.limitByOutputMerging(holder, recipe, 10, c -> true, List.of()));
        assertEquals(0, tank.getStorages()[0].getFluidAmount()); assertEquals(64, full.getStackInSlot(0).getCount());
    }

    @Test void emptyLdlibTransferReturnsTheWholeRemainderInSimulationAndRealInsertion() {
        var empty = new ItemStackTransfer(0);
        var stack = new ItemStack(Items.IRON_INGOT, 37);
        assertSame(stack, empty.insertItem(0, stack, true));
        assertSame(stack, empty.insertItem(0, stack, false));
        assertEquals(37, stack.getCount()); assertEquals(0, empty.getSlots());
        assertTrue(empty.extractItem(0, 64, false).isEmpty()); assertTrue(empty.getStackInSlot(0).isEmpty());
        empty.setStackInSlot(0, stack); empty.setStackInSlot(0, stack, true);
        assertThrows(RuntimeException.class, () -> new ItemStackHandler(0).insertItem(0, stack, false));
        var ordinary = new ItemStackHandler(1);
        assertTrue(ordinary.insertItem(0, stack.copy(), false).isEmpty()); assertEquals(37, ordinary.getStackInSlot(0).getCount());
    }

    @Test void autoExpandNativeComponentPreservesBothBooleanValuesInSavedItemsAndNetworkPackets() {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        for (boolean value : new boolean[]{false, true}) {
            var card = appeng.core.definitions.AEItems.MEMORY_CARD.stack();
            card.set(GTLDataComponents.AUTO_EXPAND.get(), value);
            assertEquals(value, ItemStack.parseOptional(registries, (net.minecraft.nbt.CompoundTag) card.save(registries).copy())
                    .get(GTLDataComponents.AUTO_EXPAND.get()));
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
            try {
                ItemStack.STREAM_CODEC.encode(buffer, card);
                assertEquals(value, ItemStack.STREAM_CODEC.decode(buffer).get(GTLDataComponents.AUTO_EXPAND.get()));
                assertFalse(buffer.isReadable());
            } finally { buffer.release(); }
        }
    }
}
