package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.integration.ae2.throughput.ThroughputMonitorStorageTracker;
import org.gtlcore.gtlcore.integration.ae2.crafting.ManualCraftingInventoryLock;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import appeng.api.storage.SupplierStorage;
import appeng.me.storage.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeThroughputStorageTest {
    private static final IActionSource SOURCE = IActionSource.empty();
    private java.lang.reflect.Field clock;
    private long previousTick;
    @BeforeEach void controlledNativeWorldClock() throws ReflectiveOperationException {
        // The Minecraft JUnit bootstrap has no ticking world. Production intentionally ignores tick zero.
        clock = appeng.hooks.ticking.TickHandler.class.getDeclaredField("tickCounter"); clock.setAccessible(true);
        previousTick = clock.getLong(appeng.hooks.ticking.TickHandler.instance()); clock.setLong(appeng.hooks.ticking.TickHandler.instance(), 100);
    }
    @AfterEach void restoreNativeClock() throws ReflectiveOperationException { clock.setLong(appeng.hooks.ticking.TickHandler.instance(), previousTick); }
    private static final class Stock implements MEStorage {
        final AEKey key; long count;
        Stock(AEKey key, long count) { this.key = key; this.count = count; }
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            if (!key.equals(what)) return 0; long accepted = Math.min(amount, Long.MAX_VALUE - count);
            if (mode == Actionable.MODULATE) count += accepted; return accepted;
        }
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            if (!key.equals(what)) return 0; long extracted = Math.min(amount, count);
            if (mode == Actionable.MODULATE) count -= extracted; return extracted;
        }
        public void getAvailableStacks(KeyCounter counter) { counter.add(key, count); }
        public Component getDescription() { return Component.literal("Controlled native storage boundary"); }
    }
    private static AEKey key() {
        var stack = new ItemStack(Items.IRON_INGOT); stack.set(DataComponents.CUSTOM_NAME, Component.literal("native throughput component key")); return AEItemKey.of(stack);
    }
    @Test void nativeWirelessSupplierObservesUnderlyingNetworkTransactionsExactlyOnce() {
        var key = key(); var stock = new Stock(key, 0);
        var network = new NetworkStorage(); network.mount(0, stock);
        var root = new SupplierStorage(() -> network); var deltas = new ArrayList<Long>();
        var listener = new ThroughputMonitorStorageTracker.Listener() {
            public AEKey getTrackedKey() { return key; }
            public void recordThroughput(long delta, long tick) { deltas.add(delta); }
        };
        ThroughputMonitorStorageTracker.register(root, listener);
        try {
            ThroughputMonitorStorageTracker.refreshVisibleStorageLinks(root);
            assertEquals(17, network.insert(key, 17, Actionable.SIMULATE, SOURCE)); assertTrue(deltas.isEmpty());
            assertEquals(17, network.insert(key, 17, Actionable.MODULATE, SOURCE));
            assertEquals(5, root.extract(key, 5, Actionable.MODULATE, SOURCE));
            assertEquals(List.of(17L, -5L), deltas); assertEquals(12, stock.count);
            assertFalse(ThroughputMonitorStorageTracker.hasPendingOperation());
        } finally { ThroughputMonitorStorageTracker.unregister(listener); }
    }
    @Test void nativeWirelessSupplierSwitchAndDisconnectDiscardOldNetworkLinks() {
        var key = key(); var oldNetwork = new NetworkStorage(); oldNetwork.mount(0, new Stock(key, 0));
        var newNetwork = new NetworkStorage(); newNetwork.mount(0, new Stock(key, 0));
        var supplied = new java.util.concurrent.atomic.AtomicReference<MEStorage>(oldNetwork);
        var root = new SupplierStorage(supplied::get); var deltas = new ArrayList<Long>();
        var listener = new ThroughputMonitorStorageTracker.Listener() {
            public AEKey getTrackedKey() { return key; }
            public void recordThroughput(long delta, long tick) { deltas.add(delta); }
        };
        ThroughputMonitorStorageTracker.register(root, listener);
        try {
            ThroughputMonitorStorageTracker.refreshVisibleStorageLinks(root);
            oldNetwork.insert(key, 31, Actionable.MODULATE, SOURCE); assertEquals(List.of(31L), deltas);
            supplied.set(newNetwork); root.getAvailableStacks();
            oldNetwork.insert(key, 9, Actionable.MODULATE, SOURCE); assertEquals(List.of(31L), deltas);
            newNetwork.insert(key, 17, Actionable.MODULATE, SOURCE); assertEquals(List.of(31L, 17L), deltas);
            supplied.set(null); assertEquals(0, root.getAvailableStacks().get(key));
            newNetwork.insert(key, 3, Actionable.MODULATE, SOURCE); assertEquals(List.of(31L, 17L), deltas);
        } finally { ThroughputMonitorStorageTracker.unregister(listener); }
    }
    @Test void actualNativeNestedNetworksDeduplicateSharedListenersAndIgnoreSimulations() {
        var key = key(); var stock = new Stock(key, 0); var child = new NetworkStorage(); child.mount(0, stock);
        var root = new NetworkStorage(); root.mount(0, child); var deltas = new ArrayList<Long>();
        var listener = new ThroughputMonitorStorageTracker.Listener() {
            public AEKey getTrackedKey() { return key; }
            public void recordThroughput(long delta, long tick) { deltas.add(delta); }
        };
        ThroughputMonitorStorageTracker.register(root, listener); ThroughputMonitorStorageTracker.register(child, listener);
        try {
            ThroughputMonitorStorageTracker.refreshVisibleStorageLinks(root);
            assertEquals(100, root.insert(key, 100, Actionable.SIMULATE, SOURCE)); assertEquals(0, stock.count); assertTrue(deltas.isEmpty());
            assertEquals(100, root.insert(key, 100, Actionable.MODULATE, SOURCE)); assertEquals(List.of(100L), deltas);
            assertEquals(40, root.extract(key, 40, Actionable.SIMULATE, SOURCE)); assertEquals(List.of(100L), deltas);
            assertEquals(40, root.extract(key, 40, Actionable.MODULATE, SOURCE)); assertEquals(List.of(100L, -40L), deltas);
            assertEquals(0, root.extract(AEItemKey.of(Items.IRON_INGOT), 60, Actionable.MODULATE, SOURCE));
            assertEquals(60, stock.count); assertFalse(ThroughputMonitorStorageTracker.hasPendingOperation());
        } finally { ThroughputMonitorStorageTracker.unregister(listener); }
    }
    @Test void nativeCompositeTopologyChangesDiscardOldVisibleParentsAndAttachCurrentStorage() {
        var key = key(); var oldStock = new Stock(key, 0); var oldNetwork = new NetworkStorage(); oldNetwork.mount(0, oldStock);
        var newStock = new Stock(key, 0); var newNetwork = new NetworkStorage(); newNetwork.mount(0, newStock);
        var root = new CompositeStorage(Map.of(AEKeyType.items(), oldNetwork)); var deltas = new ArrayList<Long>();
        var listener = new ThroughputMonitorStorageTracker.Listener() {
            public AEKey getTrackedKey() { return key; }
            public void recordThroughput(long delta, long tick) { deltas.add(delta); }
        };
        ThroughputMonitorStorageTracker.register(root, listener);
        try {
            ThroughputMonitorStorageTracker.refreshVisibleStorageLinks(root);
            assertEquals(31, oldNetwork.insert(key, 31, Actionable.MODULATE, SOURCE)); assertEquals(List.of(31L), deltas);
            root.setStorages(Map.of(AEKeyType.items(), newNetwork));
            oldNetwork.insert(key, 9, Actionable.MODULATE, SOURCE); assertEquals(List.of(31L), deltas);
            newNetwork.insert(key, 17, Actionable.MODULATE, SOURCE); assertEquals(List.of(31L, 17L), deltas);
        } finally { ThroughputMonitorStorageTracker.unregister(listener); }
    }
    @Test void nativeAvailabilityMixinKeepsReservedStockWhileMonitoringActualAllowedExtraction() {
        var key = key(); var stock = new Stock(key, 13); var network = new NetworkStorage(); network.mount(0, stock);
        var requested = new KeyCounter(); requested.add(key, 7); var reservation = ManualCraftingInventoryLock.tryAcquire(network, requested, SOURCE);
        assertNotNull(reservation); var deltas = new ArrayList<Long>();
        var listener = new ThroughputMonitorStorageTracker.Listener() {
            public AEKey getTrackedKey() { return key; }
            public void recordThroughput(long delta, long tick) { deltas.add(delta); }
        };
        ThroughputMonitorStorageTracker.register(network, listener);
        try {
            assertEquals(6, network.extract(key, 100, Actionable.SIMULATE, SOURCE)); assertTrue(deltas.isEmpty());
            assertEquals(6, network.extract(key, 100, Actionable.MODULATE, SOURCE)); assertEquals(7, stock.count); assertEquals(List.of(-6L), deltas);
            assertEquals(0, network.extract(key, 100, Actionable.MODULATE, SOURCE));
            reservation.close(); assertEquals(7, network.extract(key, 100, Actionable.MODULATE, SOURCE)); assertEquals(List.of(-6L, -7L), deltas);
        } finally { reservation.close(); ThroughputMonitorStorageTracker.unregister(listener); }
    }
}
