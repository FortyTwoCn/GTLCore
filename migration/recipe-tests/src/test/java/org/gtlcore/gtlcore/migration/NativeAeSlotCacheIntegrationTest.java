package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.integration.ae2.handler.SlotCacheManager;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NativeAeSlotCacheIntegrationTest {
    private static ItemStack item(String name) {
        var stack = new ItemStack(Items.IRON_INGOT); stack.set(DataComponents.CUSTOM_NAME, Component.literal(name)); return stack;
    }
    private static FluidStack fluid(String name) {
        var stack = new FluidStack(Fluids.WATER, 1); stack.set(DataComponents.CUSTOM_NAME, Component.literal(name)); return stack;
    }

    @Test void nativeUnrestrictedPredicatesFindComponentBearingStockAndRecheckCachedAmounts() {
        var cache = new SlotCacheManager(); var inventory = new Object2LongLinkedOpenHashMap<AEItemKey>();
        var first = AEItemKey.of(item("first")); var second = AEItemKey.of(item("second"));
        inventory.put(first, 29); inventory.put(second, 14);
        var predicate = Ingredient.of(Items.IRON_INGOT);
        assertEquals(first, cache.getBestItemMatch(predicate, inventory, 20));
        assertEquals(29, inventory.getLong(first));
        inventory.put(first, 1);
        assertEquals(second, cache.getBestItemMatch(predicate, inventory, 12));
        assertNull(cache.getBestItemMatch(predicate, inventory, 20));
        assertNull(cache.getBestItemMatch(predicate, inventory, 0));
        var fluids = new Object2LongLinkedOpenHashMap<AEFluidKey>(); var named = AEFluidKey.of(fluid("water variant"));
        fluids.put(named, 1000);
        assertEquals(named, cache.getBestFluidMatch(FluidIngredient.single(Fluids.WATER), fluids, 1000));
    }

    @Test void componentPredicatesRejectLargeWrongStockForBothItemAndFluidKeys() {
        var cache = new SlotCacheManager(); var items = new Object2LongLinkedOpenHashMap<AEItemKey>();
        var right = item("required"); var key = AEItemKey.of(right);
        items.put(AEItemKey.of(item("wrong")), Long.MAX_VALUE); items.put(key, 50);
        var predicate = LongIngredient.create(right, 9_007_199_254_740_993L);
        assertEquals(key, cache.getBestItemMatch(predicate, items, 50));
        assertNull(cache.getBestItemMatch(predicate, items, 51));
        var fluids = new Object2LongLinkedOpenHashMap<AEFluidKey>(); var required = fluid("required fluid"); var fluidKey = AEFluidKey.of(required);
        fluids.put(AEFluidKey.of(fluid("wrong fluid")), Long.MAX_VALUE); fluids.put(fluidKey, 40);
        var fluidPredicate = LongFluidIngredient.create(required, 9_007_199_254_740_993L);
        assertEquals(fluidKey, cache.getBestFluidMatch(fluidPredicate, fluids, 40));
        assertNull(cache.getBestFluidMatch(fluidPredicate, fluids, 41));
    }

    @Test void reusableCatalystsAreSimulatedSeparatelyAndNeverBecomeConsumedStock() {
        var cache = new SlotCacheManager(); var stock = new Object2LongLinkedOpenHashMap<AEItemKey>();
        var catalyst = new Object2LongLinkedOpenHashMap<AEItemKey>(); var key = AEItemKey.of(Items.IRON_INGOT);
        stock.put(key, 3); catalyst.put(key, 4); var ingredient = Ingredient.of(Items.IRON_INGOT);
        assertNull(cache.getBestItemMatchSimulate(ingredient, stock, catalyst, 6));
        catalyst.put(key, 7);
        assertEquals(key, cache.getBestItemMatchSimulate(ingredient, stock, catalyst, 6));
        assertNull(cache.getBestItemMatch(ingredient, stock, 6));
        assertEquals(3, stock.getLong(key)); assertEquals(7, catalyst.getLong(key));
        var fluids = new Object2LongLinkedOpenHashMap<AEFluidKey>(); var fluidCatalyst = new Object2LongLinkedOpenHashMap<AEFluidKey>();
        var water = AEFluidKey.of(Fluids.WATER); fluidCatalyst.put(water, 1000);
        assertEquals(water, cache.getBestFluidMatchSimulate(FluidIngredient.single(Fluids.WATER), fluids, fluidCatalyst, 1000));
        assertNull(cache.getBestFluidMatch(FluidIngredient.single(Fluids.WATER), fluids, 1000)); assertEquals(1000, fluidCatalyst.getLong(water));
    }

    @Test void registryAwareCacheSaveRestoresPredicatesComponentsAndCircuitThenRejectsInvalidEntries() {
        var cache = new SlotCacheManager(); var item = item("saved predicate"); var fluid = fluid("saved fluid");
        var stock = new Object2LongLinkedOpenHashMap<AEItemKey>(); var fluids = new Object2LongLinkedOpenHashMap<AEFluidKey>();
        stock.put(AEItemKey.of(item), 40); fluids.put(AEFluidKey.of(fluid), 500);
        cache.getBestItemMatch(LongIngredient.create(item, 40), stock, 40);
        cache.getBestFluidMatch(LongFluidIngredient.create(fluid, 500), fluids, 500); cache.setCircuitCache(24);
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var saved = cache.serializeNBT(registries); var restored = new SlotCacheManager();
        restored.deserializeNBT(registries, saved);
        assertEquals(saved, restored.serializeNBT(registries)); assertEquals(24, restored.getCircuitCache());
        assertEquals(24, IntCircuitBehaviour.getCircuitConfiguration(restored.getCircuitStack()));
        var invalid = saved.copy();
        invalid.getList("itemCache", 10).getCompound(0).put("aekey", AEItemKey.of(Items.GOLD_INGOT).toTagGeneric(registries));
        invalid.getList("fluidCache", 10).getCompound(0).remove("ingredient"); invalid.putInt("circuit", 33);
        restored.deserializeNBT(registries, invalid);
        var empty = restored.serializeNBT(registries);
        assertTrue(empty.getList("itemCache", 10).isEmpty()); assertTrue(empty.getList("fluidCache", 10).isEmpty());
        assertEquals(-1, restored.getCircuitCache()); assertTrue(restored.getCircuitStack().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> restored.setCircuitCache(33));
        restored.setCircuitCache(0); restored.setCircuitCache(-1); assertTrue(restored.getCircuitStack().isEmpty());
    }
}
