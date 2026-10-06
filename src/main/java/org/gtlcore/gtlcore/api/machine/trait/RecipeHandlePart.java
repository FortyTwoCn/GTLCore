package org.gtlcore.gtlcore.api.machine.trait;

import org.gtlcore.gtlcore.api.machine.trait.MEStock.IOptimizedMEList;

import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidParallelLogic;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank;
import com.gregtechceu.gtceu.common.machine.trait.ProgrammableCircuitSlotTrait;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import com.gregtechceu.gtceu.utils.ItemStackHashStrategy;

import net.neoforged.neoforge.fluids.FluidStack;

import net.minecraft.world.item.ItemStack;

import com.google.common.collect.Iterables;
import com.hepdd.gtmthings.common.block.machine.trait.CatalystFluidStackHandler;
import com.hepdd.gtmthings.common.block.machine.trait.CatalystItemStackHandler;
import it.unimi.dsi.fastutil.objects.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class RecipeHandlePart implements IRecipeHandlePart {

    public static final Comparator<RecipeHandlePart> COMPARATOR = (h1, h2) -> {
        int cmp = Long.compare(h1.getPriority(), h2.getPriority());
        if (cmp != 0) return cmp;
        boolean b1 = h1.getTotalContentAmount() > 0;
        boolean b2 = h2.getTotalContentAmount() > 0;
        return Boolean.compare(b1, b2);
    };

    private final Reference2ObjectMap<RecipeCapability<?>, List<IRecipeHandler<?>>> handlerMap = new Reference2ObjectOpenHashMap<>();
    private final List<IRecipeHandler<SizedFluidIngredient>> sharedFluidHandlers = new ObjectArrayList<>();

    public RecipeHandlePart(Iterable<IRecipeHandler<?>> handlers) {
        Set<IRecipeHandler<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var handler : handlers) {
            if (!visited.add(handler)) continue;
            handlerMap.computeIfAbsent(handler.getCapability(), c -> new ObjectArrayList<>()).add(handler);
        }
    }

    public static RecipeHandlePart of(IO io, Iterable<IRecipeHandler<?>> handlers) {
        RecipeHandlePart rhl = new RecipeHandlePart(handlers);
        if (io == IO.OUT) {
            for (var entry : rhl.getHandlerFastIterable()) {
                entry.getValue().sort(IRecipeHandler.ENTRY_COMPARATOR);
            }
        }
        return rhl;
    }

    public void addSharedFluidHandlers(Iterable<IRecipeHandler<SizedFluidIngredient>> handlers) {
        for (var handler : handlers) {
            if (handler.getCapability() == FluidRecipeCapability.CAP) sharedFluidHandlers.add(handler);
        }
    }

    public ObjectIterable<Reference2ObjectMap.Entry<RecipeCapability<?>, List<IRecipeHandler<?>>>> getHandlerFastIterable() {
        return Reference2ObjectMaps.fastIterable(handlerMap);
    }

    @SuppressWarnings("unchecked")
    public <S> Object2LongMap<S> getSelfContent(RecipeCapability<?> cap, boolean confirmMEStock) {
        if (cap == ItemRecipeCapability.CAP) {
            return (Object2LongMap<S>) createItemMap(this.getCapability(cap), new Object2LongOpenCustomHashMap<>(ItemStackHashStrategy.comparingAllButCount()), confirmMEStock);
        } else if (cap == FluidRecipeCapability.CAP) {
            return (Object2LongMap<S>) createFluidMap(this.getCapability(cap), LongFluidParallelLogic.newFluidAmountMap(), confirmMEStock);
        }
        return Object2LongMaps.EMPTY_MAP;
    }

    @SuppressWarnings("unchecked")
    public <S> Object2LongMap<S> getContentWithShared(RecipeCapability<?> cap, boolean confirmMEStock) {
        if (cap == ItemRecipeCapability.CAP) {
            return (Object2LongMap<S>) createItemMap(this.getCapability(ItemRecipeCapability.CAP), new Object2LongOpenCustomHashMap<>(ItemStackHashStrategy.comparingAllButCount()), confirmMEStock);
        } else if (cap == FluidRecipeCapability.CAP) {
            return (Object2LongMap<S>) createFluidMap(Iterables.concat(this.getCapability(FluidRecipeCapability.CAP), this.sharedFluidHandlers), LongFluidParallelLogic.newFluidAmountMap(), confirmMEStock);
        }
        return Object2LongMaps.EMPTY_MAP;
    }

    @SuppressWarnings("unchecked")
    public @NotNull <T> List<IRecipeHandler<T>> getCapability(RecipeCapability<T> cap) {
        List<?> handlers = handlerMap.getOrDefault(cap, Collections.emptyList());
        return (List<IRecipeHandler<T>>) handlers;
    }

    public Reference2ObjectOpenHashMap<RecipeCapability<?>, List<Object>> handleRecipe(IO io, GTRecipe recipe,
                                                                                       Map<RecipeCapability<?>, List<Object>> contents,
                                                                                       boolean simulate) {
        var copy = new Reference2ObjectOpenHashMap<>(contents);
        if (!handlerMap.isEmpty()) {
            for (var it = copy.reference2ObjectEntrySet().fastIterator(); it.hasNext();) {
                var entry = it.next();
                var handlerList = getCapability(entry.getKey());
                for (var handler : handlerList) {
                    var left = handler.handleRecipe(io, recipe, entry.getValue(), simulate);
                    if (left.isEmpty()) {
                        it.remove();
                        break;
                    } else entry.setValue(new ArrayList<>(left));
                }
            }
        }
        return copy;
    }

    /** Core cache wrapper retains its nullable completion marker; native handlers always return a list. */
    @Nullable
    public List<?> handleRecipe(IO io, GTRecipe recipe, RecipeCapability<?> cap, List<?> contents, boolean simulate) {
        var handlerList = getCapability(cap);
        for (var handler : handlerList) {
            contents = handler.handleRecipe(io, recipe, contents, simulate);
            if (contents.isEmpty()) return null;
        }
        return contents;
    }

    private static <T> Object2LongMap<ItemStack> createItemMap(Iterable<IRecipeHandler<T>> handlers,
                                                               Object2LongMap<ItemStack> itemContent, boolean confirmME) {
        var stockedKeys = new ObjectOpenCustomHashSet<>(ItemStackHashStrategy.comparingAllButCount());
        Set<IRecipeHandler<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var handler : handlers) {
            if (!visited.add(handler) || handler instanceof CatalystItemStackHandler ||
                    handler instanceof NotifiableCircuitItemStackHandler || handler instanceof ProgrammableCircuitSlotTrait ||
                    !handler.shouldSearchContent()) continue;
            if (handler instanceof IOptimizedMEList optimized && optimized.getMEItemMap() != null) {
                for (var entry : optimized.getMEItemMap().object2LongEntrySet()) {
                    if (confirmME && optimized.isStocking() && !stockedKeys.add(entry.getKey())) continue;
                    addItem(itemContent, entry.getKey(), entry.getLongValue());
                }
            } else if (handler instanceof IItemHandler items) {
                var adapter = LongStorageAdapterRegistry.findItemStorage(items);
                var storage = LongItemStorageLookup.find(items);
                if (adapter == null && storage == null && handler instanceof NotifiableItemStackHandler notifiable) {
                    adapter = LongStorageAdapterRegistry.findItemStorage(notifiable.storage);
                    storage = LongItemStorageLookup.find(notifiable.storage);
                }
                for (int slot = 0; slot < items.getSlots(); slot++) {
                    var stack = items.getStackInSlot(slot);
                    long count = adapter != null ? adapter.getAmount(slot) :
                            storage != null ? storage.gtlcore$getStoredAmount(slot) : stack.getCount();
                    addItem(itemContent, stack, count);
                }
            } else {
                for (var content : handler.getContents()) if (content instanceof ItemStack stack) {
                    addItem(itemContent, stack, stack.getCount());
                }
            }
        }
        return itemContent;
    }

    private static <T> Object2LongMap<FluidStack> createFluidMap(Iterable<IRecipeHandler<T>> handlers,
                                                                Object2LongMap<FluidStack> fluidContent, boolean confirmME) {
        Object2LongMap<FluidStack> stockedKeys = LongFluidParallelLogic.newFluidAmountMap();
        Set<IRecipeHandler<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var handler : handlers) {
            if (!visited.add(handler) || handler instanceof CatalystFluidStackHandler || !handler.shouldSearchContent()) continue;
            if (handler instanceof IOptimizedMEList optimized && optimized.getMEFluidMap() != null) {
                for (var entry : optimized.getMEFluidMap().object2LongEntrySet()) {
                    if (confirmME && optimized.isStocking() && stockedKeys.containsKey(entry.getKey())) continue;
                    stockedKeys.put(entry.getKey(), 1);
                    addFluid(fluidContent, entry.getKey(), entry.getLongValue());
                }
            } else if (handler instanceof IOptimizedMEList optimized) {
                for (var stack : optimized.getMEFluidList()) {
                    if (confirmME && optimized.isStocking() && stockedKeys.containsKey(stack)) continue;
                    stockedKeys.put(stack, 1);
                    addFluid(fluidContent, stack, stack.getAmount());
                }
            } else if (handler instanceof IFluidHandler fluids) {
                var adapter = LongStorageAdapterRegistry.findFluidStorage(fluids);
                for (int tank = 0; tank < fluids.getTanks(); tank++) {
                    var stack = fluids.getFluidInTank(tank);
                    long count = adapter == null ? stack.getAmount() : adapter.getAmount(tank);
                    if (adapter == null && handler instanceof NotifiableFluidTank notifiable) {
                        var tankAdapter = LongStorageAdapterRegistry.findFluidStorage(notifiable.getStorages()[tank]);
                        if (tankAdapter != null) count = tankAdapter.getAmount(0);
                    }
                    addFluid(fluidContent, stack, count);
                }
            } else {
                for (var content : handler.getContents()) if (content instanceof FluidStack stack) {
                    addFluid(fluidContent, stack, stack.getAmount());
                }
            }
        }
        return fluidContent;
    }

    private static void addItem(Object2LongMap<ItemStack> amounts, ItemStack stack, long count) {
        if (stack.isEmpty() || count <= 0) return;
        amounts.put(stack.copyWithCount(1), saturatedAdd(amounts.getLong(stack), count));
    }
    private static void addFluid(Object2LongMap<FluidStack> amounts, FluidStack stack, long count) {
        if (stack.isEmpty() || count <= 0) return;
        amounts.put(stack.copyWithAmount(1), saturatedAdd(amounts.getLong(stack), count));
    }
    private static long saturatedAdd(long first, long second) {
        return first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
    }

    private long getPriority() {
        long priority = 0;
        for (List<IRecipeHandler<?>> list : handlerMap.values()) {
            for (IRecipeHandler<?> handler : list) {
                priority += handler.getPriority();
            }
        }
        return priority;
    }

    private double getTotalContentAmount() {
        double sum = 0;
        for (List<IRecipeHandler<?>> list : handlerMap.values()) {
            for (IRecipeHandler<?> handler : list) {
                sum += handler.getTotalContentAmount();
            }
        }
        return sum;
    }
}
