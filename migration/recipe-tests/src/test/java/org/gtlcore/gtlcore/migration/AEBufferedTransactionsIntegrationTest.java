package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.integration.ae2.AEBufferedSlotTransactions;
import com.gregtechceu.gtceu.integration.ae2.slot.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Native slots with a bounded MEStorage fixture isolate quantities larger than physical AE test cells. */
class AEBufferedTransactionsIntegrationTest {
    private static final long LARGE = 9_007_199_254_740_993L;
    private static final IActionSource SOURCE = IActionSource.empty();
    private static final class BoundedStorage implements MEStorage {
        private final KeyCounter stored = new KeyCounter();
        private long capacity;
        private BoundedStorage(long capacity) { this.capacity = capacity; }
        @Override public Component getDescription() { return Component.literal("bounded owned-buffer transaction fixture"); }
        @Override public void getAvailableStacks(KeyCounter result) { result.addAll(stored); }
        @Override public long insert(AEKey key, long amount, Actionable action, IActionSource source) {
            MEStorage.checkPreconditions(key, amount, action, source);
            long accepted = Math.min(amount, capacity);
            if (action == Actionable.MODULATE) { stored.add(key, accepted); capacity -= accepted; }
            return accepted;
        }
        @Override public long extract(AEKey key, long amount, Actionable action, IActionSource source) {
            MEStorage.checkPreconditions(key, amount, action, source);
            long extracted = Math.min(amount, stored.get(key));
            if (action == Actionable.MODULATE) { stored.remove(key, extracted); capacity += extracted; }
            return extracted;
        }
    }
    @Test void nativeItemBufferKeepsRefusedAndPartialReturnsAndFlushesLongStockExactlyOnce() {
        var oldItem = new ItemStack(Items.IRON_INGOT); oldItem.set(DataComponents.CUSTOM_NAME, Component.literal("owned components"));
        var oldKey = AEItemKey.of(oldItem); var newKey = AEItemKey.of(Items.GOLD_INGOT);
        var slot = new ExportOnlyAEItemSlot(new GenericStack(newKey, 3_000_000_000L), new GenericStack(oldKey, LARGE));
        var storage = new BoundedStorage(0);
        AEBufferedSlotTransactions.balance(slot, storage, SOURCE);
        assertEquals(new GenericStack(oldKey, LARGE), slot.getStock()); assertEquals(0, storage.stored.get(oldKey));
        storage.capacity = 17; AEBufferedSlotTransactions.balance(slot, storage, SOURCE);
        assertEquals(LARGE - 17, slot.getStock().amount()); assertEquals(17, storage.stored.get(oldKey));
        storage.capacity = Long.MAX_VALUE; storage.stored.add(newKey, 3_000_000_000L);
        AEBufferedSlotTransactions.balance(slot, storage, SOURCE);
        assertEquals(LARGE, storage.stored.get(oldKey)); assertEquals(new GenericStack(newKey, 3_000_000_000L), slot.getStock());
        assertEquals(0, storage.stored.get(newKey));
        AEBufferedSlotTransactions.flush(slot, storage, SOURCE); AEBufferedSlotTransactions.flush(slot, storage, SOURCE);
        assertNull(slot.getStock()); assertEquals(3_000_000_000L, storage.stored.get(newKey));
    }
    @Test void nativeFluidBufferKeepsExcessAndItsComponentsAcrossPartialReturnsAndCapabilityDrain() {
        var fluid = new FluidStack(Fluids.WATER, 1); fluid.set(DataComponents.CUSTOM_NAME, Component.literal("owned fluid components"));
        var key = AEFluidKey.of(fluid); var slot = new ExportOnlyAEFluidSlot(new GenericStack(key, LARGE - 19), new GenericStack(key, LARGE));
        var storage = new BoundedStorage(7); AEBufferedSlotTransactions.balance(slot, storage, SOURCE);
        assertEquals(LARGE - 7, slot.getStock().amount()); assertEquals(7, storage.stored.get(key));
        assertTrue(slot.drain(new FluidStack(Fluids.WATER, 3), FluidAction.EXECUTE).isEmpty());
        assertEquals(3, slot.drain(fluid.copyWithAmount(3), FluidAction.SIMULATE).getAmount());
        assertEquals(LARGE - 7, slot.getStock().amount());
        assertEquals(3, slot.drain(fluid.copyWithAmount(3), FluidAction.EXECUTE).getAmount());
        storage.capacity = Long.MAX_VALUE;
        AEBufferedSlotTransactions.flush(slot, storage, SOURCE); AEBufferedSlotTransactions.flush(slot, storage, SOURCE);
        assertNull(slot.getStock()); assertEquals(LARGE - 3, storage.stored.get(key));
    }
}
