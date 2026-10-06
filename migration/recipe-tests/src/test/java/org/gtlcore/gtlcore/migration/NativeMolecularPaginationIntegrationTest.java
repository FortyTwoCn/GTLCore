package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.integration.ae2.gui.MolecularPatternPagination;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import brachy.modularui.value.sync.IntSyncValue;
import brachy.modularui.value.sync.ItemSlotSyncHandler;
import brachy.modularui.value.sync.ModularSyncManager;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widgets.PagedWidget;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NativeMolecularPaginationIntegrationTest {
    @Test void unloadedClientSourcesStillProduceIdenticalReal108SlotPages() {
        var source = new CustomItemStackHandler(108);
        var server = new PanelSyncManager(new ModularSyncManager(false), true);
        var client = new PanelSyncManager(new ModularSyncManager(true), true);
        var serverRoot = MolecularPatternPagination.build(source, 108, server, ignored -> {});
        var clientRoot = MolecularPatternPagination.build(new CustomItemStackHandler(0), 108, client, ignored -> fail());
        var serverPages = (PagedWidget<?>) serverRoot.getChildren().getFirst();
        var clientPages = (PagedWidget<?>) clientRoot.getChildren().getFirst();
        assertEquals(2, serverPages.getPages().size());
        assertEquals(72, serverPages.getPages().getFirst().getChildren().getFirst().getChildren().size());
        assertEquals(36, clientPages.getPages().getLast().getChildren().getFirst().getChildren().size());
        for (int i = 0; i < 108; i++) {
            var a = server.findSyncHandler("molecularPatternSlot", i, ItemSlotSyncHandler.class);
            var b = client.findSyncHandler("molecularPatternSlot", i, ItemSlotSyncHandler.class);
            assertEquals(i, a.getSlot().getSlotIndex()); assertEquals(i, b.getSlot().getSlotIndex());
            assertSame(source, a.getSlot().getItemHandler()); assertNotSame(source, b.getSlot().getItemHandler());
            assertFalse(a.isPhantom()); assertFalse(b.isPhantom());
            assertEquals(i < 72, a.getSlot().isActive());
        }
    }

    @Test void pageSelectionClampsBothDirectionsAndSurvivesNativeCacheRefresh() {
        var manager = new PanelSyncManager(new ModularSyncManager(false), true);
        var root = MolecularPatternPagination.build(new CustomItemStackHandler(108), 108, manager, ignored -> {});
        var pages = (PagedWidget<?>) root.getChildren().getFirst();
        var selection = manager.findSyncHandler("molecularPageIndex", IntSyncValue.class);
        assertTrue(selection.isAllowC2S());
        selection.setIntValue(Integer.MAX_VALUE, true, false);
        assertEquals(1, selection.getIntValue()); assertEquals(1, pages.getCurrentPageIndex());
        assertFalse(selection.updateCacheFromSource(false));
        assertFalse(manager.findSyncHandler("molecularPatternSlot", 0, ItemSlotSyncHandler.class).getSlot().isActive());
        assertTrue(manager.findSyncHandler("molecularPatternSlot", 107, ItemSlotSyncHandler.class).getSlot().isActive());
        selection.setIntValue(-11, true, false);
        assertEquals(0, selection.getIntValue()); assertEquals(0, pages.getCurrentPageIndex());
        assertFalse(selection.updateCacheFromSource(false));
    }

    @Test void realNativeSlotChangesNotifyOnlyAuthoritativeEdits() {
        var manager = new PanelSyncManager(new ModularSyncManager(false), true);
        var changed = new AtomicInteger(-1);
        MolecularPatternPagination.build(new CustomItemStackHandler(108), 108, manager, changed::set);
        var slot = manager.findSyncHandler("molecularPatternSlot", 107, ItemSlotSyncHandler.class).getSlot();
        slot.onSlotChangedReal(net.minecraft.world.item.ItemStack.EMPTY, net.minecraft.world.item.ItemStack.EMPTY, true, false);
        assertEquals(-1, changed.get());
        slot.onSlotChangedReal(net.minecraft.world.item.ItemStack.EMPTY, net.minecraft.world.item.ItemStack.EMPTY, false, true);
        assertEquals(-1, changed.get());
        slot.onSlotChangedReal(net.minecraft.world.item.ItemStack.EMPTY, net.minecraft.world.item.ItemStack.EMPTY, false, false);
        assertEquals(107, changed.get());
        assertThrows(IllegalStateException.class, () -> MolecularPatternPagination.build(new CustomItemStackHandler(0), 108,
                new PanelSyncManager(new ModularSyncManager(false), true), ignored -> {}));
    }
}
