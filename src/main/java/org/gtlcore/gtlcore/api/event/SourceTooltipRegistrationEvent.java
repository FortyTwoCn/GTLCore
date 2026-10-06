package org.gtlcore.gtlcore.api.event;

import org.gtlcore.gtlcore.common.data.source_tooltip.SourceTooltip;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

@OnlyIn(Dist.CLIENT)
public class SourceTooltipRegistrationEvent extends Event implements IModBusEvent {

    public SourceTooltip register$item(String id) {
        var item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).orElse(null);
        if (item == null) return null;
        return SourceTooltip.item_source_tooltips.computeIfAbsent(item, k -> new SourceTooltip());
    }

    public SourceTooltip register$fluid(String id) {
        // Some solids intentionally have no fluid. Never attach their tooltip to a fallback water entry.
        var fluid = BuiltInRegistries.FLUID.getOptional(ResourceLocation.parse(id)).orElse(null);
        if (fluid == null) return null;
        return SourceTooltip.fluid_source_tooltips.computeIfAbsent(fluid, k -> new SourceTooltip());
    }

    public SourceTooltip register(Item item) {
        if (item == null) return null;
        return SourceTooltip.item_source_tooltips.computeIfAbsent(item, k -> new SourceTooltip());
    }

    public SourceTooltip register(Fluid fluid) {
        if (fluid == null) return null;
        return SourceTooltip.fluid_source_tooltips.computeIfAbsent(fluid, k -> new SourceTooltip());
    }
}
