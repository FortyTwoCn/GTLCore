package org.gtlcore.gtlcore.integration.ae2;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.ArrayDeque;
import java.util.Deque;

/** Separate owned ledgers allow refunds of the same component key to exceed a single long in total. */
public final class AERefundQueue implements INBTSerializable<ListTag> {
    private final Deque<Object2LongOpenHashMap<AEKey>> ledgers = new ArrayDeque<>();
    private final Runnable changed;

    public AERefundQueue(Runnable changed) { this.changed = changed; }

    public boolean isEmpty() { return ledgers.isEmpty(); }

    private static Object2LongOpenHashMap<AEKey> snapshot(Object2LongMap<? extends AEKey> source) {
        var copy = new Object2LongOpenHashMap<AEKey>();
        for (var entry : Object2LongMaps.fastIterable(source)) {
            if (entry.getKey() == null || entry.getLongValue() <= 0) throw new IllegalArgumentException("Invalid owned AE refund");
            copy.put(entry.getKey(), entry.getLongValue());
        }
        return copy;
    }

    /** Validate both inventories before transferring ownership out of the disappearing slot. */
    public void takeOwnership(Object2LongMap<? extends AEKey> items, Object2LongMap<? extends AEKey> fluids) {
        var itemCopy = snapshot(items);
        var fluidCopy = snapshot(fluids);
        if (itemCopy.isEmpty() && fluidCopy.isEmpty()) return;
        if (!itemCopy.isEmpty()) ledgers.add(itemCopy);
        if (!fluidCopy.isEmpty()) ledgers.add(fluidCopy);
        items.clear(); fluids.clear(); changed.run();
    }

    /** Copy ownership for a machine drop without changing its live inventories. */
    public void appendSnapshot(Object2LongMap<? extends AEKey> source) {
        var copy = snapshot(source);
        if (!copy.isEmpty()) { ledgers.add(copy); changed.run(); }
    }

    /** Move only what fits into the shared output ledger; leave every remainder persisted here. */
    public boolean drainInto(Object2LongMap<AEKey> destination) {
        boolean moved = false;
        for (var ledgerIterator = ledgers.iterator(); ledgerIterator.hasNext();) {
            var ledger = ledgerIterator.next();
            for (var entries = ledger.object2LongEntrySet().iterator(); entries.hasNext();) {
                var entry = entries.next();
                long existing = destination.getLong(entry.getKey());
                if (existing < 0) throw new IllegalStateException("Negative AE refund destination");
                long accepted = Math.min(entry.getLongValue(), Long.MAX_VALUE - existing);
                if (accepted == 0) continue;
                destination.put(entry.getKey(), Math.addExact(existing, accepted));
                if (accepted == entry.getLongValue()) entries.remove();
                else entry.setValue(entry.getLongValue() - accepted);
                moved = true;
            }
            if (ledger.isEmpty()) ledgerIterator.remove();
        }
        if (moved) changed.run();
        return moved;
    }

    @Override public ListTag serializeNBT(HolderLookup.Provider registries) {
        var result = new ListTag();
        for (var ledger : ledgers) {
            var entries = new ListTag();
            for (var entry : ledger.object2LongEntrySet()) {
                var tag = new CompoundTag(); tag.put("key", entry.getKey().toTagGeneric(registries));
                tag.putLong("value", entry.getLongValue()); entries.add(tag);
            }
            var tag = new CompoundTag(); tag.put("entries", entries); result.add(tag);
        }
        return result;
    }

    @Override public void deserializeNBT(HolderLookup.Provider registries, ListTag tags) {
        var decoded = new ArrayDeque<Object2LongOpenHashMap<AEKey>>();
        for (var value : tags) {
            if (!(value instanceof CompoundTag ledger) || !(ledger.get("entries") instanceof ListTag entries))
                throw new IllegalArgumentException("Invalid AE refund ledger");
            var map = new Object2LongOpenHashMap<AEKey>();
            for (var element : entries) {
                if (!(element instanceof CompoundTag entry)) throw new IllegalArgumentException("Invalid AE refund entry");
                var key = AEKey.fromTagGeneric(registries, entry.getCompound("key"));
                long amount = entry.getLong("value");
                if (key == null || amount <= 0) throw new IllegalArgumentException("Invalid persisted AE refund");
                map.put(key, Math.addExact(map.getLong(key), amount));
            }
            if (!map.isEmpty()) decoded.add(map);
        }
        ledgers.clear(); ledgers.addAll(decoded);
    }
}
