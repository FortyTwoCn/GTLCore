package org.gtlcore.gtlcore.integration.ae2;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.StorageHelper;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import org.jetbrains.annotations.Nullable;

/** Bounded native powered insertion, retaining all output rejected by the network. */
public final class AEBufferedOutput {
    private AEBufferedOutput() {}

    public static <T extends AEKey> boolean flush(Object2LongMap<T> buffer, @Nullable IGrid network, IActionSource source) {
        if (buffer.isEmpty() || network == null) return false;
        var inventory = network.getStorageService().getInventory(); var energy = network.getEnergyService();
        int operations = 0, failures = 0; boolean changed = false;
        for (var it = Object2LongMaps.fastIterator(buffer); it.hasNext() && operations < 64;) {
            var entry = it.next(); long amount = entry.getLongValue();
            if (amount <= 0) { it.remove(); changed = true; continue; }
            long inserted = StorageHelper.poweredInsert(energy, inventory, entry.getKey(), amount, source);
            operations++;
            if (inserted > 0) {
                changed = true; failures = 0;
                long left = amount - inserted;
                if (left == 0) it.remove(); else entry.setValue(left);
            } else if (++failures >= 5) break;
        }
        return changed;
    }
}
