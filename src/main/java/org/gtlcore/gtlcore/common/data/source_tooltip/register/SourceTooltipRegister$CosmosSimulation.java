package org.gtlcore.gtlcore.common.data.source_tooltip.register;

import org.gtlcore.gtlcore.api.event.SourceTooltipRegistrationEvent;
import org.gtlcore.gtlcore.common.data.source_tooltip.SpecialComponent;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SourceTooltipRegister$CosmosSimulation {

    private static final Component component_cosmos_simulation = SpecialComponent.create(Component.translatable("gtceu.cosmos_simulation").withStyle(SpecialComponent.color_recipe_type), Component.literal(" OpV").withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD));

    public static void register(SourceTooltipRegistrationEvent event) {
        String[] items = {
                "gtceu:carbon_dust", "gtceu:phosphorus_dust", "gtceu:sulfur_dust", "gtceu:selenium_dust", "gtceu:iodine_dust", "gtceu:boron_dust", "gtceu:silicon_dust", "gtceu:germanium_dust", "gtceu:arsenic_dust", "gtceu:antimony_dust", "gtceu:tellurium_dust", "gtceu:astatine_dust", "gtceu:aluminium_dust", "gtceu:gallium_dust", "gtceu:indium_dust", "gtceu:tin_dust", "gtceu:thallium_dust", "gtceu:lead_dust", "gtceu:bismuth_dust", "gtceu:polonium_dust", "gtceu:titanium_dust", "gtceu:vanadium_dust", "gtceu:chromium_dust", "gtceu:manganese_dust",
                "gtceu:iron_dust", "gtceu:cobalt_dust", "gtceu:nickel_dust", "gtceu:copper_dust", "gtceu:zinc_dust", "gtceu:zirconium_dust", "gtceu:niobium_dust", "gtceu:molybdenum_dust", "gtceu:technetium_dust", "gtceu:ruthenium_dust", "gtceu:rhodium_dust", "gtceu:palladium_dust", "gtceu:silver_dust", "gtceu:cadmium_dust", "gtceu:hafnium_dust", "gtceu:tantalum_dust", "gtceu:tungsten_dust", "gtceu:rhenium_dust", "gtceu:osmium_dust", "gtceu:iridium_dust", "gtceu:platinum_dust", "gtceu:gold_dust", "gtceu:beryllium_dust", "gtceu:magnesium_dust",
                "gtceu:calcium_dust", "gtceu:strontium_dust", "gtceu:barium_dust", "gtceu:radium_dust", "gtceu:yttrium_dust", "gtceu:lithium_dust", "gtceu:sodium_dust", "gtceu:potassium_dust", "gtceu:rubidium_dust", "gtceu:caesium_dust", "gtceu:francium_dust", "gtceu:scandium_dust", "gtceu:actinium_dust", "gtceu:thorium_dust", "gtceu:protactinium_dust", "gtceu:uranium_238_dust", "gtceu:neptunium_dust", "gtceu:plutonium_239_dust", "gtceu:americium_dust", "gtceu:curium_dust", "gtceu:berkelium_dust", "gtceu:californium_dust", "gtceu:einsteinium_dust", "gtceu:fermium_dust",
                "gtceu:mendelevium_dust", "gtceu:nobelium_dust", "gtceu:lawrencium_dust", "gtceu:lanthanum_dust", "gtceu:cerium_dust", "gtceu:praseodymium_dust", "gtceu:neodymium_dust", "gtceu:promethium_dust", "gtceu:samarium_dust", "gtceu:europium_dust", "gtceu:gadolinium_dust", "gtceu:terbium_dust", "gtceu:dysprosium_dust", "gtceu:holmium_dust", "gtceu:erbium_dust", "gtceu:thulium_dust", "gtceu:ytterbium_dust", "gtceu:lutetium_dust", "gtceu:rutherfordium_dust", "gtceu:dubnium_dust", "gtceu:seaborgium_dust", "gtceu:bohrium_dust", "gtceu:hassium_dust", "gtceu:meitnerium_dust",
                "gtceu:darmstadtium_dust", "gtceu:roentgenium_dust", "gtceu:copernicium_dust", "gtceu:nihonium_dust", "gtceu:flerovium_dust", "gtceu:moscovium_dust", "gtceu:livermorium_dust", "gtceu:tennessine_dust", "gtceu:oganesson_dust", "gtceu:jasper_dust", "gtceu:naquadah_dust", "gtceu:enriched_naquadah_dust", "gtceu:naquadria_dust", "gtceu:duranium_dust", "gtceu:tritanium_dust", "gtceu:mithril_dust", "gtceu:orichalcum_dust", "gtceu:enderium_dust", "gtceu:adamantine_dust", "gtceu:vibranium_dust", "gtceu:infuscolium_dust", "gtceu:taranium_dust", "gtceu:draconium_dust", "gtceu:starmetal_dust"
        };
        String[] fluids = {
                "gtceu:uu_matter", "gtceu:unknowwater", "gtceu:raw_star_matter_plasma", "gtceu:quark_gluon_plasma", "gtceu:neon", "gtceu:deuterium", "gtceu:xenon", "gtceu:tritium", "gtceu:fluorine", "gtceu:radon", "gtceu:hydrogen", "gtceu:helium", "gtceu:oxygen", "gtceu:argon", "gtceu:nitrogen", "gtceu:chlorine", "gtceu:heavy_lepton_mixture", "gtceu:helium_3", "gtceu:krypton", "gtceu:mercury", "gtceu:neutronium", "gtceu:spacetime", "gtceu:bromine", "gtceu:heavy_quark_degenerate_matter_plasma"
        };
        for (var item : items) {
            var tooltip = event.register$item(item);
            if (tooltip == null) continue;
            tooltip.get_or_create$control(SpecialComponent.component_default$control)
                    .add(component_cosmos_simulation);
        }
        for (var fluid : fluids) {
            var tooltip = event.register$fluid(fluid);
            if (tooltip == null) continue;
            tooltip.get_or_create$control(SpecialComponent.component_default$control)
                    .add(component_cosmos_simulation);
        }
    }
}
