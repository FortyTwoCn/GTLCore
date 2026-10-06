package org.gtlcore.gtlcore.common.data.source_tooltip;

import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

@OnlyIn(Dist.CLIENT)
public class SourceTooltipComponent {

    private final List<Component> components = new ArrayList<>();
    private Component component_default;
    private final Supplier<Boolean> condition;

    public SourceTooltipComponent(Component component_default, Supplier<Boolean> condition) {
        this.component_default = component_default;
        this.condition = condition;
    }

    public void append(Consumer<Component> tooltip) {
        if (condition.get()) components.forEach(tooltip);
        else tooltip.accept(component_default);
    }

    public SourceTooltipComponent add(Component... components) {
        this.components.addAll(Stream.of(components).filter(Objects::nonNull).toList());
        return this;
    }

    public SourceTooltipComponent add(List<Component> components) {
        this.components.addAll(components);
        return this;
    }

    public SourceTooltipComponent reset_default_component(Component component_default) {
        this.component_default = component_default;
        return this;
    }

    public SourceTooltipComponent clear() {
        this.components.clear();
        return this;
    }

    public static SourceTooltipComponent create$shift_down(Component component_default) {
        return new SourceTooltipComponent(component_default, SourceTooltip.condition$shift_down);
    }

    public static SourceTooltipComponent create$control_down(Component component_default) {
        return new SourceTooltipComponent(component_default, SourceTooltip.condition$control_down);
    }

    public static SourceTooltipComponent create$alt_down(Component component_default) {
        return new SourceTooltipComponent(component_default, SourceTooltip.condition$alt_down);
    }

    public static SourceTooltipComponent create$custom(Component component_default, Supplier<Boolean> condition) {
        return new SourceTooltipComponent(component_default, condition);
    }

    private static final Component component_empty = Component.empty();

    public static SourceTooltipComponent create$always() {
        return new SourceTooltipComponent(component_empty, SourceTooltip.condition$always);
    }
}
