package org.gtlcore.gtlcore.migration;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gtladd.gtladditions.common.machine.trait.FastNotifiableInputFluidTank;
import com.gtladd.gtladditions.common.machine.trait.FastNotifiableInputItemStack;
import com.gtladd.gtladditions.utils.OreProcessorRecipeHelper;
import com.gtladd.gtladditions.utils.TransferHelper;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.gtlcore.gtlcore.api.recipe.NativeContentState;
import org.gtlcore.gtlcore.api.recipe.ingredient.*;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.gtlcore.gtlcore.migration.NativeCoreRecipeRunnerIntegrationTest.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeAddLongInputTest {
    @Test void aeInsertKeepsComponentSensitiveLongBalancesAcrossNativeSaveAndClientSync() {
        var machine = new CoreHolder();
        var items = machine.attachTrait(new FastNotifiableInputItemStack());
        var fluids = machine.attachTrait(new FastNotifiableInputFluidTank());
        var namedItem = new ItemStack(Items.DIAMOND);
        namedItem.set(DataComponents.CUSTOM_NAME, Component.literal("native-long-item"));
        var namedFluid = new FluidStack(Fluids.WATER, 1);
        namedFluid.set(DataComponents.CUSTOM_NAME, Component.literal("native-long-fluid"));
        long amount = 9_007_199_254_741_007L;
        var helper = TransferHelper.INSTANCE;
        assertEquals(amount, helper.insertFromME(items, fluids, AEItemKey.of(namedItem), amount, Actionable.SIMULATE));
        assertTrue(items.isEmpty());
        assertEquals(amount, helper.insertFromME(items, fluids, AEItemKey.of(namedItem), amount, Actionable.MODULATE));
        assertEquals(amount + 1, helper.insertFromME(items, fluids, AEFluidKey.of(namedFluid), amount + 1, Actionable.MODULATE));
        helper.insertFromME(items, fluids, AEItemKey.of(Items.DIAMOND), 7, Actionable.MODULATE);
        helper.insertFromME(items, fluids, AEFluidKey.of(Fluids.WATER), 11, Actionable.MODULATE);
        assertEquals(2, items.getRealSize()); assertEquals(2, fluids.getRealSize());
        assertEquals(amount, items.getItemStorage().getLong(namedItem));
        assertEquals(amount + 1, fluids.getFluidStorage().getLong(namedFluid));
        assertEquals(Integer.MAX_VALUE, items.getStackInSlot(0).getCount());
        assertEquals(Integer.MAX_VALUE, fluids.getFluidInTank(0).getAmount());
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var restored = new CoreHolder();
        var copyItems = restored.attachTrait(new FastNotifiableInputItemStack());
        var copyFluids = restored.attachTrait(new FastNotifiableInputFluidTank());
        var itemNbt = items.serializeNBT(registries); var fluidNbt = fluids.serializeNBT(registries);
        assertTrue(itemNbt.contains("savedInventory")); assertTrue(fluidNbt.contains("savedInventory"));
        copyItems.deserializeNBT(registries, itemNbt.copy()); copyFluids.deserializeNBT(registries, fluidNbt.copy());
        assertEquals(amount, copyItems.getItemStorage().getLong(namedItem));
        assertEquals(amount + 1, copyFluids.getFluidStorage().getLong(namedFluid));
        assertEquals(7, copyItems.getItemStorage().getLong(new ItemStack(Items.DIAMOND)));
        assertEquals(11, copyFluids.getFluidStorage().getLong(new FluidStack(Fluids.WATER, 1)));
        var client = new CoreHolder();
        var clientItems = client.attachTrait(new FastNotifiableInputItemStack());
        var clientFluids = client.attachTrait(new FastNotifiableInputFluidTank());
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            items.getSyncDataHolder().resyncAllFields(); items.getSyncDataHolder().writeClientPacket(registries, buffer);
            clientItems.getSyncDataHolder().readClientPacket(registries, buffer); assertFalse(buffer.isReadable()); buffer.clear();
            fluids.getSyncDataHolder().resyncAllFields(); fluids.getSyncDataHolder().writeClientPacket(registries, buffer);
            clientFluids.getSyncDataHolder().readClientPacket(registries, buffer); assertFalse(buffer.isReadable());
            assertEquals(amount, clientItems.getItemStorage().getLong(namedItem));
            assertEquals(amount + 1, clientFluids.getFluidStorage().getLong(namedFluid));
        } finally { buffer.release(); }
        assertEquals(Long.MAX_VALUE - amount, helper.insertFromME(items, fluids, AEItemKey.of(namedItem), Long.MAX_VALUE, Actionable.MODULATE));
        assertEquals(0, helper.insertFromME(items, fluids, AEItemKey.of(namedItem), 1, Actionable.MODULATE));
        assertEquals(Long.MAX_VALUE, items.getItemStorage().getLong(namedItem));
    }

    @Test void nativeRecipeSimulationReservesOverlappingRequirementsWithoutSpendingOrMutatingInputs() {
        var machine = new CoreHolder();
        var items = machine.attachTrait(new FastNotifiableInputItemStack());
        var fluids = machine.attachTrait(new FastNotifiableInputFluidTank());
        var offered = new ItemStack(Items.IRON_INGOT, 15);
        assertTrue(items.insertItem(0, offered, false).isEmpty()); assertEquals(15, offered.getCount());
        var sourceFluid = new FluidStack(Fluids.WATER, 15);
        assertEquals(15, fluids.fill(sourceFluid, IFluidHandler.FluidAction.EXECUTE)); assertEquals(15, sourceFluid.getAmount());
        var itemIngredient = LongIngredient.create(Ingredient.of(Items.IRON_INGOT), 12);
        var fluidIngredient = LongFluidIngredient.create(Fluids.WATER, 12);
        var itemLeft = items.handleRecipeInner(IO.IN, null, new ArrayList<>(List.of(itemIngredient, itemIngredient)), true);
        var fluidLeft = fluids.handleRecipeInner(IO.IN, null, new ArrayList<>(List.of(fluidIngredient, fluidIngredient)), true);
        assertEquals(1, itemLeft.size()); assertEquals(9, LongIngredient.amount(itemLeft.getFirst()));
        assertEquals(1, fluidLeft.size()); assertEquals(9, LongFluidIngredient.amount(fluidLeft.getFirst()));
        assertEquals(12, LongIngredient.amount(itemIngredient)); assertEquals(12, LongFluidIngredient.amount(fluidIngredient));
        assertEquals(15, items.gtlcore$getStoredAmount(0)); assertEquals(15, fluids.gtlcore$getStoredAmount(0));
        var changes = new AtomicInteger(); items.addChangedListener(changes::incrementAndGet);
        assertTrue(items.handleRecipeInner(IO.IN, null, new ArrayList<>(List.of(LongIngredient.create(Ingredient.of(Items.IRON_INGOT), 10))), false).isEmpty());
        assertEquals(5, items.gtlcore$getStoredAmount(0)); assertEquals(1, changes.get());
        assertTrue(items.extractItem(0, 5, false).isEmpty()); assertTrue(fluids.drain(5, IFluidHandler.FluidAction.EXECUTE).isEmpty());
    }

    @Test void partialNativeExportsAndImportsPreserveEveryAcceptedQuantity() {
        var machine = new CoreHolder();
        var items = machine.attachTrait(new FastNotifiableInputItemStack());
        var fluids = machine.attachTrait(new FastNotifiableInputFluidTank());
        long amount = 3_000_000_000L;
        TransferHelper.INSTANCE.insertFromME(items, fluids, AEItemKey.of(Items.IRON_INGOT), amount, Actionable.MODULATE);
        TransferHelper.INSTANCE.insertFromME(items, fluids, AEFluidKey.of(Fluids.WATER), amount, Actionable.MODULATE);
        var destinationItems = new ItemStackHandler(1); var destinationFluids = new FluidTank(650);
        TransferHelper.INSTANCE.exportToTarget(items, stack -> true, destinationItems);
        TransferHelper.INSTANCE.exportToTarget(fluids, stack -> true, destinationFluids);
        assertEquals(64, destinationItems.getStackInSlot(0).getCount()); assertEquals(amount - 64, items.gtlcore$getStoredAmount(0));
        assertEquals(650, destinationFluids.getFluidAmount()); assertEquals(amount - 650, fluids.gtlcore$getStoredAmount(0));
        TransferHelper.INSTANCE.importToTarget(items, stack -> true, destinationItems);
        TransferHelper.INSTANCE.importToTarget(fluids, stack -> true, destinationFluids);
        assertTrue(destinationItems.getStackInSlot(0).isEmpty()); assertTrue(destinationFluids.isEmpty());
        assertEquals(amount, items.gtlcore$getStoredAmount(0)); assertEquals(amount, fluids.gtlcore$getStoredAmount(0));
        items.getItemStorage().put(new ItemStack(Items.IRON_INGOT), Long.MAX_VALUE - 5);
        fluids.getFluidStorage().put(new FluidStack(Fluids.WATER, 1), Long.MAX_VALUE - 7);
        destinationItems.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 11)); destinationFluids.fill(new FluidStack(Fluids.WATER, 13), IFluidHandler.FluidAction.EXECUTE);
        TransferHelper.INSTANCE.importToTarget(items, stack -> true, destinationItems);
        TransferHelper.INSTANCE.importToTarget(fluids, stack -> true, destinationFluids);
        assertEquals(Long.MAX_VALUE, items.gtlcore$getStoredAmount(0)); assertEquals(6, destinationItems.getStackInSlot(0).getCount());
        assertEquals(Long.MAX_VALUE, fluids.gtlcore$getStoredAmount(0)); assertEquals(6, destinationFluids.getFluidAmount());
    }

    @Test void oreCopyPreservesNativeMetadataNonConsumablesAndExactLongOutputs() {
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_add_ore_copy")
                .notConsumableFluid(new FluidStack(Fluids.LAVA, 3))
                .inputFluids(LongFluidIngredient.create(Fluids.WATER, 3_000_000_000L))
                .outputItems(LongIngredient.create(Ingredient.of(Items.DIAMOND), 4_000_000_000L)).duration(100).build();
        var output = recipe.getOutputContents(ItemRecipeCapability.CAP).getFirst();
        NativeContentState.of(output).gtlcore$setTierChanceBoost(300);
        NativeContentState.of(output).gtlcore$setSlotName("ore-result");
        recipe.groupColor = 11; recipe.keepSpoilingProgress = true; recipe.data.putString("native_test", "unchanged");
        var copy = OreProcessorRecipeHelper.INSTANCE.copyForOreProcessor(recipe, 2, 2, .5, .25);
        assertEquals(8_000_000_000L, LongIngredient.amount(ItemRecipeCapability.CAP.of(copy.getOutputContents(ItemRecipeCapability.CAP).getFirst().content())));
        assertEquals(3, LongFluidIngredient.amount(FluidRecipeCapability.CAP.of(copy.getInputContents(FluidRecipeCapability.CAP).get(0).content())));
        assertEquals(1_500_000_000L, LongFluidIngredient.amount(FluidRecipeCapability.CAP.of(copy.getInputContents(FluidRecipeCapability.CAP).get(1).content())));
        assertEquals(600, NativeContentState.of(copy.getOutputContents(ItemRecipeCapability.CAP).getFirst()).gtlcore$getTierChanceBoost());
        assertEquals("ore-result", NativeContentState.of(copy.getOutputContents(ItemRecipeCapability.CAP).getFirst()).gtlcore$getSlotName());
        assertEquals(25, copy.duration); assertEquals(recipe.recipeCategory, copy.recipeCategory); assertEquals(11, copy.groupColor); assertTrue(copy.keepSpoilingProgress);
        copy.data.putString("native_test", "copy"); assertEquals("unchanged", recipe.data.getString("native_test"));
        assertEquals(4_000_000_000L, LongIngredient.amount(ItemRecipeCapability.CAP.of(output.content())));
        assertEquals(300, NativeContentState.of(output).gtlcore$getTierChanceBoost());
    }
}
