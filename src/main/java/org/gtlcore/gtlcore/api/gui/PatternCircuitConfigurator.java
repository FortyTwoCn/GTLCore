package org.gtlcore.gtlcore.api.gui;

import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;
import com.gregtechceu.gtceu.common.mui.widgets.PopupPanel;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.SyncHandlers;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.ToggleButton;
import brachy.modularui.widgets.textfield.TextFieldWidget;
import java.util.function.IntSupplier;
import java.util.function.IntConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class PatternCircuitConfigurator {
    private final IntSupplier circuit;
    private final IntConsumer setCircuit;
    private final BooleanSupplier skipExisting;
    private final Consumer<Boolean> setSkipExisting;
    private final Runnable apply, clear;

    public PatternCircuitConfigurator(IntSupplier circuit, IntConsumer setCircuit, BooleanSupplier skipExisting,
                                       Consumer<Boolean> setSkipExisting, Runnable apply, Runnable clear) {
        this.circuit = circuit; this.setCircuit = setCircuit; this.skipExisting = skipExisting;
        this.setSkipExisting = setSkipExisting; this.apply = apply; this.clear = clear;
    }

    public PopupPanel createPanel(PanelSyncManager sync) {
        var number = SyncHandlers.string(() -> Integer.toString(circuit.getAsInt()), text -> {
            try { setCircuit.accept(Math.clamp(Integer.parseInt(text), 1, IntCircuitBehaviour.CIRCUIT_MAX)); }
            catch (NumberFormatException ignored) {}
        }).allowC2S();
        var skip = SyncHandlers.bool(skipExisting::getAsBoolean, setSkipExisting::accept).allowC2S();
        sync.syncValue("embeddedPatternCircuit", number);
        sync.syncValue("skipExistingPatternCircuit", skip);
        sync.registerServerSyncedAction("applyPatternCircuit", buffer -> apply.run());
        sync.registerServerSyncedAction("clearPatternCircuits", buffer -> clear.run());
        return PopupPanel.createPopupPanel("patternCircuitConfigPanel", 190, 104)
                .child(Text.lang("gui.gtlcore.pattern_buffer_circuit_config").asWidget().pos(8, 6))
                .child(Text.lang("gui.gtlcore.pattern_buffer_circuit_number").asWidget().pos(10, 28))
                .child(new TextFieldWidget().value(number).size(70, 16).pos(110, 24))
                .child(Text.lang("gui.gtlcore.pattern_buffer_skip_existing_circuit").asWidget().pos(10, 50))
                .child(new ToggleButton().value(skip).size(20, 16).pos(158, 46).overlay(Text.str("#")))
                .child(new ButtonWidget<>().size(78, 18).pos(10, 72).overlay(Text.lang("gui.gtlcore.pattern_buffer_apply_circuit"))
                        .onMousePressed((context, button) -> { if (button != 0) return false; sync.callSyncedAction("applyPatternCircuit"); return true; }))
                .child(new ButtonWidget<>().size(78, 18).pos(96, 72).overlay(Text.lang("gui.gtlcore.pattern_buffer_clear_circuits"))
                        .onMousePressed((context, button) -> { if (button != 0) return false; sync.callSyncedAction("clearPatternCircuits"); return true; }));
    }
}
