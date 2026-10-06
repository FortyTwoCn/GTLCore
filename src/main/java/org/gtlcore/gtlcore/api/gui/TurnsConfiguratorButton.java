package org.gtlcore.gtlcore.api.gui;

import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.drawable.DynamicDrawable;
import brachy.modularui.value.sync.IntSyncValue;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widgets.CycleButtonWidget;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Native cycle control with server-owned state and per-mode icons/tooltips. */
public final class TurnsConfiguratorButton {
    private final IDrawable[] icons;
    private final IntSupplier getter;
    private final IntConsumer setter;
    private Function<Integer, List<Component>> tooltips = mode -> List.of();

    public TurnsConfiguratorButton(IntSupplier getter, IntConsumer setter, IDrawable... icons) {
        this.getter = Objects.requireNonNull(getter); this.setter = Objects.requireNonNull(setter);
        if (icons.length == 0) throw new IllegalArgumentException("A cycle control requires at least one icon");
        this.icons = icons.clone();
        for (var icon : this.icons) Objects.requireNonNull(icon);
    }

    public TurnsConfiguratorButton setTooltipsSupplier(Function<Integer, List<Component>> supplier) {
        tooltips = Objects.requireNonNull(supplier); return this;
    }

    public CycleButtonWidget createConfigurator(PanelSyncManager sync, String key) {
        var state = new IntSyncValue(() -> Math.floorMod(getter.getAsInt(), icons.length), setter) {
            @Override public void setIntValue(int value, boolean setSource, boolean synchronize) {
                if (value >= 0 && value < icons.length) super.setIntValue(value, setSource, synchronize);
            }
        }.allowC2S();
        sync.syncValue(key, state);
        return new CycleButtonWidget().length(icons.length).value(state)
                .overlay(new DynamicDrawable(() -> icons[state.getIntValue()]))
                .tooltipDynamic(tooltip -> tooltips.apply(state.getIntValue()).forEach(tooltip::addLine));
    }
}
