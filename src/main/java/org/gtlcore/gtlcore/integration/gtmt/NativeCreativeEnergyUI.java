package org.gtlcore.gtlcore.integration.gtmt;

import org.gtlcore.gtlcore.utils.NumberUtils;
import com.gregtechceu.gtceu.api.GTValues;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.value.sync.IntSyncValue;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.SyncHandlers;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.layout.Flow;
import brachy.modularui.widgets.menu.DropdownWidget;
import brachy.modularui.widgets.textfield.TextFieldWidget;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.stream.IntStream;

/** Integer voltage packets preserve all nineteen decimal digits, including Long.MAX_VALUE. */
public final class NativeCreativeEnergyUI {
    private NativeCreativeEnergyUI() {}

    public static String validateVoltage(String text, long minimum) {
        try {
            if (text == null || text.length() > 19 || !text.matches("[0-9]+")) return "";
            long value = Long.parseLong(text);
            return value >= minimum ? Long.toString(value) : "";
        } catch (NumberFormatException ignored) { return ""; }
    }

    public static void build(ParentWidget<?> parent, PanelSyncManager manager, LongSupplier getVoltage,
                             LongConsumer setVoltage, IntSupplier getAmps, IntConsumer setAmps, int minimumTier) {
        long minimumVoltage = GTValues.VEX[minimumTier];
        var voltage = SyncHandlers.string(() -> Long.toString(getVoltage.getAsLong()), text -> {
            String validated = validateVoltage(text, minimumVoltage);
            if (!validated.isEmpty()) setVoltage.accept(Long.parseLong(validated));
        }).allowC2S();
        var amperage = new IntSyncValue(getAmps, value -> {
            if (value >= 0) setAmps.accept(value);
        }).allowC2S();
        var tier = new IntSyncValue(() -> Math.max(minimumTier, NumberUtils.getFakeVoltageTier(getVoltage.getAsLong())), value -> {
            if (value >= minimumTier && value <= GTValues.MAX_TRUE) setVoltage.accept(GTValues.VEX[value]);
        }).allowC2S();
        manager.syncValue("voltage", voltage);
        manager.syncValue("amps", amperage);
        manager.syncValue("tier", tier);
        var column = Flow.column().width(174).coverChildrenHeight().childPadding(4);
        column.child(new DropdownWidget<>("creative_energy_tiers", Integer.class)
                .options(IntStream.rangeClosed(minimumTier, GTValues.MAX_TRUE).boxed().toList())
                .optionToWidget((value, selected) -> Text.str(NewGTValues.VNF[value]).asWidget().height(14))
                .value(tier).maxVerticalMenuSize(112).size(170, 18));
        column.child(Text.lang("gtceu.creative.energy.voltage").asWidget());
        column.child(new TextFieldWidget().name("creative_voltage").size(170, 18).setMaxLength(19)
                .setValidator(text -> validateVoltage(text, minimumVoltage)).value(voltage));
        column.child(Text.lang("gtceu.creative.energy.amperage").asWidget());
        column.child(Flow.row().height(18).childPadding(2)
                .child(new ButtonWidget<>().name("creative_amp_decrease").size(18).overlay(Text.str("-")).onMousePressed((context, button) -> {
                    if (button != 0) return false;
                    amperage.setIntValue(Math.max(0, amperage.getIntValue() - 1)); return true;
                }))
                .child(new TextFieldWidget().name("creative_amperage").size(128, 18)
                        .setNumbers(0, Integer.MAX_VALUE).value(amperage))
                .child(new ButtonWidget<>().name("creative_amp_increase").size(18).overlay(Text.str("+")).onMousePressed((context, button) -> {
                    if (button != 0) return false;
                    if (amperage.getIntValue() < Integer.MAX_VALUE) amperage.setIntValue(amperage.getIntValue() + 1);
                    return true;
                })));
        parent.child(column);
    }
}
