package org.gtlcore.gtlcore.api.gui;

import com.gregtechceu.gtceu.api.cover.IMuiCover;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.mui.factory.CoverUIFactory;
import com.gregtechceu.gtceu.common.mui.GTGuiTextures;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.value.sync.InteractionSyncHandler;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.Dialog;
import brachy.modularui.widgets.layout.Flow;
import brachy.modularui.widgets.slot.ItemSlot;
import brachy.modularui.widgets.slot.ModularSlot;
import brachy.modularui.widgets.slot.SlotGroup;

/** Six real cover slots and native cover menus replace the old directional configuration page. */
public final class NativeCoverUI {
    public static ButtonWidget<?> button(MetaMachine machine, ModularPanel<?> parent, PanelSyncManager manager) {
        var handler = manager.syncedPanel("gtlcoreCoverSettings", true,
                (sync, panelHandler) -> panel(machine, sync).relative(parent).leftRel(0f, -4, 1f));
        return new ButtonWidget<>().name("gtlcoreCoverSettingsButton").size(18).overlay(GTGuiTextures.TOOL_COVER_SETTINGS)
                .tooltip(t -> t.addLine(Component.translatable("gtlcore.gui.cover_settings")))
                .onMousePressed((context, mouse) -> { handler.openPanel(); return true; });
    }

    private static ModularPanel<?> panel(MetaMachine machine, PanelSyncManager manager) {
        var covers = machine.getCoverContainer();
        var slots = new NativeCoverInventory(covers, () -> manager.getPlayer() instanceof ServerPlayer player ? player : null);
        var group = new SlotGroup("gtlcoreCoverSlots", 6);
        manager.registerSlotGroup(group);
        var column = Flow.column().widthRel(1).coverChildrenHeight().padding(5).childPadding(3);
        column.child(Text.lang("gtlcore.gui.cover_settings").asWidget());
        for (Direction side : Direction.values()) {
            column.child(Flow.row().coverChildren().childPadding(3)
                    .child(Text.lang("gtlcore.gui.cover_side." + side.getSerializedName()).asWidget().width(65))
                    .child(new ItemSlot().slot(new ModularSlot(slots, side.get3DDataValue()).slotGroup(group)))
                    .child(new ButtonWidget<>().size(18).overlay(GTGuiTextures.TOOL_COVER_SETTINGS)
                            .tooltip(t -> t.addLine(Component.translatable("gtlcore.gui.cover_settings")))
                            .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouse -> {
                                if (mouse.isClient() || !(manager.getPlayer() instanceof ServerPlayer player)) return;
                                var cover = covers.getCoverAtSide(side);
                                if (cover instanceof IMuiCover uiCover && !uiCover.isRemoved()) CoverUIFactory.INSTANCE.open(player, uiCover);
                            }))));
        }
        return new Dialog<>("gtlcoreCoverSettings").size(145, 160).draggable(true).closeOnOutOfBoundsClick(true)
                .disablePanelsBelow(false).child(column);
    }

    private NativeCoverUI() {}
}
