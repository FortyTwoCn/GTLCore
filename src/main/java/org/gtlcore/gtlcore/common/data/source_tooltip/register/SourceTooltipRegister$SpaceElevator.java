package org.gtlcore.gtlcore.common.data.source_tooltip.register;

import org.gtlcore.gtlcore.api.event.SourceTooltipRegistrationEvent;
import org.gtlcore.gtlcore.common.data.source_tooltip.SourceTooltip;
import org.gtlcore.gtlcore.common.data.source_tooltip.SpecialComponent;

import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SourceTooltipRegister$SpaceElevator {

    private static final Component component_miner_module = Component.translatable("gtceu.miner_module").withStyle(SpecialComponent.color_recipe_type);
    private static final Component component_drilling_module = Component.translatable("gtceu.drilling_module").withStyle(SpecialComponent.color_recipe_type);

    private static void of_mm(SourceTooltip tooltip, Component... components) {
        if (tooltip == null) return;
        if (components == null || components.length == 0) return;
        tooltip.get_or_create$control(SpecialComponent.component_default$control)
                .add(component_miner_module)
                .add(components);
    }

    private static void of_dm(SourceTooltip tooltip, Component... components) {
        if (tooltip == null) return;
        if (components == null || components.length == 0) return;
        tooltip.get_or_create$control(SpecialComponent.component_default$control)
                .add(component_drilling_module)
                .add(components);
    }

    private static Component as(int i, String cs) {
        return Component.empty().append(SpecialComponent.components_space_drone[i]).append(Component.literal(cs).withStyle(SpecialComponent.color_circuits));
    }

    public static void register(SourceTooltipRegistrationEvent event) {
        of_mm(event.register$item("ae2:sky_stone_block"), as(0, " [ 6 ]"));
        of_mm(event.register$item("gtceu:adamantine_compounds_ore"), as(3, " [ 28 ]"));
        of_mm(event.register$item("gtceu:alien_algae_ore"), as(0, " [ 14 ]"));
        of_mm(event.register$item("gtceu:almandine_ore"), as(0, " [ 2 ]"), as(2, " [ 27 ]"));
        of_mm(event.register$item("gtceu:aluminium_ore"), as(0, " [ 20 ]"), as(4, " [ 31 ]"));
        of_mm(event.register$item("gtceu:alunite_ore"), as(0, " [ 7 ]"));
        of_mm(event.register$item("gtceu:amethyst_ore"), as(0, " [ 14 ]"), as(1, " [ 25 ]"));
        of_mm(event.register$item("gtceu:andesite_platinum_ore"), as(4, " [ 31 ]"));
        of_mm(event.register$item("gtceu:apatite_ore"), as(0, " [ 18 ]"));
        of_mm(event.register$item("gtceu:asbestos_ore"), as(0, " [ 19 ]"));
        of_mm(event.register$item("gtceu:barite_ore"), as(0, " [ 14 ]"));
        of_mm(event.register$item("gtceu:basaltic_mineral_sand_ore"), as(0, " [ 15 ]"));
        of_mm(event.register$item("gtceu:bastnasite_ore"), as(0, " [ 3 ]"), as(3, " [ 28 ]"));
        of_mm(event.register$item("gtceu:bauxite_ore"), as(0, " [ 12, 20 ]"), as(4, " [ 30 ]"));
        of_mm(event.register$item("gtceu:bentonite_ore"), as(0, " [ 1 ]"));
        of_mm(event.register$item("gtceu:beryllium_ore"), as(0, " [ 8 ]"));
        of_mm(event.register$item("gtceu:bloodstone_ore"), as(2, " [ 27 ]"));
        of_mm(event.register$item("gtceu:blue_topaz_ore"), as(0, " [ 15 ]"));
        of_mm(event.register$item("gtceu:bornite_ore"), as(0, " [ 16, 21 ]"));
        of_mm(event.register$item("gtceu:calcite_ore"), as(0, " [ 10 ]"));
        of_mm(event.register$item("gtceu:calorite_ore"), as(0, " [ 10 ]"));
        of_mm(event.register$item("gtceu:cassiterite_ore"), as(0, " [ 6, 13 ]"));
        of_mm(event.register$item("gtceu:cassiterite_sand_ore"), as(0, " [ 19 ]"));
        of_mm(event.register$item("gtceu:celestine_ore"), as(1, " [ 25 ]"), as(4, " [ 30 ]"));
        of_mm(event.register$item("gtceu:certus_quartz_ore"), as(0, " [ 13 ]"));
        of_mm(event.register$item("gtceu:chalcocite_ore"), as(0, " [ 16 ]"));
        of_mm(event.register$item("gtceu:chalcopyrite_ore"), as(0, " [ 6, 8 ]"));
        of_mm(event.register$item("gtceu:chromite_ore"), as(0, " [ 24 ]"));
        of_mm(event.register$item("gtceu:cinnabar_ore"), as(0, " [ 6, 17 ]"));
        of_mm(event.register$item("gtceu:coal_ore"), as(0, " [ 7, 21 ]"));
        of_mm(event.register$item("gtceu:cobalt_ore"), as(2, " [ 26 ]"));
        of_mm(event.register$item("gtceu:cobaltite_ore"), as(0, " [ 22 ]"));
        of_mm(event.register$item("gtceu:cooperite_ore"), as(0, " [ 21 ]"));
        of_mm(event.register$item("gtceu:copper_ore"), as(0, " [ 1, 8 ]"));
        of_mm(event.register$item("gtceu:diamond_ore"), as(0, " [ 21 ]"));
        of_mm(event.register$item("gtceu:diatomite_ore"), as(0, " [ 7, 19 ]"));
        of_mm(event.register$item("gtceu:earth_crystal_ore"), as(3, " [ 29 ]"));
        of_mm(event.register$item("gtceu:electrotine_ore"), as(0, " [ 7 ]"));
        of_mm(event.register$item("gtceu:emerald_ore"), as(0, " [ 8 ]"), as(1, " [ 25 ]"));
        of_mm(event.register$item("gtceu:enderium_ore"), as(4, " [ 30 ]"));
        of_mm(event.register$item("gtceu:enriched_naquadah_ore"), as(0, " [ 24 ]"), as(3, " [ 28 ]"));
        of_mm(event.register$item("gtceu:force_ore"), as(2, " [ 26 ]"));
        of_mm(event.register$item("gtceu:fullers_earth_ore"), as(0, " [ 15 ]"));
        of_mm(event.register$item("gtceu:galena_ore"), as(0, " [ 11 ]"));
        of_mm(event.register$item("gtceu:garnet_sand_ore"), as(0, " [ 19 ]"));
        of_mm(event.register$item("gtceu:garnierite_ore"), as(0, " [ 22 ]"));
        of_mm(event.register$item("gtceu:glauconite_sand_ore"), as(0, " [ 1, 4 ]"));
        of_mm(event.register$item("gtceu:goethite_ore"), as(0, " [ 3, 12 ]"));
        of_mm(event.register$item("gtceu:gold_ore"), as(0, " [ 9, 13, 20 ]"));
        of_mm(event.register$item("gtceu:granitic_mineral_sand_ore"), as(0, " [ 15 ]"));
        of_mm(event.register$item("gtceu:graphite_ore"), as(0, " [ 21 ]"));
        of_mm(event.register$item("gtceu:gravel_ruby_ore"), as(2, " [ 27 ]"));
        of_mm(event.register$item("gtceu:green_sapphire_ore"), as(0, " [ 2 ]"), as(5, " [ 32 ]"));
        of_mm(event.register$item("gtceu:grossular_ore"), as(0, " [ 5, 9 ]"));
        of_mm(event.register$item("gtceu:gypsum_ore"), as(0, " [ 15 ]"));
        of_mm(event.register$item("gtceu:hematite_ore"), as(0, " [ 3, 13 ]"));
        of_mm(event.register$item("gtceu:ignis_crystal_ore"), as(3, " [ 29 ]"));
        of_mm(event.register$item("gtceu:ilmenite_ore"), as(0, " [ 20 ]"));
        of_mm(event.register$item("gtceu:indium_ore"), as(0, " [ 24 ]"));
        of_mm(event.register$item("gtceu:infused_gold_ore"), as(0, " [ 20 ]"));
        of_mm(event.register$item("gtceu:iron_ore"), as(0, " [ 8 ]"), as(2, " [ 26 ]"), as(4, " [ 31 ]"));
        of_mm(event.register$item("gtceu:jasper_ore"), as(1, " [ 25 ]"));
        of_mm(event.register$item("gtceu:kyanite_ore"), as(0, " [ 12 ]"));
        of_mm(event.register$item("gtceu:lapis_ore"), as(0, " [ 10 ]"), as(4, " [ 30 ]"));
        of_mm(event.register$item("gtceu:lazurite_ore"), as(0, " [ 10 ]"), as(5, " [ 32 ]"));
        of_mm(event.register$item("gtceu:lead_ore"), as(0, " [ 11 ]"));
        of_mm(event.register$item("gtceu:lepidolite_ore"), as(0, " [ 16 ]"));
        of_mm(event.register$item("gtceu:lithium_ore"), as(0, " [ 23 ]"));
        of_mm(event.register$item("gtceu:magnetite_ore"), as(0, " [ 1, 9, 19 ]"));
        of_mm(event.register$item("gtceu:malachite_ore"), as(0, " [ 3 ]"));
        of_mm(event.register$item("gtceu:mica_ore"), as(0, " [ 12 ]"));
        of_mm(event.register$item("gtceu:mithril_ore"), as(3, " [ 29 ]"));
        of_mm(event.register$item("gtceu:molybdenite_ore"), as(0, " [ 11 ]"));
        of_mm(event.register$item("gtceu:molybdenum_ore"), as(0, " [ 3, 11 ]"));
        of_mm(event.register$item("gtceu:monazite_ore"), as(0, " [ 4 ]"), as(3, " [ 28 ]"));
        of_mm(event.register$item("gtceu:naquadah_ore"), as(0, " [ 24 ]"), as(3, " [ 28 ]"));
        of_mm(event.register$item("gtceu:neodymium_ore"), as(0, " [ 4 ]"));
        of_mm(event.register$item("gtceu:nether_quartz_ore"), as(0, " [ 17 ]"));
        of_mm(event.register$item("gtceu:nickel_ore"), as(0, " [ 22 ]"), as(2, " [ 26 ]"));
        of_mm(event.register$item("gtceu:oilsands_ore"), as(0, " [ 20 ]"));
        of_mm(event.register$item("gtceu:olivine_ore"), as(0, " [ 1 ]"));
        of_mm(event.register$item("gtceu:opal_ore"), as(0, " [ 14 ]"));
        of_mm(event.register$item("gtceu:orichalcum_ore"), as(3, " [ 29 ]"));
        of_mm(event.register$item("gtceu:palladium_ore"), as(0, " [ 22 ]"));
        of_mm(event.register$item("gtceu:pentlandite_ore"), as(0, " [ 4, 22 ]"));
        of_mm(event.register$item("gtceu:pitchblende_ore"), as(0, " [ 23 ]"), as(4, " [ 30 ]"));
        of_mm(event.register$item("gtceu:platinum_ore"), as(0, " [ 22 ]"));
        of_mm(event.register$item("gtceu:plutonium_ore"), as(0, " [ 24 ]"));
        of_mm(event.register$item("gtceu:pollucite_ore"), as(0, " [ 12 ]"), as(5, " [ 32 ]"));
        of_mm(event.register$item("gtceu:powellite_ore"), as(0, " [ 11 ]"));
        of_mm(event.register$item("gtceu:pyrite_ore"), as(0, " [ 8, 18 ]"));
        of_mm(event.register$item("gtceu:pyrochlore_ore"), as(0, " [ 18 ]"));
        of_mm(event.register$item("gtceu:pyrolusite_ore"), as(0, " [ 5, 9 ]"));
        of_mm(event.register$item("gtceu:pyrope_ore"), as(0, " [ 2 ]"), as(2, " [ 27 ]"));
        of_mm(event.register$item("gtceu:quartzite_ore"), as(0, " [ 13, 17 ]"));
        of_mm(event.register$item("gtceu:rare_earth_metal_ore"), as(3, " [ 28 ]"));
        of_mm(event.register$item("gtceu:realgar_ore"), as(0, " [ 6 ]"));
        of_mm(event.register$item("gtceu:red_garnet_ore"), as(0, " [ 14 ]"), as(1, " [ 25 ]"), as(2, " [ 27 ]"));
        of_mm(event.register$item("gtceu:redstone_ore"), as(0, " [ 5, 17 ]"), as(2, " [ 27 ]"));
        of_mm(event.register$item("gtceu:rock_salt_ore"), as(0, " [ 16 ]"));
        of_mm(event.register$item("gtceu:rubidium_ore"), as(0, " [ 7 ]"));
        of_mm(event.register$item("gtceu:ruby_ore"), as(0, " [ 5, 17 ]"));
        of_mm(event.register$item("gtceu:salt_ore"), as(0, " [ 16 ]"), as(3, " [ 29 ]"));
        of_mm(event.register$item("gtceu:saltpeter_ore"), as(0, " [ 7 ]"));
        of_mm(event.register$item("gtceu:sapphire_ore"), as(0, " [ 2 ]"), as(5, " [ 32 ]"));
        of_mm(event.register$item("gtceu:scheelite_ore"), as(0, " [ 23 ]"));
        of_mm(event.register$item("gtceu:silver_ore"), as(0, " [ 11 ]"), as(4, " [ 31 ]"));
        of_mm(event.register$item("gtceu:soapstone_ore"), as(0, " [ 4 ]"));
        of_mm(event.register$item("gtceu:sodalite_ore"), as(0, " [ 10 ]"), as(4, " [ 30 ]"));
        of_mm(event.register$item("gtceu:spessartine_ore"), as(0, " [ 5 ]"));
        of_mm(event.register$item("gtceu:sphalerite_ore"), as(0, " [ 18 ]"));
        of_mm(event.register$item("gtceu:spodumene_ore"), as(0, " [ 16 ]"));
        of_mm(event.register$item("gtceu:starmetal_ore"), as(5, " [ 32 ]"));
        of_mm(event.register$item("gtceu:stibnite_ore"), as(0, " [ 2 ]"));
        of_mm(event.register$item("gtceu:sulfur_ore"), as(0, " [ 18 ]"));
        of_mm(event.register$item("gtceu:talc_ore"), as(0, " [ 4 ]"));
        of_mm(event.register$item("gtceu:tantalite_ore"), as(0, " [ 5, 9 ]"));
        of_mm(event.register$item("gtceu:tartarite_ore"), as(4, " [ 31 ]"));
        of_mm(event.register$item("gtceu:tellurium_ore"), as(0, " [ 23 ]"));
        of_mm(event.register$item("gtceu:tetrahedrite_ore"), as(0, " [ 1 ]"));
        of_mm(event.register$item("gtceu:tin_ore"), as(2, " [ 26 ]"));
        of_mm(event.register$item("gtceu:titanium_ore"), as(0, " [ 21 ]"));
        of_mm(event.register$item("gtceu:topaz_ore"), as(0, " [ 15 ]"), as(1, " [ 25 ]"));
        of_mm(event.register$item("gtceu:tricalcium_phosphate_ore"), as(0, " [ 18 ]"));
        of_mm(event.register$item("gtceu:trinium_compound_ore"), as(0, " [ 24 ]"));
        of_mm(event.register$item("gtceu:tungstate_ore"), as(0, " [ 23 ]"));
        of_mm(event.register$item("gtceu:tungsten_ore"), as(0, " [ 23 ]"));
        of_mm(event.register$item("gtceu:uraninite_ore"), as(0, " [ 2 ]"), as(3, " [ 29 ]"));
        of_mm(event.register$item("gtceu:uruium_ore"), as(2, " [ 26 ]"));
        of_mm(event.register$item("gtceu:vanadium_magnetite_ore"), as(0, " [ 9, 19 ]"));
        of_mm(event.register$item("gtceu:vibranium_ore"), as(4, " [ 31 ]"));
        of_mm(event.register$item("gtceu:wulfenite_ore"), as(0, " [ 10 ]"));
        of_mm(event.register$item("gtceu:yellow_garnet_ore"), as(0, " [ 14 ]"), as(5, " [ 32 ]"));
        of_mm(event.register$item("gtceu:yellow_limonite_ore"), as(0, " [ 3, 12 ]"));
        of_mm(event.register$item("gtceu:zeolite_ore"), as(0, " [ 6 ]"));
        of_mm(event.register$item("gtceu:zircon_ore"), as(0, " [ 13 ]"));
        of_mm(event.register$item("minecraft:ancient_debris"), as(0, " [ 17 ]"));

        of_dm(event.register$fluid("gtceu:ammonia"), as(0, " [ 8 ]"));
        of_dm(event.register$fluid("gtceu:argon"), as(1, " [ 15 ]"));
        of_dm(event.register$fluid("gtceu:barnarda_air"), as(2, " [ 28 ]"));
        of_dm(event.register$fluid("gtceu:black_dwarf_mtter"), as(5, " [ 30 ]"));
        of_dm(event.register$fluid("gtceu:bromine"), as(2, " [ 27 ]"));
        of_dm(event.register$fluid("gtceu:carbon_dioxide"), as(0, " [ 6 ]"));
        of_dm(event.register$fluid("gtceu:carbon_monoxide"), as(0, " [ 11 ]"));
        of_dm(event.register$fluid("gtceu:chlorine"), as(0, " [ 9 ]"));
        of_dm(event.register$fluid("gtceu:coal_gas"), as(2, " [ 26 ]"));
        of_dm(event.register$fluid("gtceu:deuterium"), as(2, " [ 20 ]"));
        of_dm(event.register$fluid("gtceu:fluorine"), as(0, " [ 10 ]"));
        of_dm(event.register$fluid("gtceu:heavy_fuel"), as(2, " [ 22 ]"));
        of_dm(event.register$fluid("gtceu:helium_3"), as(1, " [ 19 ]"));
        of_dm(event.register$fluid("gtceu:helium"), as(0, " [ 2 ]"));
        of_dm(event.register$fluid("gtceu:hydrogen"), as(0, " [ 1 ]"));
        of_dm(event.register$fluid("gtceu:krypton"), as(1, " [ 16 ]"));
        of_dm(event.register$fluid("gtceu:light_fuel"), as(2, " [ 23 ]"));
        of_dm(event.register$fluid("gtceu:methane"), as(0, " [ 4 ]"));
        of_dm(event.register$fluid("gtceu:naphtha"), as(2, " [ 24 ]"));
        of_dm(event.register$fluid("gtceu:neon"), as(1, " [ 14 ]"));
        of_dm(event.register$fluid("gtceu:nitrogen_dioxide"), as(0, " [ 7 ]"));
        of_dm(event.register$fluid("gtceu:nitrogen"), as(0, " [ 3 ]"));
        of_dm(event.register$fluid("gtceu:oxygen"), as(0, " [ 12 ]"));
        of_dm(event.register$fluid("gtceu:radon"), as(1, " [ 18 ]"));
        of_dm(event.register$fluid("gtceu:refinery_gas"), as(2, " [ 25 ]"));
        of_dm(event.register$fluid("gtceu:sulfur_dioxide"), as(0, " [ 5 ]"));
        of_dm(event.register$fluid("gtceu:tritium"), as(2, " [ 21 ]"));
        of_dm(event.register$fluid("gtceu:unknowwater"), as(1, " [ 13 ]"));
        of_dm(event.register$fluid("gtceu:white_dwarf_mtter"), as(5, " [ 29 ]"));
        of_dm(event.register$fluid("gtceu:xenon"), as(1, " [ 17 ]"));
    }
}
