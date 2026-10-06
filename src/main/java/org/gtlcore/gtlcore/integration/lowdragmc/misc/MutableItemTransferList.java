package org.gtlcore.gtlcore.integration.lowdragmc.misc;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/** Mutable native inventory view. Its source inventories retain ownership and persistence of every item. */
public class MutableItemTransferList implements IItemHandlerModifiable {
    @Getter @Setter private Runnable onContentsChanged = () -> {};
    @Getter @Setter protected Predicate<ItemStack> filter = item -> true;
    private final List<IItemHandlerModifiable> transfers;
    private int totalSlots;

    public MutableItemTransferList() { this(0); }
    public MutableItemTransferList(int initialCapacity) { transfers = new ArrayList<>(initialCapacity); }
    public MutableItemTransferList(Collection<? extends IItemHandlerModifiable> initialTransfers) {
        this(); addTransfers(initialTransfers);
    }

    public boolean addTransfer(@Nullable IItemHandlerModifiable transfer) {
        if (transfer == null) return false;
        transfers.add(transfer); updateSlots(); return true;
    }
    public boolean addTransfers(@Nullable Collection<? extends IItemHandlerModifiable> additions) {
        if (additions == null || additions.isEmpty()) return false;
        for (var transfer : additions) if (transfer != null) transfers.add(transfer);
        updateSlots(); return true;
    }
    public void addTransfer(int index, @NotNull IItemHandlerModifiable transfer) {
        transfers.add(index, transfer); updateSlots();
    }
    public boolean removeTransfer(@Nullable IItemHandlerModifiable transfer) {
        boolean changed = transfers.remove(transfer); if (changed) updateSlots(); return changed;
    }
    public IItemHandlerModifiable removeTransfer(int index) {
        var removed = transfers.remove(index); updateSlots(); return removed;
    }
    public boolean removeTransfersIf(Predicate<IItemHandlerModifiable> predicate) {
        boolean changed = transfers.removeIf(predicate); if (changed) updateSlots(); return changed;
    }
    public void clear() { transfers.clear(); totalSlots = 0; }
    public int getTransferCount() { return transfers.size(); }
    public boolean isEmpty() { return transfers.isEmpty(); }
    @Override public int getSlots() { return totalSlots; }

    @Override public @NotNull ItemStack getStackInSlot(int slot) {
        var pair = find(slot); return pair == null ? ItemStack.EMPTY : pair.handler.getStackInSlot(pair.slot);
    }
    @Override public void setStackInSlot(int slot, @NotNull ItemStack stack) {
        var pair = find(slot);
        if (pair == null) throw new IndexOutOfBoundsException("Composite inventory slot " + slot);
        pair.handler.setStackInSlot(pair.slot, stack); onContentsChanged();
    }
    @Override public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        var pair = find(slot);
        if (pair == null || stack.isEmpty() || !filter.test(stack)) return stack;
        var remainder = pair.handler.insertItem(pair.slot, stack, simulate);
        if (!simulate && remainder.getCount() != stack.getCount()) onContentsChanged();
        return remainder;
    }
    @Override public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        var pair = find(slot);
        if (pair == null || amount <= 0) return ItemStack.EMPTY;
        var extracted = pair.handler.extractItem(pair.slot, amount, simulate);
        if (!simulate && !extracted.isEmpty()) onContentsChanged();
        return extracted;
    }
    @Override public int getSlotLimit(int slot) {
        var pair = find(slot); return pair == null ? 0 : pair.handler.getSlotLimit(pair.slot);
    }
    @Override public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        var pair = find(slot); return pair != null && filter.test(stack) && pair.handler.isItemValid(pair.slot, stack);
    }
    public void onContentsChanged() { onContentsChanged.run(); }

    private void updateSlots() {
        int slots = 0;
        for (var transfer : transfers) slots = Math.addExact(slots, transfer.getSlots());
        totalSlots = slots;
    }
    private @Nullable TransferSlotPair find(int globalSlot) {
        if (globalSlot < 0 || globalSlot >= totalSlots) return null;
        for (var transfer : transfers) {
            int count = transfer.getSlots();
            if (globalSlot < count) return new TransferSlotPair(transfer, globalSlot);
            globalSlot -= count;
        }
        return null;
    }
    private record TransferSlotPair(IItemHandlerModifiable handler, int slot) {}

    public Object createSnapshot() {
        var inventories = new ArrayList<List<ItemStack>>();
        for (var transfer : transfers) {
            var items = new ArrayList<ItemStack>();
            for (int slot = 0; slot < transfer.getSlots(); slot++) items.add(transfer.getStackInSlot(slot).copy());
            inventories.add(items);
        }
        return new Snapshot(List.copyOf(transfers), inventories);
    }
    public void restoreFromSnapshot(Object snapshot) {
        if (!(snapshot instanceof Snapshot saved) || saved.handlers.size() != transfers.size())
            throw new IllegalArgumentException("Snapshot does not match the composite inventory");
        for (int i = 0; i < transfers.size(); i++) {
            if (saved.handlers.get(i) != transfers.get(i) || saved.items.get(i).size() != transfers.get(i).getSlots())
                throw new IllegalArgumentException("Snapshot source inventory changed");
        }
        for (int i = 0; i < transfers.size(); i++) {
            var transfer = transfers.get(i); var items = saved.items.get(i);
            for (int slot = 0; slot < items.size(); slot++) transfer.setStackInSlot(slot, items.get(slot).copy());
        }
        onContentsChanged();
    }
    private record Snapshot(List<IItemHandlerModifiable> handlers, List<List<ItemStack>> items) {}

    public static MutableItemTransferList empty() { return new MutableItemTransferList(); }
    public static MutableItemTransferList of(IItemHandlerModifiable... handlers) {
        return new MutableItemTransferList(List.of(handlers));
    }
}
