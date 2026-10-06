package org.gtlcore.gtlcore.integration.ae2.async;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

/** Machine-owned output. Mutating the ledger and publishing drained data share this monitor. */
public final class AEAccumulator {
    private final Object2LongOpenHashMap<AEKey> acc = new Object2LongOpenHashMap<>();

    public synchronized void add(AEKey key, long delta) {
        if (delta == 0) return;
        if (key == null || delta < 0) throw new IllegalArgumentException("Invalid accepted AE output");
        acc.put(key, Math.addExact(acc.getLong(key), delta));
    }
    public synchronized void addAll(Object2LongMap<AEKey> data) {
        for (var entry : data.object2LongEntrySet()) {
            if (entry.getKey() == null || entry.getLongValue() <= 0) throw new IllegalArgumentException("Invalid accepted AE output");
            Math.addExact(acc.getLong(entry.getKey()), entry.getLongValue());
        }
        for (var entry : data.object2LongEntrySet()) acc.addTo(entry.getKey(), entry.getLongValue());
    }
    public synchronized void copyTo(Object2LongMap<AEKey> target) {
        for (var entry : acc.object2LongEntrySet()) Math.addExact(target.getLong(entry.getKey()), entry.getLongValue());
        for (var entry : acc.object2LongEntrySet()) target.put(entry.getKey(), target.getLong(entry.getKey()) + entry.getLongValue());
    }
    public synchronized void drainTo(Object2LongOpenHashMap<AEKey> target) { copyTo(target); acc.clear(); }
    public synchronized void clear() { acc.clear(); }
    public synchronized boolean isEmpty() { return acc.isEmpty(); }
}
