package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.machine.trait.NotifiableCircuitItemStackHandler;
import org.gtlcore.gtlcore.api.machine.trait.RecipeHandlePart;
import org.gtlcore.gtlcore.api.machine.trait.LongStorageAdapterRegistry;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.StorageMachine;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntCircuitIngredient;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;
import com.gregtechceu.gtceu.common.machine.trait.ProgrammableCircuitSlotTrait;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEFluidList;
import com.hepdd.gtmthings.common.block.machine.trait.CatalystItemStackHandler;
import com.hepdd.gtmthings.common.block.machine.trait.CatalystFluidStackHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Production Core wrapper, real native GT/GTMThings traits and AE slots; no replacement handlers. */
class NativeRecipeHandlerIntegrationTest {
    private static StorageMachine owner() {
        var definition = GTMultiMachines.VACUUM_FREEZER;
        return new StorageMachine(new BlockEntityCreationInfo(definition.getBlockEntityType(), BlockPos.ZERO,
                definition.getBlock().defaultBlockState()), 64);
    }
    private static NotifiableItemStackHandler items(IO io, ItemStack... stacks) {
        var handler = owner().attachTrait(new NotifiableItemStackHandler(stacks.length, io, io));
        for (int i = 0; i < stacks.length; i++) handler.storage.setStackInSlot(i, stacks[i]);
        return handler;
    }
    private static NotifiableFluidTank fluids(int amount) {
        var handler = owner().attachTrait(new NotifiableFluidTank(1, 1000, IO.IN, IO.IN));
        handler.getStorages()[0].setFluid(new FluidStack(Fluids.WATER, amount));
        return handler;
    }

    @Test
    void itemAggregationUsesComponentsLongAdaptersAndIdentityDeduplication() {
        var named = new ItemStack(Items.IRON_INGOT, 7);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("catalyst"));
        var first = items(IO.IN, new ItemStack(Items.IRON_INGOT, 37), named);
        var second = items(IO.IN, new ItemStack(Items.IRON_INGOT, 11));
        LongStorageAdapterRegistry.attachItemStorage(first.storage, slot -> slot == 0 ? 9_007_199_254_740_993L : 7);
        var part = new RecipeHandlePart(List.of(first, second, first));
        Object2LongMap<ItemStack> amounts = part.getSelfContent(ItemRecipeCapability.CAP, false);
        assertEquals(2, amounts.size());
        assertEquals(9_007_199_254_741_004L, amounts.getLong(new ItemStack(Items.IRON_INGOT)));
        assertEquals(7, amounts.getLong(named));
    }

    @Test
    void cachedInventoryKeysRemainStableWhenTheSourceStackChanges() {
        var handler = items(IO.IN, new ItemStack(Items.IRON_INGOT, 64));
        Object2LongMap<ItemStack> amounts = new RecipeHandlePart(List.of(handler)).getSelfContent(ItemRecipeCapability.CAP, false);
        handler.storage.getStackInSlot(0).set(DataComponents.CUSTOM_NAME, Component.literal("changed"));
        assertEquals(64, amounts.getLong(new ItemStack(Items.IRON_INGOT)));
        assertEquals(0, amounts.getLong(handler.storage.getStackInSlot(0)));
    }

    @Test
    void aggregationSaturatesInsteadOfOverflowingTheLongQuantity() {
        var first = items(IO.IN, new ItemStack(Items.IRON_INGOT));
        var second = items(IO.IN, new ItemStack(Items.IRON_INGOT));
        LongStorageAdapterRegistry.attachItemStorage(first.storage, slot -> Long.MAX_VALUE);
        LongStorageAdapterRegistry.attachItemStorage(second.storage, slot -> 100);
        Object2LongMap<ItemStack> amounts = new RecipeHandlePart(List.of(first, second)).getSelfContent(ItemRecipeCapability.CAP, false);
        assertEquals(Long.MAX_VALUE, amounts.getLong(new ItemStack(Items.IRON_INGOT)));
    }

    @Test
    void aeFluidAndSharedTanksUseLongStocksAndDoNotDuplicateTheSameHandler() {
        var owner = owner();
        var list = owner.attachTrait(new ExportOnlyAEFluidList(owner, 2));
        var plain = new FluidStack(Fluids.WATER, 1);
        var named = plain.copy(); named.set(DataComponents.CUSTOM_NAME, Component.literal("purified"));
        list.getInventory()[0].setStock(new GenericStack(AEFluidKey.of(plain), 6_000_000_017L));
        list.getInventory()[1].setStock(new GenericStack(AEFluidKey.of(named), 5_000_000_003L));
        var shared = fluids(200);
        var part = new RecipeHandlePart(List.of(list));
        part.addSharedFluidHandlers(List.of(list, shared));
        Object2LongMap<FluidStack> own = part.getSelfContent(FluidRecipeCapability.CAP, false);
        Object2LongMap<FluidStack> all = part.getContentWithShared(FluidRecipeCapability.CAP, false);
        assertEquals(6_000_000_017L, own.getLong(plain));
        assertEquals(6_000_000_217L, all.getLong(plain));
        assertEquals(5_000_000_003L, all.getLong(named));
    }

    @Test
    void nativeEmptyListsCompleteSimulationWithoutMutatingTheRecipeOrInventory() {
        var first = items(IO.IN, new ItemStack(Items.IRON_INGOT, 64));
        var second = items(IO.IN, new ItemStack(Items.IRON_INGOT, 64));
        var part = new RecipeHandlePart(List.of(first, second));
        var ingredient = LongIngredient.create(Ingredient.of(Items.IRON_INGOT), 100);
        Map<RecipeCapability<?>, List<Object>> inputs = Map.of(ItemRecipeCapability.CAP, List.of(ingredient));
        assertTrue(part.handleRecipe(IO.IN, null, inputs, true).isEmpty());
        assertEquals(64, first.storage.getStackInSlot(0).getCount());
        assertEquals(64, second.storage.getStackInSlot(0).getCount());
        assertEquals(100, LongIngredient.amount(ingredient));
        assertEquals(1, inputs.size());
    }

    @Test
    void realConsumptionCarriesTheRemainingQuantityToTheNextNativeHandler() {
        var first = items(IO.IN, new ItemStack(Items.IRON_INGOT, 64));
        var second = items(IO.IN, new ItemStack(Items.IRON_INGOT, 64));
        var part = new RecipeHandlePart(List.of(first, second));
        assertNull(part.handleRecipe(IO.IN, null, ItemRecipeCapability.CAP,
                List.of(LongIngredient.create(Ingredient.of(Items.IRON_INGOT), 100)), false));
        assertTrue(first.storage.getStackInSlot(0).isEmpty());
        assertEquals(28, second.storage.getStackInSlot(0).getCount());
        var remaining = part.handleRecipe(IO.IN, null, ItemRecipeCapability.CAP,
                List.of(LongIngredient.create(Ingredient.of(Items.IRON_INGOT), 100)), true);
        assertNotNull(remaining);
        assertEquals(72, LongIngredient.amount(ItemRecipeCapability.CAP.of(remaining.getFirst())));
    }

    @Test
    void duplicateHandlerReferencesCannotSpendInventoryTwiceDuringSimulation() {
        var source = items(IO.IN, new ItemStack(Items.IRON_INGOT, 64));
        var part = new RecipeHandlePart(List.of(source, source));
        var remaining = part.handleRecipe(IO.IN, null, ItemRecipeCapability.CAP,
                List.of(LongIngredient.create(Ingredient.of(Items.IRON_INGOT), 100)), true);
        assertNotNull(remaining);
        assertEquals(36, LongIngredient.amount(ItemRecipeCapability.CAP.of(remaining.getFirst())));
        assertEquals(64, source.storage.getStackInSlot(0).getCount());
    }

    @Test
    void outputRemaindersFillSuccessiveNativeInventories() {
        var first = items(IO.OUT, ItemStack.EMPTY);
        var second = items(IO.OUT, ItemStack.EMPTY);
        var part = RecipeHandlePart.of(IO.OUT, List.of(first, second));
        assertNull(part.handleRecipe(IO.OUT, null, ItemRecipeCapability.CAP,
                List.of(LongIngredient.create(Ingredient.of(Items.DIAMOND), 100)), false));
        assertEquals(64, first.storage.getStackInSlot(0).getCount());
        assertEquals(36, second.storage.getStackInSlot(0).getCount());
    }

    @Test
    void catalystsAndBothCircuitTraitKindsAreExcludedFromConsumableInventoryCounts() {
        var owner = owner();
        var itemCatalyst = owner.attachTrait(new CatalystItemStackHandler(1, IO.IN, IO.IN));
        itemCatalyst.storage.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 64));
        var fluidCatalyst = owner.attachTrait(new CatalystFluidStackHandler(1, 1000, IO.IN, IO.IN));
        fluidCatalyst.getStorages()[0].setFluid(new FluidStack(Fluids.WATER, 800));
        var circuit = new NotifiableCircuitItemStackHandler(owner);
        circuit.insertItem(0, IntCircuitBehaviour.stack(7), false);
        var nativeCircuit = owner.attachTrait(new ProgrammableCircuitSlotTrait()); nativeCircuit.setCurrentCircuit(8);
        var part = new RecipeHandlePart(List.of(itemCatalyst, fluidCatalyst, circuit, nativeCircuit,
                items(IO.IN, new ItemStack(Items.IRON_INGOT, 16)), fluids(200)));
        Object2LongMap<ItemStack> itemAmounts = part.getSelfContent(ItemRecipeCapability.CAP, false);
        Object2LongMap<FluidStack> fluidAmounts = part.getSelfContent(FluidRecipeCapability.CAP, false);
        assertEquals(1, itemAmounts.size());
        assertEquals(16, itemAmounts.getLong(new ItemStack(Items.IRON_INGOT)));
        assertEquals(200, fluidAmounts.getLong(new FluidStack(Fluids.WATER, 1)));
    }

    @Test
    void sharedCircuitMatchesItsConfigurationWithoutConsumptionOrSearchableStock() {
        var owner = owner();
        var circuit = new NotifiableCircuitItemStackHandler(owner);
        assertSame(owner, circuit.getMachine());
        assertFalse(circuit.shouldSearchContent());
        assertEquals(1, circuit.storage.getSlotLimit(0));
        assertTrue(circuit.insertItem(0, IntCircuitBehaviour.stack(7), true).isEmpty());
        assertTrue(circuit.storage.getStackInSlot(0).isEmpty());
        circuit.insertItem(0, IntCircuitBehaviour.stack(7), false);
        assertTrue(circuit.handleRecipe(IO.IN, null, List.of(new SizedIngredient(IntCircuitIngredient.circuit(7), 1)), true).isEmpty());
        assertEquals(1, circuit.handleRecipe(IO.IN, null, List.of(new SizedIngredient(IntCircuitIngredient.circuit(8), 1)), true).size());
        assertEquals(1, circuit.handleRecipe(IO.IN, null, List.of(new SizedIngredient(IntCircuitIngredient.circuit(7), 1)), false).size());
        assertEquals(7, IntCircuitBehaviour.getCircuitConfiguration(circuit.storage.getStackInSlot(0)));
        var invalid = new ItemStack(Items.IRON_INGOT);
        assertSame(invalid, circuit.insertItem(0, invalid, false));
    }
}
