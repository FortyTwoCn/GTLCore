package org.gtlcore.gtlcore.client.ae2.wireless;

import org.gtlcore.gtlcore.integration.ae2.chamber.MEChamberConfigurator;

import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import brachy.modularui.api.drawable.Text;
import brachy.modularui.drawable.ItemDrawable;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.value.BoolValue;
import brachy.modularui.value.StringValue;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.ToggleButton;
import brachy.modularui.widgets.textfield.TextFieldWidget;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Client-only embedded panels. Each edit sends a bounded domain command to the terminal. */
public final class MEChamberConfiguratorPanels {
    private MEChamberConfiguratorPanels() {}

    public static View create(MEChamberConfigurator.Kind kind, int circuit, int ticks,
                              String whitelist, String blacklist, Consumer<Consumer<FriendlyByteBuf>> send) {
        return switch (kind) {
            case CIRCUIT -> circuits(circuit, send);
            case SYNC_OFFSET -> refresh(ticks, send);
            case TAG_FILTER -> tags(whitelist, blacklist, send);
        };
    }

    private static View circuits(int initial, Consumer<Consumer<FriendlyByteBuf>> send) {
        var selection = new AtomicInteger(initial);
        var panel = new ModularPanel<>("gtlcoreChamberCircuits").size(156, 116);
        for (int configuration = 0; configuration <= IntCircuitBehaviour.CIRCUIT_MAX; configuration++) {
            int circuit = configuration;
            panel.child(new ToggleButton().name("chamberCircuit" + circuit).size(18)
                    .pos(6 + (circuit % 8) * 18, 4 + (circuit / 8) * 18)
                    .overlay(new ItemDrawable(IntCircuitBehaviour.stack(circuit)))
                    .value(new BoolValue.Dynamic(() -> selection.get() == circuit, selected -> {
                        int value = selected ? circuit : -1;
                        selection.set(value);
                        send.accept(payload -> payload.writeVarInt(value));
                    })));
        }
        panel.child(new ButtonWidget<>().name("chamberClearCircuit").size(18).pos(6, 96)
                .overlay(new ItemDrawable(Items.BARRIER))
                .tooltip(t -> t.addLine(Component.translatable("metaitem.int_circuit.configuration", -1)))
                .onMousePressed((context, button) -> {
                    if (button != 0) return false;
                    selection.set(-1);
                    send.accept(payload -> payload.writeVarInt(-1));
                    return true;
                }));
        return new View(Component.translatable("item.gtceu.circuit.integrated.gui"), panel);
    }

    private static View refresh(int initial, Consumer<Consumer<FriendlyByteBuf>> send) {
        var text = new AtomicReference<>(Integer.toString(initial));
        var value = new StringValue.Dynamic(text::get, changed -> {
            text.set(changed);
            try {
                int ticks = Integer.parseInt(changed);
                if (ticks >= 0) send.accept(payload -> payload.writeVarInt(ticks));
            } catch (NumberFormatException ignored) {
                // A field may temporarily be empty while the player edits it.
            }
        });
        var panel = new ModularPanel<>("gtlcoreChamberRefresh").size(160, 62)
                .child(Text.lang("gtceu.gui.title.adv_me_config.ticks_per_cycle").asWidget().pos(4, 4).width(152))
                .child(new TextFieldWidget().name("chamberRefreshTicks").value(value)
                        .setNumbersLong(ticks -> Math.clamp(ticks, 0, Integer.MAX_VALUE))
                        .pos(4, 36).size(152, 18));
        return new View(Component.translatable("gtceu.gui.adv_me_config.title"), panel);
    }

    private static View tags(String initialWhite, String initialBlack, Consumer<Consumer<FriendlyByteBuf>> send) {
        var white = tagValue(initialWhite, true, send);
        var black = tagValue(initialBlack, false, send);
        var panel = new ModularPanel<>("gtlcoreChamberTags").size(204, 122)
                .child(Text.lang("gui.gtlcore.tag_whitelist").asWidget().pos(6, 4).width(192))
                .child(new TextFieldWidget().name("chamberTagWhitelist").value(white)
                        .setMaxLength(MEChamberConfigurator.MAX_TAG_LENGTH).pos(6, 16).size(192, 18))
                .child(Text.lang("gui.gtlcore.tag_blacklist").asWidget().pos(6, 44).width(192))
                .child(new TextFieldWidget().name("chamberTagBlacklist").value(black)
                        .setMaxLength(MEChamberConfigurator.MAX_TAG_LENGTH).pos(6, 56).size(192, 18))
                .child(Text.lang("gui.gtlcore.wildcard_info").asWidget().pos(6, 88).width(192))
                .child(Text.lang("gui.gtlcore.logic_operators").asWidget().pos(6, 104).width(192));
        return new View(Component.translatable("gui.gtlcore.tag_filter_config"), panel);
    }

    private static StringValue.Dynamic tagValue(String initial, boolean whitelist,
                                               Consumer<Consumer<FriendlyByteBuf>> send) {
        var text = new AtomicReference<>(initial);
        return new StringValue.Dynamic(text::get, changed -> {
            if (changed.length() > MEChamberConfigurator.MAX_TAG_LENGTH) return;
            text.set(changed);
            send.accept(payload -> payload.writeBoolean(whitelist).writeUtf(changed, MEChamberConfigurator.MAX_TAG_LENGTH));
        });
    }

    public record View(Component title, ModularPanel<?> root) {}
}
