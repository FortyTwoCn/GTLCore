package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.item.VirtualIngredientBehavior;
import org.gtlcore.gtlcore.common.machine.VirtualIngredientSupplyMachine;
import org.gtlcore.gtlcore.integration.ae2.pattern.VirtualIngredientEncoding;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeVirtualIngredientIntegrationTest {
    private static final long LARGE = 9_007_199_254_740_993L;
    private static VirtualIngredientSupplyMachine create() {
        var definition = NativeAePatternFixtures.VIRTUAL_SUPPLY;
        return (VirtualIngredientSupplyMachine) definition.getBlockEntityType().create(BlockPos.ZERO, definition.getBlock().defaultBlockState());
    }
    private static ItemStack item() { var value = new ItemStack(Items.DIAMOND, 7); value.set(DataComponents.CUSTOM_NAME, Component.literal("virtual supply payload")); return value; }
    private static FluidStack fluid() { var value = new FluidStack(Fluids.WATER, 750); value.set(DataComponents.CUSTOM_NAME, Component.literal("virtual supply fluid")); return value; }

    @Test void publishedTokensKeepBackingItemsAndExactLongAmountsWithoutExposingRealMaterial() {
        var supply = create(); var payload = item(); supply.getInventory().storage.setStackInSlot(287, payload);
        var token = AEItemKey.of(VirtualIngredientBehavior.wrap(payload));
        assertEquals(288, supply.getInventory().getSlots());
        assertEquals(LARGE, supply.extract(token, LARGE, Actionable.SIMULATE, IActionSource.empty()));
        assertEquals(LARGE, supply.extract(token, LARGE, Actionable.MODULATE, IActionSource.empty()));
        assertEquals(7, supply.getInventory().storage.getStackInSlot(287).getCount());
        assertEquals(0, supply.extract(AEItemKey.of(payload), LARGE, Actionable.MODULATE, IActionSource.empty()));
        assertEquals(0, supply.insert(AEItemKey.of(payload), LARGE, Actionable.MODULATE, IActionSource.empty()));
        assertEquals(LARGE, supply.insert(token, LARGE, Actionable.MODULATE, IActionSource.empty()));
    }

    @Test void wrappersAreSealedOnAdmissionAndCanonicalPayloadsRetainFluidComponents() {
        var supply = create(); var editable = NativeAePatternFixtures.VIRTUAL.asStack();
        var storage = VirtualIngredientBehavior.getFluidStorage(editable); storage.setFluid(fluid()); VirtualIngredientBehavior.saveFluidStorage(editable, storage);
        var beforeSeal = AEItemKey.of(editable); supply.getInventory().storage.setStackInSlot(0, editable);
        assertTrue(VirtualIngredientBehavior.isMarked(editable));
        assertEquals(0, supply.extract(beforeSeal, 1000, Actionable.MODULATE, IActionSource.empty()));
        var token = AEItemKey.of(VirtualIngredientBehavior.wrap(fluid()));
        assertEquals(LARGE, supply.extract(token, LARGE, Actionable.MODULATE, IActionSource.empty()));
        assertEquals(AEFluidKey.of(fluid()), VirtualIngredientBehavior.payloadFluidKey(token.toStack()));
    }

    @Test void duplicateBackingStacksPublishOneKeyAndRemovalImmediatelyInvalidatesItsCachedAvailability() {
        var supply = create(); var payload = item(); var token = AEItemKey.of(VirtualIngredientBehavior.wrap(payload));
        supply.getInventory().storage.setStackInSlot(0, payload); supply.getInventory().storage.setStackInSlot(287, payload.copy());
        assertEquals(Long.MAX_VALUE, supply.getAvailableStacks().get(token));
        supply.getInventory().storage.setStackInSlot(0, ItemStack.EMPTY);
        assertEquals(Long.MAX_VALUE, supply.getAvailableStacks().get(token));
        supply.getInventory().storage.setStackInSlot(287, ItemStack.EMPTY);
        assertEquals(0, supply.getAvailableStacks().get(token)); assertEquals(0, supply.extract(token, 1, Actionable.MODULATE, IActionSource.empty()));
    }

    @Test void nativeEncodingOnlyWrapsNonConsumedRecipeInputsAndPreservesTheirComponents() {
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_virtual_encoding")
                .notConsumable(item()).notConsumableFluid(org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient.create(fluid(), 750))
                .inputItems(Items.IRON_INGOT).duration(2).build();
        var keys = VirtualIngredientEncoding.notConsumedKeys(recipe);
        assertFalse(keys.contains(AEItemKey.of(Items.IRON_INGOT)));
        var wrappedFluid = VirtualIngredientEncoding.wrapIfNotConsumed(new GenericStack(AEFluidKey.of(fluid()), 750), keys);
        assertNotNull(wrappedFluid); assertEquals(1, wrappedFluid.amount());
        assertEquals(AEFluidKey.of(fluid()), VirtualIngredientBehavior.payloadFluidKey(((AEItemKey) wrappedFluid.what()).toStack()));
        assertNull(VirtualIngredientEncoding.wrapIfNotConsumed(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1), keys));
        assertEquals(keys, VirtualIngredientEncoding.notConsumedKeys(new net.minecraft.world.item.crafting.RecipeHolder<>(recipe.getId(), recipe)));
    }
}
