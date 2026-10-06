package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.integration.ae2.TagFilteredStocking;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEItemSlot;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.material.Fluids;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class TagFilteredStockingIntegrationTest {
    private static final long LARGE = 9_007_199_254_740_993L;
    private static class Storage implements MEStorage {
        private final KeyCounter available = new KeyCounter(); private final Set<AEKey> denied = new HashSet<>();
        @Override public Component getDescription() { return Component.literal("bounded tag stock fixture"); }
        @Override public void getAvailableStacks(KeyCounter result) { result.addAll(available); }
        @Override public long extract(AEKey key, long amount, Actionable action, IActionSource source) {
            MEStorage.checkPreconditions(key, amount, action, source); assertEquals(Actionable.SIMULATE, action);
            return denied.contains(key) ? 0 : Math.min(available.get(key), amount);
        }
    }
    @Test void countSortKeepsTheLargestSixteenAccessibleComponentKeysWithoutTruncatingOrConsumingLongStock() {
        var network = new Storage(); var keys = new ArrayList<AEKey>();
        for (int i = 0; i < 20; i++) {
            var item = new ItemStack(Items.IRON_INGOT); item.set(DataComponents.CUSTOM_NAME, Component.literal("tag key " + i));
            var key = AEItemKey.of(item); keys.add(key); network.available.set(key, LARGE + i);
        }
        network.denied.add(keys.getLast());
        var result = TagFilteredStocking.select(network, IActionSource.empty(), "", "", true, AEItemKey.class::isInstance, 16);
        assertEquals(16, result.size()); assertEquals(new GenericStack(keys.get(18), LARGE + 18), result.getFirst());
        assertEquals(new GenericStack(keys.get(3), LARGE + 3), result.getLast());
        assertEquals(20, network.available.size()); assertEquals(LARGE + 19, network.available.get(keys.getLast()));
    }
    @Test void nativeFluidSelectionKeepsItemAndFluidKeyTypesSeparateAndRetainsComponentIdentity() {
        var network = new Storage(); var water = new net.neoforged.neoforge.fluids.FluidStack(Fluids.WATER, 1);
        water.set(DataComponents.CUSTOM_NAME, Component.literal("tagged fluid component")); var key = AEFluidKey.of(water);
        network.available.set(key, LARGE); network.available.set(AEItemKey.of(Items.IRON_INGOT), Long.MAX_VALUE);
        var result = TagFilteredStocking.select(network, IActionSource.empty(), "", "", false, AEFluidKey.class::isInstance, 16);
        assertEquals(List.of(new GenericStack(key, LARGE)), result); assertEquals(LARGE, network.available.get(key));
    }
    @Test void staleOrEmptyDisplaysInvalidateSnapshotsAndNeverBecomeNegativeAfterRealExtraction() {
        var key = AEItemKey.of(Items.IRON_INGOT); var slot = new ExportOnlyAEItemSlot(new GenericStack(key, 1), new GenericStack(key, 2));
        var changed = new AtomicInteger(); slot.setOnContentsChanged(changed::incrementAndGet);
        TagFilteredStocking.afterExtraction(slot, key, 5); assertNull(slot.getStock()); assertEquals(1, changed.get());
        TagFilteredStocking.afterExtraction(slot, key, 5); assertNull(slot.getStock()); assertEquals(2, changed.get());
    }
}
