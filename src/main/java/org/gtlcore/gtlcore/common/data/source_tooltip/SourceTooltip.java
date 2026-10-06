package org.gtlcore.gtlcore.common.data.source_tooltip;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

@OnlyIn(Dist.CLIENT)
public class SourceTooltip {

    private @Nullable SourceTooltipComponent component$shift;
    private @Nullable SourceTooltipComponent component$control;
    private @Nullable SourceTooltipComponent component$alt;
    private @Nullable SourceTooltipComponent component$custom;
    private @Nullable SourceTooltipComponent component$always;

    public static final Map<Item, SourceTooltip> item_source_tooltips = new HashMap<>();
    public static final Map<Fluid, SourceTooltip> fluid_source_tooltips = new HashMap<>();
    public static long register_time;

    public void append(Consumer<Component> tooltip) {
        if (component$shift != null) component$shift.append(tooltip);
        else if (component$control != null) component$control.append(tooltip);
        else if (component$alt != null) component$alt.append(tooltip);
        else if (component$custom != null) component$custom.append(tooltip);
    }

    public SourceTooltipComponent get_or_create$shift(Component component_default) {
        if (component$shift == null) component$shift = SourceTooltipComponent.create$shift_down(component_default);
        return component$shift;
    }

    public SourceTooltipComponent get_or_create$control(Component component_default) {
        if (component$control == null)
            component$control = SourceTooltipComponent.create$control_down(component_default);
        return component$control;
    }

    public SourceTooltipComponent get_or_create$alt(Component component_default) {
        if (component$alt == null) component$alt = SourceTooltipComponent.create$alt_down(component_default);
        return component$alt;
    }

    public SourceTooltipComponent get_or_create$custom(Component component_default, Supplier<Boolean> condition) {
        if (component$custom == null)
            component$custom = SourceTooltipComponent.create$custom(component_default, condition);
        return component$custom;
    }

    public SourceTooltipComponent get_or_create$always() {
        if (component$always == null)
            component$always = SourceTooltipComponent.create$always();
        return component$always;
    }

    public static void append(Item item, Consumer<Component> tooltip) {
        if (item == null) return;
        var source_tooltip = item_source_tooltips.get(item);
        if (source_tooltip == null) return;
        source_tooltip.append(tooltip);
    }

    public static void append(Fluid fluid, Consumer<Component> tooltip) {
        if (fluid == null) return;
        var source_tooltip = fluid_source_tooltips.get(fluid);
        if (source_tooltip == null) return;
        source_tooltip.append(tooltip);
    }

    public static final Supplier<Boolean> condition$shift_down = Screen::hasShiftDown;
    public static final Supplier<Boolean> condition$control_down = Screen::hasControlDown;
    public static final Supplier<Boolean> condition$alt_down = Screen::hasAltDown;
    public static final Supplier<Boolean> condition$always = () -> true;
}
