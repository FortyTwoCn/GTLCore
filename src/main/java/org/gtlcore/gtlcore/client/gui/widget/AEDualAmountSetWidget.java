package org.gtlcore.gtlcore.client.gui.widget;

import brachy.modularui.api.drawable.Text;
import brachy.modularui.value.sync.*;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.textfield.TextFieldWidget;
import org.gtlcore.gtlcore.integration.ae2.gui.NativeAEAmounts;

/** Long decimal editing is synced by the native panel; the server validates the selected configuration. */
public class AEDualAmountSetWidget extends ParentWidget<AEDualAmountSetWidget> {
    private final AEDualConfigWidget parent;
    public AEDualAmountSetWidget(int x, int y, AEDualConfigWidget parent) {
        this.parent = parent; pos(x, y).size(160, 46);
    }
    public AEDualAmountSetWidget syncManager(PanelSyncManager manager) {
        var fluid = new BooleanSyncValue(parent::isEditedFluid);
        var amount = SyncHandlers.string(parent::getEditedAmount, parent::setEditedAmount).allowC2S();
        manager.syncValue("dualAmountFluid", fluid); manager.syncValue("dualAmount", amount);
        child(Text.dynamic(() -> net.minecraft.network.chat.Component.literal(fluid.getBoolValue() ? "Amount (mB)" : "Amount")).asWidget().pos(2, 2));
        child(new TextFieldWidget().value(amount).setMaxLength(NativeAEAmounts.MAX_TEXT_LENGTH)
                .setValidator(text -> {
                    long parsed = NativeAEAmounts.parse(text);
                    return parsed > 0 ? Long.toString(parsed) : parent.getEditedAmount();
                }).pos(2, 22).size(152, 18));
        return this;
    }
}
