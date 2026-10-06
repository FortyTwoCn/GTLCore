package org.gtlcore.gtlcore.api.gui;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.common.mui.GTGuiTextures;
import com.gregtechceu.gtceu.common.mui.widgets.PopupPanel;
import net.minecraft.network.chat.Component;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.value.BoolValue;
import brachy.modularui.value.sync.IntSyncValue;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.ListWidget;
import brachy.modularui.widgets.ToggleButton;

/** A native, server-owned recipe-mode selector retaining the original six visible rows. */
public final class MachineModeConfigurator extends ListWidget<brachy.modularui.api.widget.IWidget, MachineModeConfigurator> {
    public static final String PANEL = "gtlcoreRecipeModes";
    public static final String SELECT = "gtlcoreSelectRecipeMode";
    public static final String CURRENT = "gtlcoreCurrentRecipeMode";
    public static final int VISIBLE_ROWS = 6;

    public MachineModeConfigurator(IRecipeLogicMachine machine, PanelSyncManager sync) {
        var types = machine.getRecipeTypes();
        var current = new IntSyncValue(machine::getActiveRecipeType);
        sync.syncValue(CURRENT, current);
        sync.registerSyncedAction(SELECT, false, true, buf -> {
            int index = buf.readVarInt();
            if (sync.isClient() || index < 0 || index >= machine.getRecipeTypes().length) return;
            var host = machine.self();
            var player = sync.getPlayer();
            if (host.isRemoved() || host.getLevel() != player.level()
                    || MetaMachine.getMachine(host.getLevel(), host.getBlockPos()) != host
                    || player.distanceToSqr(host.getBlockPos().getCenter()) > 64) return;
            machine.setActiveRecipeType(index);
            machine.getRecipeLogic().updateTickSubscription();
            current.notifyUpdate();
        });
        size(140, 20 * Math.min(types.length, VISIBLE_ROWS) + 4);
        padding(2);
        background(GTGuiTextures.BACKGROUND_INVERSE);
        for (int i = 0; i < types.length; i++) {
            int index = i;
            child(new ToggleButton().name("gtlcoreRecipeMode" + i).widthRel(1f).height(20)
                    .value(new BoolValue.Dynamic(() -> current.getIntValue() == index, selected -> {
                        if (selected && sync.isClient()) sync.callSyncedAction(SELECT, buf -> buf.writeVarInt(index));
                    }))
                    .overlay(Text.lang(types[i].getTranslationKey()).asIcon().width(128)));
        }
    }

    public static ButtonWidget<?> createButton(IRecipeLogicMachine machine, PanelSyncManager sync) {
        var handler = sync.syncedPanel(PANEL, true, (manager, ignored) ->
                PopupPanel.createPopupPanel(PANEL, 148, 20 * Math.min(machine.getRecipeTypes().length, VISIBLE_ROWS) + 30)
                        .child(Text.lang("gtceu.gui.machinemode.title").asWidget().pos(4, 5))
                        .child(new MachineModeConfigurator(machine, manager).pos(4, 22)));
        return new ButtonWidget<>().name("gtlcoreRecipeModeButton").size(18)
                .overlay(GTGuiTextures.PROGRESS_MIXER[0])
                .tooltip(t -> t.addLine(Component.translatable("gtceu.gui.machinemode.tab_tooltip")))
                .onMousePressed((context, button) -> {
                    if (button != 0 && button != 1) return false;
                    handler.openPanel();
                    return true;
                })
                .setEnabledIf(widget -> machine.getRecipeTypes().length > 1);
    }
}
