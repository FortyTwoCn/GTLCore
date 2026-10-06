package org.gtlcore.gtlcore.api.recipe.ingredient;

import org.gtlcore.gtlcore.api.machine.multiblock.ParallelMachine;
import org.gtlcore.gtlcore.api.machine.trait.LongItemStorageLookup;
import org.gtlcore.gtlcore.api.machine.trait.LongStorageAdapterRegistry;
import org.gtlcore.gtlcore.api.machine.trait.MEStock.IOptimizedMEList;
import org.gtlcore.gtlcore.api.recipe.IAdvancedContentModifier;

import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeHandlerGroup;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeHandlerGroupDistinctness;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeHandlerList;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntCircuitIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntProviderIngredient;
import com.gregtechceu.gtceu.utils.ItemStackHashStrategy;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.items.IItemHandler;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;

import java.util.*;

/** Quantity limits over GTCEu 8's native bus/color groups; no legacy capability tables. */
public final class LongItemParallelLogic {
    private LongItemParallelLogic() {}

    public static boolean hasLongIngredients(List<Content> contents) {
        return contents != null && contents.stream()
                .anyMatch(content -> LongIngredient.get(ItemRecipeCapability.CAP.of(content.content())) != null);
    }

    public static boolean hasLongInventory(IRecipeCapabilityHolder holder) {
        for (var handler : holder.getCapabilitiesFlat(IO.IN, ItemRecipeCapability.CAP)) {
            if (handler instanceof IOptimizedMEList) return true;
            if (handler instanceof IItemHandler items &&
                    (LongItemStorageLookup.find(items) != null ||
                            LongStorageAdapterRegistry.findItemStorage(items) != null)) return true;
        }
        return false;
    }

    public static long getMaxParallelByInput(IRecipeCapabilityHolder holder, GTRecipe recipe,
                                             long limit, boolean tick) {
        if (limit <= 0) return 0;
        var contents = (tick ? recipe.tickInputs : recipe.inputs).get(ItemRecipeCapability.CAP);
        if (contents == null || contents.isEmpty()) return limit;
        if (!holder.hasCapabilityProxies()) return 0;
        long maximum = 0;
        for (var inventory : getInputInventories(holder)) {
            maximum = Math.max(maximum, getMaxParallelByInput(inventory, contents, limit));
            if (maximum == limit) break;
        }
        return maximum;
    }

    /** Same bounds for a selected Core bus or pattern slot; the inventory is never changed. */
    public static long getMaxParallelByInput(Object2LongMap<ItemStack> inventory, List<Content> contents, long limit) {
        if (limit <= 0 || contents.isEmpty()) return Math.max(0, limit);
        List<Requirement> requirements = new ArrayList<>();
        for (var content : contents) {
            SizedIngredient sized = ItemRecipeCapability.CAP.of(content.content());
            Ingredient ingredient = LongIngredient.matchingIngredient(sized);
            if (ingredient.getCustomIngredient() instanceof IntCircuitIngredient) continue;
            long count = ingredient.getCustomIngredient() instanceof IntProviderIngredient provider ?
                    provider.getMaxRoll() : LongIngredient.amount(sized);
            if (count <= 0) continue;
            if (content.chance() != 0) limit = Math.min(limit, Long.MAX_VALUE / count);
            requirements.add(new Requirement(ingredient, count, content.chance() != 0));
        }
        if (requirements.isEmpty()) return limit;
        long low = 0, high = limit;
        while (low < high) {
            long middle = low + (high - low) / 2 + (high - low) % 2;
            if (canSupply(inventory, requirements, middle)) low = middle;
            else high = middle - 1;
        }
        return low;
    }

    private static boolean canSupply(Object2LongMap<ItemStack> inventory,
                                     List<Requirement> requirements, long parallels) {
        var stacks = inventory.object2LongEntrySet().stream().toList();
        long[] available = new long[stacks.size()];
        for (int i = 0; i < stacks.size(); i++) available[i] = stacks.get(i).getLongValue();
        // Reserve non-consumables once, then spend the remaining inventory in recipe order.
        for (int pass = 0; pass < 2; pass++) {
            for (var requirement : requirements) {
                if (requirement.consumable != (pass == 1)) continue;
                long needed = requirement.consumable ? requirement.count * parallels : requirement.count;
                for (int i = 0; i < available.length && needed > 0; i++) {
                    if (!requirement.ingredient.test(stacks.get(i).getKey())) continue;
                    long used = Math.min(needed, available[i]);
                    available[i] -= used;
                    needed -= used;
                }
                if (needed > 0) return false;
            }
        }
        return true;
    }

    private static List<Object2LongMap<ItemStack>> getInputInventories(IRecipeCapabilityHolder holder) {
        Map<RecipeHandlerGroup, List<RecipeHandlerList>> groups = new LinkedHashMap<>();
        for (var handler : holder.getCapabilitiesForIO(IO.IN)) {
            if (handler.hasCapability(ItemRecipeCapability.CAP)) {
                RecipeHelper.addToRecipeHandlerMap(handler.getGroup(), handler, groups);
            }
        }
        boolean confirmStock = holder instanceof ParallelMachine machine && machine.needConfirmMEStock();
        List<Object2LongMap<ItemStack>> inventories = new ArrayList<>();
        for (var entry : groups.entrySet()) {
            if (entry.getKey() == RecipeHandlerGroupDistinctness.BUS_DISTINCT) {
                for (var handler : entry.getValue()) inventories.add(inventory(List.of(handler), confirmStock));
            } else {
                inventories.add(inventory(entry.getValue(), confirmStock));
            }
        }
        return inventories;
    }

    private static Object2LongMap<ItemStack> inventory(List<RecipeHandlerList> lists, boolean confirmStock) {
        var strategy = ItemStackHashStrategy.comparingAllButCount();
        var inventory = new Object2LongOpenCustomHashMap<ItemStack>(strategy);
        var stockedKeys = new ObjectOpenCustomHashSet<ItemStack>(strategy);
        Set<IRecipeHandler<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var list : lists) {
            for (var handler : list.getCapability(ItemRecipeCapability.CAP)) {
                if (!visited.add(handler) || !handler.shouldSearchContent()) continue;
                if (handler instanceof IOptimizedMEList optimized && optimized.getMEItemMap() != null) {
                    for (var entry : optimized.getMEItemMap().object2LongEntrySet()) {
                        if (confirmStock && optimized.isStocking() && !stockedKeys.add(entry.getKey())) continue;
                        add(inventory, entry.getKey(), entry.getLongValue());
                    }
                    continue;
                }
                IItemHandler items = handler instanceof IItemHandler transfer ? transfer : null;
                if (items != null) {
                    var adapter = LongStorageAdapterRegistry.findItemStorage(items);
                    var storage = LongItemStorageLookup.find(items);
                    if (adapter == null && storage == null && handler instanceof NotifiableItemStackHandler notifiable) {
                        adapter = LongStorageAdapterRegistry.findItemStorage(notifiable.storage);
                        storage = LongItemStorageLookup.find(notifiable.storage);
                    }
                    for (int slot = 0; slot < items.getSlots(); slot++) {
                        ItemStack stack = items.getStackInSlot(slot);
                        long count = adapter != null ? adapter.getAmount(slot) :
                                storage != null ? storage.gtlcore$getStoredAmount(slot) : stack.getCount();
                        add(inventory, stack, count);
                    }
                } else {
                    for (var content : handler.getContents()) {
                        if (content instanceof ItemStack stack) add(inventory, stack, stack.getCount());
                    }
                }
            }
        }
        return inventory;
    }

    private static void add(Object2LongMap<ItemStack> inventory, ItemStack stack, long amount) {
        if (stack.isEmpty() || amount <= 0) return;
        long current = inventory.getLong(stack);
        inventory.put(stack.copyWithCount(1), current > Long.MAX_VALUE - amount ? Long.MAX_VALUE : current + amount);
    }

    public static long limitMaxParallelByOutput(IRecipeCapabilityHolder holder, GTRecipe recipe,
                                                long limit, boolean tick) {
        if (limit <= 0) return 0;
        var contents = (tick ? recipe.tickOutputs : recipe.outputs).get(ItemRecipeCapability.CAP);
        if (contents == null || contents.isEmpty()) return limit;
        var handlers = holder.getCapabilitiesFlat(IO.OUT, ItemRecipeCapability.CAP);
        if (handlers.isEmpty()) return 0;
        List<SizedIngredient> ingredients = new ArrayList<>(contents.size());
        for (var content : contents) {
            var ingredient = ItemRecipeCapability.CAP.of(content.content());
            long amount = LongIngredient.amount(ingredient);
            if (ingredient.ingredient().getCustomIngredient() instanceof IntProviderIngredient provider) {
                amount = provider.getMaxRoll();
                // The native provider has an int range; retain its worst-case output bound.
                limit = Math.min(limit, Integer.MAX_VALUE / Math.max(1, amount));
            } else if (amount > 0) {
                limit = Math.min(limit, Long.MAX_VALUE / amount);
            }
            ingredients.add(ingredient);
        }
        long low = 0, high = limit;
        while (low < high) {
            long middle = low + (high - low) / 2 + (high - low) % 2;
            var modifier = IAdvancedContentModifier.preciseMultiplier(middle);
            List<SizedIngredient> remaining = new ArrayList<>(ingredients.size());
            for (var ingredient : ingredients) {
                remaining.add(ItemRecipeCapability.CAP.copyWithModifier(ingredient, modifier));
            }
            for (var handler : handlers) {
                // Every native handler returns an empty list on success, never null.
                @SuppressWarnings("unchecked")
                var result = (List<SizedIngredient>) handler.handleRecipe(IO.OUT, recipe, remaining, true);
                remaining = result;
                if (remaining.isEmpty()) break;
            }
            if (remaining.isEmpty()) low = middle;
            else high = middle - 1;
        }
        return low;
    }

    private record Requirement(Ingredient ingredient, long count, boolean consumable) {}
}
