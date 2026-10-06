package org.gtlcore.gtlcore.api.recipe.ingredient;

import org.gtlcore.gtlcore.api.machine.multiblock.ParallelMachine;
import org.gtlcore.gtlcore.api.machine.trait.LongStorageAdapterRegistry;
import org.gtlcore.gtlcore.api.machine.trait.MEStock.IOptimizedMEList;
import org.gtlcore.gtlcore.api.recipe.IAdvancedContentModifier;

import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeHandlerGroup;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeHandlerGroupDistinctness;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeHandlerList;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntProviderFluidIngredient;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;

import java.util.*;

/** Native fluid group limits using actual long quantities and component-sensitive identities. */
public final class LongFluidParallelLogic {
    private LongFluidParallelLogic() {}

    private static final Hash.Strategy<FluidStack> FLUID_IDENTITY = new Hash.Strategy<>() {
        @Override public int hashCode(FluidStack stack) { return stack == null ? 0 : FluidStack.hashFluidAndComponents(stack); }
        @Override public boolean equals(FluidStack first, FluidStack second) {
            return first == second || first != null && second != null && FluidStack.isSameFluidSameComponents(first, second);
        }
    };

    public static Object2LongMap<FluidStack> newFluidAmountMap() {
        return new Object2LongOpenCustomHashMap<>(FLUID_IDENTITY);
    }

    public static Hash.Strategy<FluidStack> fluidIdentity() {
        return FLUID_IDENTITY;
    }

    public static boolean hasLongIngredients(List<Content> contents) {
        return contents != null && contents.stream().anyMatch(content ->
                LongFluidIngredient.get(FluidRecipeCapability.CAP.of(content.content())) != null);
    }

    public static boolean hasLongInventory(IRecipeCapabilityHolder holder) {
        for (var handler : holder.getCapabilitiesFlat(IO.IN, FluidRecipeCapability.CAP)) {
            if (handler instanceof IOptimizedMEList) return true;
            if (handler instanceof IFluidHandler fluids && LongStorageAdapterRegistry.findFluidStorage(fluids) != null)
                return true;
            if (handler instanceof NotifiableFluidTank tank) {
                for (var storage : tank.getStorages()) {
                    if (LongStorageAdapterRegistry.findFluidStorage(storage) != null) return true;
                }
            }
        }
        return false;
    }

    public static long getMaxParallelByInput(IRecipeCapabilityHolder holder, GTRecipe recipe, long limit, boolean tick) {
        if (limit <= 0) return 0;
        var contents = (tick ? recipe.tickInputs : recipe.inputs).get(FluidRecipeCapability.CAP);
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
    public static long getMaxParallelByInput(Object2LongMap<FluidStack> inventory, List<Content> contents, long limit) {
        if (limit <= 0 || contents.isEmpty()) return Math.max(0, limit);
        List<Requirement> requirements = new ArrayList<>();
        for (var content : contents) {
            var sized = FluidRecipeCapability.CAP.of(content.content());
            var ingredient = LongFluidIngredient.matchingIngredient(sized);
            long amount = ingredient instanceof IntProviderFluidIngredient provider ?
                    provider.getMaxRoll() : LongFluidIngredient.amount(sized);
            if (amount <= 0) continue;
            if (content.chance() != 0) limit = Math.min(limit, Long.MAX_VALUE / amount);
            requirements.add(new Requirement(ingredient, amount, content.chance() != 0));
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

    private static boolean canSupply(Object2LongMap<FluidStack> inventory, List<Requirement> requirements, long parallels) {
        var stacks = inventory.object2LongEntrySet().stream().toList();
        long[] available = new long[stacks.size()];
        for (int i = 0; i < stacks.size(); i++) available[i] = stacks.get(i).getLongValue();
        for (int pass = 0; pass < 2; pass++) {
            for (var requirement : requirements) {
                if (requirement.consumable != (pass == 1)) continue;
                long needed = requirement.consumable ? requirement.amount * parallels : requirement.amount;
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

    private static List<Object2LongMap<FluidStack>> getInputInventories(IRecipeCapabilityHolder holder) {
        Map<RecipeHandlerGroup, List<RecipeHandlerList>> groups = new LinkedHashMap<>();
        for (var handler : holder.getCapabilitiesForIO(IO.IN)) {
            if (handler.hasCapability(FluidRecipeCapability.CAP)) {
                RecipeHelper.addToRecipeHandlerMap(handler.getGroup(), handler, groups);
            }
        }
        boolean confirmStock = holder instanceof ParallelMachine machine && machine.needConfirmMEStock();
        List<Object2LongMap<FluidStack>> inventories = new ArrayList<>();
        for (var entry : groups.entrySet()) {
            if (entry.getKey() == RecipeHandlerGroupDistinctness.BUS_DISTINCT) {
                for (var handler : entry.getValue()) inventories.add(inventory(List.of(handler), confirmStock));
            } else inventories.add(inventory(entry.getValue(), confirmStock));
        }
        return inventories;
    }

    private static Object2LongMap<FluidStack> inventory(List<RecipeHandlerList> lists, boolean confirmStock) {
        var inventory = new Object2LongOpenCustomHashMap<FluidStack>(FLUID_IDENTITY);
        var stockedKeys = new ObjectOpenCustomHashSet<FluidStack>(FLUID_IDENTITY);
        Set<IRecipeHandler<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var list : lists) {
            for (var handler : list.getCapability(FluidRecipeCapability.CAP)) {
                if (!visited.add(handler) || !handler.shouldSearchContent()) continue;
                if (handler instanceof IOptimizedMEList optimized && optimized.getMEFluidMap() != null) {
                    for (var entry : optimized.getMEFluidMap().object2LongEntrySet()) {
                        if (confirmStock && optimized.isStocking() && !stockedKeys.add(entry.getKey())) continue;
                        add(inventory, entry.getKey(), entry.getLongValue());
                    }
                    continue;
                }
                if (handler instanceof IOptimizedMEList optimized && !optimized.getMEFluidList().isEmpty()) {
                    for (var stack : optimized.getMEFluidList()) {
                        if (confirmStock && optimized.isStocking() && !stockedKeys.add(stack)) continue;
                        add(inventory, stack, stack.getAmount());
                    }
                    continue;
                }
                if (handler instanceof IFluidHandler fluids) {
                    var adapter = LongStorageAdapterRegistry.findFluidStorage(fluids);
                    for (int tank = 0; tank < fluids.getTanks(); tank++) {
                        var stack = fluids.getFluidInTank(tank);
                        long amount = adapter == null ? stack.getAmount() : adapter.getAmount(tank);
                        if (adapter == null && handler instanceof NotifiableFluidTank notifiable) {
                            var tankAdapter = LongStorageAdapterRegistry.findFluidStorage(notifiable.getStorages()[tank]);
                            if (tankAdapter != null) amount = tankAdapter.getAmount(0);
                        }
                        add(inventory, stack, amount);
                    }
                } else {
                    for (var content : handler.getContents()) {
                        if (content instanceof FluidStack stack) add(inventory, stack, stack.getAmount());
                    }
                }
            }
        }
        return inventory;
    }

    private static void add(Object2LongMap<FluidStack> inventory, FluidStack stack, long amount) {
        if (stack.isEmpty() || amount <= 0) return;
        long current = inventory.getLong(stack);
        inventory.put(stack.copyWithAmount(1), current > Long.MAX_VALUE - amount ? Long.MAX_VALUE : current + amount);
    }

    public static long limitMaxParallelByOutput(IRecipeCapabilityHolder holder, GTRecipe recipe, long limit, boolean tick) {
        if (limit <= 0) return 0;
        var contents = (tick ? recipe.tickOutputs : recipe.outputs).get(FluidRecipeCapability.CAP);
        if (contents == null || contents.isEmpty()) return limit;
        var handlers = holder.getCapabilitiesFlat(IO.OUT, FluidRecipeCapability.CAP);
        if (handlers.isEmpty()) return 0;
        List<SizedFluidIngredient> ingredients = new ArrayList<>(contents.size());
        for (var content : contents) {
            var ingredient = FluidRecipeCapability.CAP.of(content.content());
            long amount = LongFluidIngredient.amount(ingredient);
            if (ingredient.ingredient() instanceof IntProviderFluidIngredient provider) {
                amount = provider.getMaxRoll();
                limit = Math.min(limit, Integer.MAX_VALUE / Math.max(1, amount));
            } else if (amount > 0) limit = Math.min(limit, Long.MAX_VALUE / amount);
            ingredients.add(ingredient);
        }
        long low = 0, high = limit;
        while (low < high) {
            long middle = low + (high - low) / 2 + (high - low) % 2;
            var modifier = IAdvancedContentModifier.preciseMultiplier(middle);
            List<SizedFluidIngredient> remaining = new ArrayList<>(ingredients.size());
            for (var ingredient : ingredients) remaining.add(FluidRecipeCapability.CAP.copyWithModifier(ingredient, modifier));
            for (var handler : handlers) {
                @SuppressWarnings("unchecked")
                var result = (List<SizedFluidIngredient>) handler.handleRecipe(IO.OUT, recipe, remaining, true);
                remaining = result;
                if (remaining.isEmpty()) break;
            }
            if (remaining.isEmpty()) low = middle;
            else high = middle - 1;
        }
        return low;
    }

    private record Requirement(FluidIngredient ingredient, long amount, boolean consumable) {}
}
