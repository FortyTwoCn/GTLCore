package org.gtlcore.gtlcore.common.data.source_tooltip.register;

import org.gtlcore.gtlcore.api.event.SourceTooltipRegistrationEvent;
import org.gtlcore.gtlcore.common.data.source_tooltip.SpecialComponent;

import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SourceTooltipRegister$OreProcessing {

    private static final Component component_ore_process = Component.translatable("gtlcore.source_tooltip.series.ore_processing");

    public static void register(SourceTooltipRegistrationEvent event) {
        // 从集成矿石处理自动获取
        // TODO 后面需要给大量首次获取来源不是矿石处理的粉添加 前期提示
        String[] items = {
                "gtceu:pitchblende_dust", "gtceu:thorium_dust", "gtceu:uraninite_dust", "gtceu:lead_dust", "gtceu:purified_bornite_ore", "gtceu:pyrite_dust", "gtceu:gold_dust", "gtceu:celestine_dust", "gtceu:chromite_dust", "gtceu:iron_dust", "gtceu:magnesium_dust",
                "gtceu:aluminium_dust", "gtceu:bauxite_dust", "gtceu:ilmenite_dust", "gtceu:apatite_dust", "gtceu:tricalcium_phosphate_dust", "gtceu:vanadium_magnetite_dust", "gtceu:magnetite_dust", "gtceu:copper_dust", "gtceu:cobalt_dust", "gtceu:nickel_dust", "gtceu:saltpeter_dust", "gtceu:spessartine_dust",
                "gtceu:red_garnet_gem", "gtceu:red_garnet_dust", "gtceu:galena_dust", "gtceu:sulfur_dust", "gtceu:silver_dust", "gtceu:tetrahedrite_dust", "gtceu:antimony_dust", "gtceu:zinc_dust", "gtceu:cadmium_dust", "gtceu:exquisite_spessartine_gem", "gtceu:flawless_spessartine_gem", "gtceu:spessartine_gem",
                "gtceu:manganese_dust", "gtceu:titanium_dust", "gtceu:alien_algae_dust", "gtceu:paper_dust", "gtceu:agar_dust", "gtceu:opal_dust", "gtceu:opal_gem", "gtceu:mica_dust", "gtceu:potassium_dust", "gtceu:platinum_dust", "gtceu:quartzite_dust", "gtceu:certus_quartz_gem",
                "gtceu:certus_quartz_dust", "gtceu:barite_dust", "gtceu:molybdenum_dust", "gtceu:zeolite_dust", "gtceu:calcium_dust", "gtceu:silicon_dust", "gtceu:lapis_dust", "gtceu:lazurite_gem", "gtceu:lazurite_dust", "gtceu:sodalite_dust", "gtceu:magnesite_dust", "gtceu:cobaltite_dust",
                "gtceu:talc_dust", "gtceu:clay_dust", "gtceu:carbon_dust", "gtceu:ruby_dust", "gtceu:chromium_dust", "gtceu:grossular_gem", "gtceu:gallium_dust", "gtceu:rutile_dust", "gtceu:electrotine_dust", "minecraft:redstone", "gtceu:electrum_dust", "gtceu:realgar_dust",
                "gtceu:pyrope_dust", "gtceu:mithril_dust", "gtceu:actinium_dust", "gtceu:technetium_dust", "gtceu:tungsten_dust", "gtceu:lithium_dust", "gtceu:palladium_dust", "gtceu:tin_dust", "gtceu:diamond_dust", "gtceu:diatomite_dust", "gtceu:hematite_dust", "gtceu:sapphire_dust",
                "gtceu:plutonium_239_dust", "gtceu:emerald_dust", "gtceu:beryllium_dust", "gtceu:exquisite_sapphire_gem", "gtceu:flawless_sapphire_gem", "gtceu:sapphire_gem", "gtceu:green_sapphire_dust", "gtceu:calorite_dust", "gtceu:spodumene_dust", "gtceu:tartarite_dust", "gtceu:americium_dust", "gtceu:grossular_dust",
                "gtceu:bentonite_dust", "gtceu:uruium_dust", "gtceu:europium_dust", "gtceu:powellite_dust", "gtceu:molybdenite_dust", "gtceu:soapstone_dust", "gtceu:silicon_dioxide_dust", "gtceu:starmetal_dust", "gtceu:polonium_dust", "gtceu:malachite_dust", "gtceu:goethite_dust", "gtceu:zincite_dust",
                "gtceu:calcite_dust", "gtceu:cooperite_dust", "gtceu:monazite_dust", "gtceu:exquisite_monazite_gem", "gtceu:flawless_monazite_gem", "gtceu:monazite_gem", "gtceu:neodymium_dust", "gtceu:graphite_dust", "gtceu:coal_dust", "minecraft:coal", "gtceu:exquisite_coal_gem", "gtceu:flawless_coal_gem",
                "minecraft:emerald", "gtceu:rare_earth_metal_dust", "gtceu:rare_earth_dust", "gtceu:nether_quartz_dust", "gtceu:quartzite_gem", "gtceu:exquisite_nether_quartz_gem", "gtceu:flawless_nether_quartz_gem", "minecraft:quartz", "gtceu:lepidolite_dust", "gtceu:asbestos_dust", "gtceu:rock_salt_dust", "gtceu:salt_gem",
                "gtceu:salt_dust", "gtceu:borax_dust", "gtceu:blue_topaz_dust", "gtceu:topaz_gem", "gtceu:topaz_dust", "gtceu:tungstate_dust", "gtceu:cobalt_oxide_dust", "gtceu:yellow_garnet_gem", "gtceu:yellow_garnet_dust", "gtceu:exquisite_grossular_gem", "gtceu:flawless_grossular_gem", "gtceu:cinnabar_gem",
                "gtceu:cinnabar_dust", "gtceu:rubidium_dust", "gtceu:caesium_dust", "gtceu:gypsum_dust", "gtceu:orichalcum_dust", "gtceu:boron_dust", "gtceu:phosphate_dust", "gtceu:almandine_dust", "gtceu:pyrolusite_dust", "gtceu:tantalite_dust", "gtceu:exquisite_apatite_gem", "gtceu:flawless_apatite_gem",
                "gtceu:apatite_gem", "gtceu:purified_cooperite_ore", "gtceu:exquisite_opal_gem", "gtceu:flawless_opal_gem", "gtceu:indium_dust", "gtceu:jasper_dust", "gtceu:exquisite_sodalite_gem", "gtceu:flawless_sodalite_gem", "gtceu:sodalite_gem", "gtceu:pollucite_dust", "gtceu:exquisite_ruby_gem", "gtceu:flawless_ruby_gem",
                "gtceu:ruby_gem", "gtceu:trona_dust", "gtceu:sodium_dust", "gtceu:soda_ash_dust", "gtceu:garnierite_dust", "gtceu:alunite_dust", "gtceu:cassiterite_dust", "gtceu:bismuth_dust", "gtceu:sphalerite_dust", "gtceu:oilsands_dust", "gtceu:stibnite_dust", "gtceu:antimony_trioxide_dust",
                "gtceu:ostrum_dust", "gtceu:pyrochlore_dust", "gtceu:olivine_dust", "gtceu:pyrope_gem", "gtceu:amethyst_dust", "minecraft:amethyst_shard", "gtceu:andradite_gem", "gtceu:andradite_dust", "gtceu:tellurium_dust", "gtceu:vibranium_dust", "gtceu:plutonium_241_dust", "gtceu:enriched_naquadah_dust",
                "gtceu:naquadah_dust", "gtceu:pentlandite_dust", "gtceu:yellow_limonite_dust", "minecraft:glowstone_dust", "gtceu:bornite_dust", "gtceu:exquisite_amethyst_gem", "gtceu:flawless_amethyst_gem", "gtceu:bastnasite_dust", "gtceu:exquisite_cinnabar_gem", "gtceu:flawless_cinnabar_gem", "gtceu:exquisite_jasper_gem", "gtceu:flawless_jasper_gem",
                "gtceu:jasper_gem", "gtceu:kyanite_dust", "gtceu:glauconite_sand_dust", "gtceu:rock_salt_gem", "gtceu:exquisite_salt_gem", "gtceu:flawless_salt_gem", "gtceu:exquisite_yellow_garnet_gem", "gtceu:flawless_yellow_garnet_gem", "gtceu:wulfenite_dust", "gtceu:fullers_earth_dust", "gtceu:exquisite_certus_quartz_gem", "gtceu:flawless_certus_quartz_gem",
                "gtceu:uvarovite_dust", "gtceu:chalcocite_dust", "gtceu:blue_topaz_gem", "gtceu:malachite_gem", "gtceu:exquisite_malachite_gem", "gtceu:flawless_malachite_gem", "gtceu:desh_dust", "gtceu:zircon_dust", "gtceu:trinium_compound_dust", "gtceu:exquisite_quartzite_gem", "gtceu:flawless_quartzite_gem", "gtceu:exquisite_topaz_gem",
                "gtceu:flawless_topaz_gem", "gtceu:enderium_dust", "gtceu:endstone_dust", "gtceu:ender_pearl_dust", "gtceu:niobium_dust", "gtceu:tantalum_dust", "gtceu:massicot_dust", "gtceu:garnet_sand_dust", "gtceu:adamantine_compounds_dust", "gtceu:ignis_crystal_dust", "gtceu:perditio_crystal_dust", "gtceu:granitic_mineral_sand_dust",
                "gtceu:deepslate_dust", "gtceu:earth_crystal_dust", "gtceu:bloodstone_dust", "gtceu:scheelite_dust", "gtceu:exquisite_pyrope_gem", "gtceu:flawless_pyrope_gem", "gtceu:basaltic_mineral_sand_dust", "gtceu:basalt_dust", "gtceu:samarium_refined_powder_dust", "gtceu:exquisite_lazurite_gem", "gtceu:flawless_lazurite_gem", "gtceu:vanadium_dust",
                "gtceu:purified_tetrahedrite_ore", "gtceu:exquisite_green_sapphire_gem", "gtceu:flawless_green_sapphire_gem", "gtceu:green_sapphire_gem", "gtceu:exquisite_olivine_gem", "gtceu:flawless_olivine_gem", "gtceu:olivine_gem", "gtceu:exquisite_red_garnet_gem", "gtceu:flawless_red_garnet_gem", "gtceu:infused_gold_dust", "gtceu:exquisite_rock_salt_gem", "gtceu:flawless_rock_salt_gem",
                "gtceu:chalcopyrite_dust", "gtceu:exquisite_emerald_gem", "gtceu:flawless_emerald_gem", "gtceu:exquisite_blue_topaz_gem", "gtceu:flawless_blue_topaz_gem", "gtceu:exquisite_almandine_gem", "gtceu:flawless_almandine_gem", "gtceu:almandine_gem", "gtceu:exquisite_diamond_gem", "gtceu:flawless_diamond_gem", "minecraft:diamond", "gtceu:exquisite_realgar_gem",
                "gtceu:flawless_realgar_gem", "gtceu:realgar_gem", "gtceu:cassiterite_sand_dust", "gtceu:exquisite_lapis_gem", "gtceu:flawless_lapis_gem", "minecraft:lapis_lazuli", "gtceu:force_dust", "gtceu:lanthanum_dust", "gtceu:purified_chalcocite_ore", "gtceu:raw_tengam_dust"
        };
        var component = Component.translatable("gtlcore.source_tooltip.series.ore_processing");
        for (var item : items) {
            var tooltip = event.register$item(item);
            if (tooltip == null) continue;
            tooltip.get_or_create$control(SpecialComponent.component_default$control)
                    .add(component_ore_process);
        }
    }
}
