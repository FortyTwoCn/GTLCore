package org.gtlcore.gtlcore.api.machine;

import com.gregtechceu.gtceu.common.mui.widgets.PopupPanel;
import net.minecraft.network.chat.Component;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widgets.layout.Flow;
import java.util.List;
import java.util.function.Function;

/** Native ModularUI replacement for the former LDLib output-filter tab. */
public final class MEExtendedOutputFancyConfigurator {
    private final Function<PanelSyncManager, Flow> widgetSupplier;
    public MEExtendedOutputFancyConfigurator(Function<PanelSyncManager, Flow> widgetSupplier) {
        this.widgetSupplier = widgetSupplier;
    }
    public PopupPanel createPanel(PanelSyncManager manager) {
        return PopupPanel.createPopupPanel("extendedOutputFilterPanel", 220, 176)
                .child(Text.dynamic(this::getTitle).asWidget().margin(4).width(184).height(16))
                .child(widgetSupplier.apply(manager).top(22));
    }
    public Component getTitle() { return Component.translatable("gtlcore.gui.filter_config.title"); }
    public List<Component> getTabTooltips() {
        return List.of(Component.translatable("tooltip.gtlcore.change_item_fluid_filter_priority"));
    }
}
