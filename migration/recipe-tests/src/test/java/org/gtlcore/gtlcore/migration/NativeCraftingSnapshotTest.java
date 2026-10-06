package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.config.ConfigHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import appeng.crafting.inv.NetworkCraftingSimulationState;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class NativeCraftingSnapshotTest {
    private static IStorageService service(KeyCounter cache, MEStorage inventory) {
        return (IStorageService) Proxy.newProxyInstance(IStorageService.class.getClassLoader(),
                new Class<?>[]{IStorageService.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getCachedInventory" -> cache;
                    case "getInventory" -> inventory;
                    default -> throw new UnsupportedOperationException(method.toString());
                });
    }
    @Test void nativeWorkerUsesCapturedExtractabilityWithoutTouchingWorldStorage() {
        boolean previous = ConfigHolder.INSTANCE.ae2CraftingSimulatedExtraction;
        ConfigHolder.INSTANCE.ae2CraftingSimulatedExtraction = true;
        try {
            var key = AEItemKey.of(Items.IRON_INGOT); var cache = new KeyCounter(); cache.set(key, Long.MAX_VALUE);
            var calls = new AtomicInteger(); Thread owner = Thread.currentThread();
            MEStorage inventory = new MEStorage() {
                public Component getDescription() { return Component.literal("physical inventory"); }
                public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
                    assertSame(owner, Thread.currentThread()); assertEquals(Actionable.SIMULATE, mode);
                    calls.incrementAndGet(); return Math.min(amount, 7);
                }
            };
            var snapshot = new NetworkCraftingSimulationState(service(cache, inventory), IActionSource.empty());
            assertEquals(1, calls.get()); cache.set(key, 0);
            long extracted = CompletableFuture.supplyAsync(() -> snapshot.extract(key, Long.MAX_VALUE, Actionable.MODULATE)).join();
            assertEquals(7, extracted); assertEquals(1, calls.get()); assertEquals(0, cache.get(key));
            assertEquals(0, snapshot.extract(key, 1, Actionable.SIMULATE));
        } finally { ConfigHolder.INSTANCE.ae2CraftingSimulatedExtraction = previous; }
    }
    @Test void ordinaryNativeSnapshotRemainsDetachedAndKeepsLargeCachedAmounts() {
        boolean previous = ConfigHolder.INSTANCE.ae2CraftingSimulatedExtraction;
        ConfigHolder.INSTANCE.ae2CraftingSimulatedExtraction = false;
        try {
            var key = AEItemKey.of(Items.GOLD_INGOT); var cache = new KeyCounter(); cache.set(key, 9_007_199_254_740_993L);
            MEStorage inventory = new MEStorage() { public Component getDescription() { return Component.literal("cached"); } };
            var snapshot = new NetworkCraftingSimulationState(service(cache, inventory), IActionSource.empty());
            cache.reset();
            assertEquals(9_007_199_254_740_993L, snapshot.extract(key, Long.MAX_VALUE, Actionable.SIMULATE));
        } finally { ConfigHolder.INSTANCE.ae2CraftingSimulatedExtraction = previous; }
    }
}
