package org.gtlcore.gtlcore.common.data.source_tooltip.register;

import org.gtlcore.gtlcore.api.event.SourceTooltipRegistrationEvent;
import org.gtlcore.gtlcore.common.data.source_tooltip.SourceTooltip;
import org.gtlcore.gtlcore.common.data.source_tooltip.SpecialComponent;

import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.common.data.GTMaterials;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SourceTooltipRegister$EarlyStage {

    private static void of(SourceTooltip tooltip, Component... components) {
        if (tooltip == null) return;
        if (components == null || components.length == 0) return;
        tooltip.get_or_create$control(SpecialComponent.component_default$control)
                .add(components);
    }

    public static void register(SourceTooltipRegistrationEvent event) {
        register_dust(event);
        register_ingot(event);
        register_fluid(event);
        register_hot_ingot(event);
    }

    private static void register_ingot(SourceTooltipRegistrationEvent event) {
        // 前期: 化学浸洗机处理热锭
        String[] a = { "gtceu:silicon_ingot", "gtceu:kanthal_ingot" };
        var component_a = SpecialComponent.create(Component.translatable("gtceu.chemical_bath").withStyle(SpecialComponent.color_recipe_type), SpecialComponent.component_early_stage);
        for (var item : a) of(event.register$item(item), component_a);
    }

    private static void register_dust(SourceTooltipRegistrationEvent event) {
        // 前期: 核废料处理
        String[] a = { "gtceu:neptunium_dust", "gtceu:protactinium_dust", "gtceu:polonium_dust" };
        var component_a = SpecialComponent.create(Component.translatable("gtlcore.source_tooltip.productionline.nuclear_waste").withStyle(ChatFormatting.GOLD), SpecialComponent.component_early_stage);
        for (var item : a) of(event.register$item(item), component_a);
        // 前期: 铂系处理
        String[] b = { "gtceu:palladium_dust", "gtceu:ruthenium_dust", "gtceu:iridium_dust", "gtceu:rhodium_dust", "gtceu:osmium_dust" };
        var component_b = SpecialComponent.create(Component.translatable("gtlcore.source_tooltip.series.platinum_group_precess").withStyle(SpecialComponent.color_recipe_type), SpecialComponent.component_early_stage);
        for (var item : b) of(event.register$item(item), component_b);
        // 前期: 离心机
        String[] c = { "gtceu:plutonium_241_dust", "gtceu:iodine_dust", "gtceu:arsenic_dust" };
        var component_c = SpecialComponent.create(Component.translatable("gtceu.centrifuge").withStyle(SpecialComponent.color_recipe_type), SpecialComponent.component_early_stage);
        for (var item : c) of(event.register$item(item), component_c);
    }

    private static void register_fluid(SourceTooltipRegistrationEvent event) {
        // 前期: 提取机
        String[] a = { "gtceu:titanium_50" };
        var component_a = SpecialComponent.create(Component.translatable("gtceu.extractor").withStyle(SpecialComponent.color_recipe_type), SpecialComponent.component_early_stage);
        for (var fluid : a) of(event.register$fluid(fluid), component_a);
    }

    private static void register_hot_ingot(SourceTooltipRegistrationEvent event) {
        // 前期: 电力高炉 第一次非粉
        var component_electric_blast_furnace = Component.translatable("gtceu.electric_blast_furnace").withStyle(SpecialComponent.color_recipe_type);
        of(event.register$item("gtceu:hot_titanium_50_ingot"), SpecialComponent.create(component_electric_blast_furnace, SpecialComponent.component_space, Component.translatable("material.gtceu.titanium_50_tetrachloride").withStyle(ChatFormatting.GOLD), SpecialComponent.component_early_stage));
        of(event.register$item("gtceu:hot_naquadria_ingot"), SpecialComponent.create(component_electric_blast_furnace, SpecialComponent.component_space, TagPrefix.dust.getLocalizedName(GTMaterials.NaquadriaSulfate).withStyle(ChatFormatting.GOLD), SpecialComponent.component_early_stage));
    }
}
