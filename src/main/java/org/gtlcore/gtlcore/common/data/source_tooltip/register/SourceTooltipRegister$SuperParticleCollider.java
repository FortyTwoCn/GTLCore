package org.gtlcore.gtlcore.common.data.source_tooltip.register;

import org.gtlcore.gtlcore.api.event.SourceTooltipRegistrationEvent;
import org.gtlcore.gtlcore.common.data.source_tooltip.SpecialComponent;

import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SourceTooltipRegister$SuperParticleCollider {

    private static final Component component_super_particle_collider = Component.translatable("gtceu.super_particle_collider").withStyle(SpecialComponent.color_recipe_type);

    public static void register(SourceTooltipRegistrationEvent event) {
        String[] fluids = {
                "gtceu:antineutron", "gtceu:positive_electron", "gtceu:antiproton", "gtceu:astatine", "gtceu:copernicium", "gtceu:mendelevium", "gtceu:plutonium", "gtceu:uranium", "gtceu:lawrencium", "gtceu:californium", "gtceu:curium", "gtceu:nobelium", "gtceu:berkelium", "gtceu:einsteinium", "gtceu:fermium", "gtceu:neptunium", "gtceu:nihonium", "gtceu:roentgenium", "gtceu:bohrium", "gtceu:antimatter"
        };
        for (var fluid : fluids) {
            var tooltip = event.register$fluid(fluid);
            if (tooltip == null) return;
            tooltip.get_or_create$control(SpecialComponent.component_default$control)
                    .add(component_super_particle_collider);
        }
    }
}
