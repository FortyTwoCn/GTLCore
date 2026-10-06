package org.gtlcore.gtlcore.api.gui;

import com.gregtechceu.gtceu.api.machine.mui.MachineUIPanelBuilder;
import com.gregtechceu.gtceu.common.mui.widgets.PopupPanel;
import net.minecraft.network.chat.Component;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.SyncHandlers;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.textfield.TextFieldWidget;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Native machine sidebar and server-authoritative refresh interval editor. Zero uses the GT default. */
public final class AdvancedMEConfigurator {
    private final Consumer<Integer> consumer;
    private final Supplier<Integer> offset;
    public AdvancedMEConfigurator(Consumer<Integer> consumer, Supplier<Integer> offset) {
        this.consumer = consumer; this.offset = offset;
    }
    public MachineUIPanelBuilder attachTo(MachineUIPanelBuilder builder, PanelSyncManager sync) {
        var inherited = builder.leftConfigurators();
        var popup = sync.syncedPanel("gtlcoreMERefresh", true, (sm, handler) -> {
            var ticks = SyncHandlers.string(() -> Integer.toString(offset.get()), text -> {
                try { int value = Integer.parseInt(text); if (value >= 0) consumer.accept(value); }
                catch (NumberFormatException ignored) {}
            }).allowC2S();
            sm.syncValue("gtlcoreMERefreshTicks", ticks);
            return PopupPanel.createPopupPanel("gtlcoreMERefreshPanel", 160, 62)
                    .child(Text.lang("gtceu.gui.title.adv_me_config.ticks_per_cycle").asWidget().pos(4, 4).width(152))
                    .child(new TextFieldWidget().value(ticks).setNumbersLong(value -> Math.clamp(value, 0, Integer.MAX_VALUE))
                            .pos(4, 36).size(152, 18));
        });
        return builder.leftConfigurators(flow -> {
            inherited.accept(flow);
            flow.child(new ButtonWidget<>().name("gtlcoreMERefreshButton").size(18).overlay(Text.str("T"))
                    .tooltip(tooltip -> tooltip.addLine(Component.translatable("gtceu.gui.adv_me_config.title")))
                    .onMousePressed((context, button) -> { if (button != 0) return false; popup.openPanel(); return true; }));
        });
    }
}
