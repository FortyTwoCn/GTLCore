package org.gtlcore.gtlcore.common.data.source_tooltip.register;

import org.gtlcore.gtlcore.api.event.SourceTooltipRegistrationEvent;
import org.gtlcore.gtlcore.common.data.source_tooltip.SpecialComponent;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SourceTooltipRegister$ElementCopying {

    private static final Component component_element_copying = SpecialComponent.create(Component.translatable("gtceu.element_copying").withStyle(SpecialComponent.color_recipe_type), Component.literal(" UXV").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));

    public static void register(SourceTooltipRegistrationEvent event) {
        String[] items = {
                "gtceu:europium_dust", "gtceu:calcium_dust", "gtceu:scandium_dust", "gtceu:lithium_dust", "gtceu:thallium_dust", "gtceu:bohrium_dust", "gtceu:niobium_dust", "gtceu:tungsten_dust", "gtceu:berkelium_dust", "gtceu:silver_dust", "gtceu:nobelium_dust", "gtceu:strontium_dust", "gtceu:zirconium_dust", "gtceu:francium_dust", "gtceu:lawrencium_dust", "gtceu:tantalum_dust", "gtceu:molybdenum_dust", "gtceu:seaborgium_dust", "gtceu:cobalt_dust", "gtceu:zinc_dust", "gtceu:polonium_dust", "gtceu:einsteinium_dust", "gtceu:plutonium_241_dust", "gtceu:rhodium_dust",
                "gtceu:germanium_dust", "gtceu:protactinium_dust", "gtceu:lead_dust", "gtceu:darmstadtium_dust", "gtceu:erbium_dust", "gtceu:mendelevium_dust", "gtceu:carbon_dust", "gtceu:rubidium_dust", "gtceu:oganesson_dust", "gtceu:actinium_dust", "gtceu:plutonium_239_dust", "gtceu:cerium_dust", "gtceu:terbium_dust", "gtceu:cadmium_dust", "gtceu:iodine_dust", "gtceu:silicon_dust", "gtceu:copper_dust", "gtceu:aluminium_dust", "gtceu:promethium_dust", "gtceu:arsenic_dust", "gtceu:technetium_dust", "gtceu:praseodymium_dust", "gtceu:caesium_dust", "gtceu:tin_dust",
                "gtceu:bismuth_dust", "gtceu:moscovium_dust", "gtceu:gallium_dust", "gtceu:manganese_dust", "gtceu:neptunium_dust", "gtceu:uranium_235_dust", "gtceu:lanthanum_dust", "gtceu:rutherfordium_dust", "gtceu:rhenium_dust", "gtceu:vanadium_dust", "gtceu:gold_dust", "gtceu:iridium_dust", "gtceu:magnesium_dust", "gtceu:lutetium_dust", "gtceu:hassium_dust", "gtceu:curium_dust", "gtceu:nihonium_dust", "gtceu:nickel_dust", "gtceu:hafnium_dust", "gtceu:samarium_dust", "gtceu:selenium_dust", "gtceu:uranium_238_dust", "gtceu:flerovium_dust", "gtceu:fermium_dust",
                "gtceu:roentgenium_dust", "gtceu:beryllium_dust", "gtceu:iron_dust", "gtceu:titanium_50_dust", "gtceu:dubnium_dust", "gtceu:neodymium_dust", "gtceu:sodium_dust", "gtceu:gadolinium_dust", "gtceu:thorium_dust", "gtceu:radium_dust", "gtceu:barium_dust", "gtceu:californium_dust", "gtceu:holmium_dust", "gtceu:chromium_dust", "gtceu:ytterbium_dust", "gtceu:tennessine_dust", "gtceu:copernicium_dust", "gtceu:platinum_dust", "gtceu:meitnerium_dust", "gtceu:astatine_dust", "gtceu:ruthenium_dust", "gtceu:phosphorus_dust", "gtceu:sulfur_dust", "gtceu:dysprosium_dust",
                "gtceu:palladium_dust", "gtceu:titanium_dust", "gtceu:thulium_dust", "gtceu:yttrium_dust", "gtceu:antimony_dust", "gtceu:livermorium_dust", "gtceu:americium_dust", "gtceu:osmium_dust", "gtceu:indium_dust", "gtceu:boron_dust", "gtceu:potassium_dust"
        };
        String[] fluids = {
                "gtceu:neon", "gtceu:deuterium", "gtceu:xenon", "gtceu:tritium", "gtceu:fluorine", "gtceu:radon", "gtceu:hydrogen", "gtceu:helium", "gtceu:oxygen", "gtceu:argon", "gtceu:nitrogen", "gtceu:chlorine", "gtceu:helium_3", "gtceu:krypton", "gtceu:mercury", "gtceu:bromine"
        };
        for (var item : items) {
            var tooltip = event.register$item(item);
            if (tooltip == null) continue;
            tooltip.get_or_create$control(SpecialComponent.component_default$control)
                    .add(component_element_copying);
        }
        for (var fluid : fluids) {
            var tooltip = event.register$fluid(fluid);
            if (tooltip == null) continue;
            tooltip.get_or_create$control(SpecialComponent.component_default$control)
                    .add(component_element_copying);
        }
    }
}
