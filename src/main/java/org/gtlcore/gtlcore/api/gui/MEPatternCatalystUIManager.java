package org.gtlcore.gtlcore.api.gui;

import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.api.transfer.fluid.FluidHandlerList;
import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;
import com.gregtechceu.gtceu.common.mui.widgets.PopupPanel;
import brachy.modularui.api.IPanelHandler;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.ItemSlotSyncHandler;
import brachy.modularui.value.sync.FluidSlotSyncHandler;
import brachy.modularui.value.sync.SyncHandlers;
import brachy.modularui.widgets.layout.Grid;
import brachy.modularui.widgets.slot.ItemSlot;
import brachy.modularui.widgets.slot.FluidSlot;
import brachy.modularui.widgets.slot.SlotGroup;
import brachy.modularui.widgets.textfield.TextFieldWidget;
import java.util.function.IntConsumer;

/** Native synchronized, draggable per-pattern catalyst panels, opened by middle-clicking a pattern. */
public final class MEPatternCatalystUIManager {
    private final IPanelHandler[] panels;
    private int previous = -1;

    public MEPatternCatalystUIManager(PanelSyncManager sync, CustomItemStackHandler[] items, FluidHandlerList[] fluids,
                                      byte[] cacheCount, IntConsumer changed) {
        panels = new IPanelHandler[items.length];
        for (int i = 0; i < panels.length; i++) {
            int slot = i;
            panels[i] = sync.syncedPanel("patternCatalysts" + slot, true, (manager, handler) -> {
                var count = SyncHandlers.string(() -> Byte.toString(cacheCount[slot]), text -> {
                    try {
                        int parsed = Integer.parseInt(text);
                        byte bounded = (byte) Math.clamp(parsed, 1, Byte.MAX_VALUE);
                        if (cacheCount[slot] != bounded) { cacheCount[slot] = bounded; changed.accept(slot); }
                    } catch (NumberFormatException ignored) {}
                }).allowC2S();
                manager.syncValue("patternRecipeCacheCount", count);
                var group = new SlotGroup("patternCatalystItems" + slot, 3, false);
                manager.registerSlotGroup(group);
                return PopupPanel.createPopupPanel("patternCatalystsPanel" + slot, 142, 182).draggable(true)
                        .child(Text.lang("gui.gtlcore.pattern_buffer_gui_1").asWidget().pos(4, 4))
                        .child(new TextFieldWidget().value(count).size(70, 16).pos(4, 20))
                        .child(Text.lang("gui.gtlcore.pattern_buffer_gui_2").asWidget().pos(4, 40))
                        .child(new Grid().pos(4, 54).size(54).minElementMargin(0, 0).minColWidth(18).minRowHeight(18)
                                .gridOfSizeWidth(items[slot].getSlots(), 3, (x, y, index) -> {
                                    var value = new ItemSlotSyncHandler(SyncHandlers.itemSlot(items[slot], index).slotGroup(group));
                                    manager.syncValue("patternCatalystItem", index, value);
                                    return new ItemSlot().syncHandler(value);
                                }))
                        .child(Text.lang("gui.gtlcore.pattern_buffer_gui_3").asWidget().pos(4, 112))
                        .child(new Grid().pos(4, 126).size(54).minElementMargin(0, 0).minColWidth(18).minRowHeight(18)
                                .gridOfSizeWidth(fluids[slot].getTanks(), 3, (x, y, index) -> {
                                    var tank = (CustomFluidTank) fluids[slot].handlers[index];
                                    var value = new FluidSlotSyncHandler(tank);
                                    manager.syncValue("patternCatalystFluid", index, value);
                                    return new FluidSlot().syncHandler(value);
                                }));
            });
        }
    }

    public void toggleFor(int slot) {
        if (slot < 0 || slot >= panels.length) return;
        if (previous != slot && previous >= 0) panels[previous].closePanel();
        panels[slot].togglePanel();
        previous = slot;
    }
}
