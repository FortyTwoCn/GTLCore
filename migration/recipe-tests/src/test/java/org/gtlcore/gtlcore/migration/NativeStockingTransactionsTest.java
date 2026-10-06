package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.integration.ae2.StockingInputTransactions;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Fault injection at the native MEStorage boundary complements real AE drive world tests. */
class NativeStockingTransactionsTest {
    private static final class Storage implements MEStorage {
        final Object2LongMap<AEKey> contents = new Object2LongOpenHashMap<>();
        final List<AEKey> extractedKeys = new ArrayList<>();
        AEKey shortKey;
        boolean refuseRefund;
        @Override public Component getDescription() { return Component.literal("withdrawal fault test"); }
        @Override public long extract(AEKey key, long amount, Actionable action, IActionSource source) {
            long available = contents.getLong(key);
            long extracted = Math.min(amount, available);
            if (action == Actionable.MODULATE) {
                extractedKeys.add(key);
                if (key.equals(shortKey)) extracted = Math.min(extracted, 2);
                contents.put(key, available - extracted);
            }
            return extracted;
        }
        @Override public long insert(AEKey key, long amount, Actionable action, IActionSource source) {
            if (refuseRefund) return 0;
            if (action == Actionable.MODULATE) contents.put(key, Math.addExact(contents.getLong(key), amount));
            return amount;
        }
    }
    private static AEItemKey key(String name) {
        var stack = new ItemStack(Items.IRON_INGOT); stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return AEItemKey.of(stack);
    }
    private static boolean handle(Object2LongMap<AEItemKey> demands, Object2LongMap<AEItemKey> owned,
                                  Storage storage, Object2LongMap<AEKey> refunds, AtomicInteger changed, boolean simulate) {
        return StockingInputTransactions.handle(demands, owned, Object2LongMaps.emptyMap(),
                List.copyOf(storage.contents.keySet()).stream().map(k -> (AEItemKey) k).toList(), Objects::equals,
                k -> storage.extract(k, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty()), simulate,
                storage, IActionSource.empty(), refunds, changed::incrementAndGet);
    }
    @Test void partialWithdrawalReturnsEveryTakenKeyWithoutCommittingLocalOwnershipOrDemands() {
        var a = key("a"); var b = key("b"); var storage = new Storage(); storage.shortKey = b;
        storage.contents.put(a, 8); storage.contents.put(b, 8);
        var demands = new Object2LongOpenHashMap<AEItemKey>(); demands.put(a, 5); demands.put(b, 5);
        var owned = new Object2LongOpenHashMap<AEItemKey>(); owned.put(a, 1); owned.put(b, 1);
        var refunds = new Object2LongOpenHashMap<AEKey>(); var changed = new AtomicInteger();
        assertTrue(handle(demands, owned, storage, refunds, changed, true));
        assertFalse(handle(demands, owned, storage, refunds, changed, false));
        assertEquals(1, owned.getLong(a)); assertEquals(1, owned.getLong(b));
        assertEquals(5, demands.getLong(a)); assertEquals(5, demands.getLong(b));
        assertEquals(8, storage.contents.getLong(a)); assertEquals(8, storage.contents.getLong(b));
        assertTrue(refunds.isEmpty()); assertEquals(1, changed.get());
    }
    @Test void refusedRollbackRetainsEveryExtractedComponentKeyInPersistedRefundBuffer() {
        var a = key("a"); var b = key("b"); var storage = new Storage(); storage.shortKey = b; storage.refuseRefund = true;
        storage.contents.put(a, 8); storage.contents.put(b, 8);
        var demands = new Object2LongOpenHashMap<AEItemKey>(); demands.put(a, 5); demands.put(b, 5);
        var owned = new Object2LongOpenHashMap<AEItemKey>(); owned.put(a, 1); owned.put(b, 1);
        var refunds = new Object2LongOpenHashMap<AEKey>(); var changed = new AtomicInteger();
        assertFalse(handle(demands, owned, storage, refunds, changed, false));
        for (var entry : Object2LongMaps.fastIterable(storage.contents)) assertEquals(8, entry.getLongValue() + refunds.getLong(entry.getKey()));
        assertEquals(1, owned.getLong(a)); assertEquals(1, owned.getLong(b)); assertEquals(2, demands.size());
        assertEquals(1, changed.get());
    }
    @Test void rollbackBufferOverflowRejectsBeforeAnyNetworkExtraction() {
        var a = key("a"); var storage = new Storage(); storage.contents.put(a, 8);
        var demands = new Object2LongOpenHashMap<AEItemKey>(); demands.put(a, 2);
        var owned = new Object2LongOpenHashMap<AEItemKey>();
        var refunds = new Object2LongOpenHashMap<AEKey>(); refunds.put(a, Long.MAX_VALUE);
        var changed = new AtomicInteger();
        assertFalse(handle(demands, owned, storage, refunds, changed, false));
        assertTrue(storage.extractedKeys.isEmpty()); assertEquals(8, storage.contents.getLong(a));
        assertEquals(2, demands.getLong(a)); assertEquals(Long.MAX_VALUE, refunds.getLong(a)); assertEquals(0, changed.get());
    }
    @Test void successfulCommitKeepsLongQuantitiesAndDistinctComponentsExact() {
        var a = key("a"); var b = key("b"); long large = 9_007_199_254_740_993L;
        var storage = new Storage(); storage.contents.put(a, large); storage.contents.put(b, 10);
        var demands = new Object2LongOpenHashMap<AEItemKey>(); demands.put(a, large); demands.put(b, 11);
        var owned = new Object2LongOpenHashMap<AEItemKey>(); owned.put(b, 1);
        var refunds = new Object2LongOpenHashMap<AEKey>(); var changed = new AtomicInteger();
        assertTrue(handle(demands, owned, storage, refunds, changed, false));
        assertTrue(demands.isEmpty()); assertTrue(owned.isEmpty()); assertTrue(refunds.isEmpty());
        assertEquals(0, storage.contents.getLong(a)); assertEquals(0, storage.contents.getLong(b)); assertEquals(1, changed.get());
    }
}
