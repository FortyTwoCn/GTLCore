package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Calls actual GT methods transformed by the production quantity mixins. */
class LongRecipeIntegrationTest {
    @Test
    void capabilityModificationKeepsPrecisionAndCreatesLongQuantitiesOnOverflow() {
        var input = LongIngredient.create(Ingredient.of(Items.IRON_INGOT), 9_007_199_254_740_993L);
        var changed = ItemRecipeCapability.CAP.copyWithModifier(input, ContentModifier.addition(1));
        assertEquals(9_007_199_254_740_994L, LongIngredient.amount(changed));
        assertEquals(9_007_199_254_740_993L, LongIngredient.amount(input));
        var ordinary = SizedIngredient.of(Items.IRON_INGOT, 2_000_000_000);
        assertEquals(4_000_000_000L, LongIngredient.amount(
                ItemRecipeCapability.CAP.copyWithModifier(ordinary, ContentModifier.multiplier(2))));
    }

    @Test
    void consumeRetainsLongRemainderAndNotifiesOnce() {
        var storage = new CustomItemStackHandler(2);
        storage.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 64));
        storage.setStackInSlot(1, new ItemStack(Items.IRON_INGOT, 64));
        var notifications = new AtomicInteger();
        storage.setOnContentsChanged(notifications::incrementAndGet);
        var original = LongIngredient.create(Ingredient.of(Items.IRON_INGOT), 3_000_000_000L);
        var left = new ArrayList<>(java.util.List.of(original));
        var remainder = NotifiableItemStackHandler.handleRecipe(IO.IN, null, left, false, IO.IN, storage, null);
        assertSame(left, remainder);
        assertEquals(3_000_000_000L - 128, LongIngredient.amount(remainder.getFirst()));
        assertEquals(3_000_000_000L, LongIngredient.amount(original));
        assertTrue(storage.getStackInSlot(0).isEmpty());
        assertTrue(storage.getStackInSlot(1).isEmpty());
        assertEquals(1, notifications.get());
    }

    @Test
    void simulationDoesNotSpendTheSameItemsTwiceOrMutateInventory() {
        var storage = new CustomItemStackHandler(1);
        storage.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 64));
        var notifications = new AtomicInteger();
        storage.setOnContentsChanged(notifications::incrementAndGet);
        var left = new ArrayList<>(java.util.List.of(
                LongIngredient.create(Ingredient.of(Items.IRON_INGOT), 100),
                SizedIngredient.of(Items.IRON_INGOT, 32)));
        NotifiableItemStackHandler.handleRecipe(IO.IN, null, left, true, IO.IN, storage, null);
        assertEquals(36, LongIngredient.amount(left.get(0)));
        assertEquals(32, LongIngredient.amount(left.get(1)));
        assertEquals(64, storage.getStackInSlot(0).getCount());
        assertEquals(0, notifications.get());
    }

    @Test
    void outputKeepsLongRemainderAndRespectsStackCapacity() {
        var storage = new CustomItemStackHandler(2);
        var notifications = new AtomicInteger();
        storage.setOnContentsChanged(notifications::incrementAndGet);
        var left = new ArrayList<>(java.util.List.of(LongIngredient.create(Ingredient.of(Items.DIAMOND), 3_000_000_000L)));
        NotifiableItemStackHandler.handleRecipe(IO.OUT, null, left, false, IO.OUT, storage, null);
        assertEquals(3_000_000_000L - 128, LongIngredient.amount(left.getFirst()));
        assertEquals(64, storage.getStackInSlot(0).getCount());
        assertEquals(64, storage.getStackInSlot(1).getCount());
        assertEquals(1, notifications.get());
    }

    @Test
    void simulationCompletesARecipeWithoutNullRemainders() {
        var storage = new CustomItemStackHandler(1);
        storage.setStackInSlot(0, new ItemStack(Items.DIAMOND, 32));
        var left = new ArrayList<>(java.util.List.of(LongIngredient.create(Ingredient.of(Items.DIAMOND), 32)));
        assertTrue(NotifiableItemStackHandler.handleRecipe(IO.IN, null, left, true, IO.IN, storage, null).isEmpty());
        assertEquals(32, storage.getStackInSlot(0).getCount());
    }

    @Test
    void handlerModeMismatchDoesNotConsumeOrRewriteTheRecipe() {
        var storage = new CustomItemStackHandler(1);
        storage.setStackInSlot(0, new ItemStack(Items.DIAMOND, 32));
        var ingredient = LongIngredient.create(Ingredient.of(Items.DIAMOND), 32);
        var left = new ArrayList<>(java.util.List.of(ingredient));
        assertSame(left, NotifiableItemStackHandler.handleRecipe(IO.IN, null, left, false, IO.OUT, storage, null));
        assertSame(ingredient, left.getFirst());
        assertEquals(32, storage.getStackInSlot(0).getCount());
    }
}
