package org.gtlcore.gtlcore.common.data.source_tooltip.register;

import org.gtlcore.gtlcore.api.event.SourceTooltipRegistrationEvent;
import org.gtlcore.gtlcore.common.data.source_tooltip.SpecialComponent;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SourceTooltipRegister$FusionReactor {

    public static void register(SourceTooltipRegistrationEvent event) {
        String[][] fluids_array = {
                { "gtceu:helium_plasma", "gtceu:plutonium", "gtceu:uranium", "gtceu:uranium_235", "gtceu:europium", "gtceu:chromium", "gtceu:duranium", "gtceu:osmium", "gtceu:lutetium" },
                { "gtceu:radon", "gtceu:oxygen_plasma", "gtceu:argon_plasma", "gtceu:nitrogen_plasma", "gtceu:tritanium", "gtceu:plutonium_241", "gtceu:indium", "gtceu:americium", "gtceu:darmstadtium" },
                { "gtceu:orichalcum_plasma", "gtceu:neutronium", "gtceu:naquadria", "gtceu:mithril_plasma", "gtceu:iron_plasma", "gtceu:silver_plasma", "gtceu:nickel_plasma" },
                { "gtceu:metastable_hassium_plasma", "gtceu:taranium_rich_liquid_helium_4_plasma", "gtceu:moscovium", "gtceu:tennessine", "gtceu:livermorium", "gtceu:dubnium", "gtceu:seaborgium", "gtceu:hot_oganesson" },
                { "gtceu:vibranium_plasma", "gtceu:infinity", "gtceu:draconiumawakened_plasma" }
        };
        for (int i = 0; i < fluids_array.length; i++) {
            var fluids = fluids_array[i];
            for (var fluid : fluids) {
                var tooltip = event.register$fluid(fluid);
                if (tooltip == null) continue;
                tooltip.get_or_create$control(SpecialComponent.component_default$control)
                        .add(SpecialComponent.components_fusion_reactor[i]);
            }
        }
    }
}
