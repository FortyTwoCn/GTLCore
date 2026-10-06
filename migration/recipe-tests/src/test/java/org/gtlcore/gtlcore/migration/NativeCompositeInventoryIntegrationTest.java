package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.integration.lowdragmc.misc.MutableItemTransferList;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NativeCompositeInventoryIntegrationTest {
    @Test void nativeCompositeDelegatesComponentsAndSimulationAcrossMutableSlotBoundaries() {
        var first = new CustomItemStackHandler(2); var second = new CustomItemStackHandler(3);
        var combined = new MutableItemTransferList(List.of(first, second)); var changes = new AtomicInteger();
        combined.setOnContentsChanged(changes::incrementAndGet);
        var stack = new ItemStack(Items.DIAMOND, 7); stack.set(DataComponents.CUSTOM_NAME, Component.literal("native composite key"));
        assertEquals(5, combined.getSlots());
        assertTrue(combined.insertItem(2, stack, true).isEmpty()); assertTrue(second.getStackInSlot(0).isEmpty());
        assertEquals(0, changes.get());
        assertTrue(combined.insertItem(2, stack, false).isEmpty()); assertEquals(1, changes.get());
        assertTrue(ItemStack.matches(stack, second.getStackInSlot(0)));
        assertEquals(3, combined.extractItem(2, 3, true).getCount()); assertEquals(7, second.getStackInSlot(0).getCount());
        var extracted = combined.extractItem(2, 3, false);
        assertEquals(stack.getComponents(), extracted.getComponents()); assertEquals(4, second.getStackInSlot(0).getCount());
        combined.removeTransfer(first); assertEquals(3, combined.getSlots()); assertEquals(4, combined.getStackInSlot(0).getCount());
        assertTrue(combined.getStackInSlot(-1).isEmpty()); assertFalse(combined.isItemValid(-1, stack));
        assertTrue(ItemStack.matches(stack, combined.insertItem(3, stack, false)));
        assertThrows(IndexOutOfBoundsException.class, () -> combined.setStackInSlot(-1, stack));
        combined.clear(); assertEquals(0, combined.getSlots()); assertEquals(4, second.getStackInSlot(0).getCount());
    }

    @Test void nativeSnapshotsRestoreCopiesAndRejectChangedSourceOrderBeforeAnyMutation() {
        var first = new CustomItemStackHandler(1); var second = new CustomItemStackHandler(1);
        first.setStackInSlot(0, new ItemStack(Items.DIAMOND, 5)); second.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 3));
        var combined = new MutableItemTransferList(List.of(first, second)); var snapshot = combined.createSnapshot();
        combined.extractItem(0, 3, false); combined.restoreFromSnapshot(snapshot);
        assertEquals(5, first.getStackInSlot(0).getCount());
        combined.extractItem(0, 4, false); combined.restoreFromSnapshot(snapshot);
        assertEquals(5, first.getStackInSlot(0).getCount());
        combined.clear(); combined.addTransfer(second); combined.addTransfer(first);
        first.setStackInSlot(0, new ItemStack(Items.DIAMOND, 2));
        assertThrows(IllegalArgumentException.class, () -> combined.restoreFromSnapshot(snapshot));
        assertEquals(2, first.getStackInSlot(0).getCount()); assertEquals(3, second.getStackInSlot(0).getCount());
    }
}
