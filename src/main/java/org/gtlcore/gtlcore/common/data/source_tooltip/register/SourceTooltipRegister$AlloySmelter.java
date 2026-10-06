package org.gtlcore.gtlcore.common.data.source_tooltip.register;

import org.gtlcore.gtlcore.api.event.SourceTooltipRegistrationEvent;
import org.gtlcore.gtlcore.common.data.source_tooltip.SpecialComponent;

import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SourceTooltipRegister$AlloySmelter {

    private static final Component component_alloy_smelter = SpecialComponent.create(Component.translatable("gtceu.alloy_smelter").withStyle(SpecialComponent.color_recipe_type));
    private static final Component component_alloy_blast_smelter = SpecialComponent.create(Component.translatable("gtceu.alloy_blast_smelter").withStyle(SpecialComponent.color_recipe_type));

    public static void register(SourceTooltipRegistrationEvent event) {
        String[] items = {
                "gtceu:bronze_ingot", "gtceu:cupronickel_ingot", "gtceu:tin_alloy_ingot", "gtceu:invar_ingot", "gtceu:brass_ingot", "gtceu:electrum_ingot", "gtceu:battery_alloy_ingot", "gtceu:red_alloy_ingot", "gtceu:blue_alloy_ingot", "gtceu:magnalium_ingot", "gtceu:pulsating_alloy_ingot", "gtceu:conductive_alloy_ingot", "ad_astra:calorite_ingot"
        };
        for (var item : items) {
            var tooltip = event.register$item(item);
            if (tooltip == null) continue;
            tooltip.get_or_create$always()
                    .add(component_alloy_smelter);
        }
        for (var item : items) {
            var tooltip = event.register$fluid(item.replace("_ingot", ""));
            if (tooltip == null) continue;
            tooltip.get_or_create$always()
                    .add(component_alloy_blast_smelter);
        }
    }
}
