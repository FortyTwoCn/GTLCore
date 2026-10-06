package org.gtlcore.gtlcore.common.data.source_tooltip;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Arrays;

@OnlyIn(Dist.CLIENT)
public class SpecialComponent {

    public static final ChatFormatting color_item = ChatFormatting.DARK_GREEN;
    public static final ChatFormatting color_fluid = ChatFormatting.DARK_AQUA;
    public static final ChatFormatting color_circuits = ChatFormatting.GRAY;
    public static final ChatFormatting color_recipe_type = ChatFormatting.LIGHT_PURPLE;
    public static final ChatFormatting color_world_fragment = ChatFormatting.DARK_GREEN;
    public static final ChatFormatting color_space_drone = ChatFormatting.DARK_AQUA;

    public static final Component component_default$shift = Component.translatable("gtlcore.source_tooltip.default.shift");
    public static final Component component_default$control = Component.translatable("gtlcore.source_tooltip.default.control");
    public static final Component component_default$alt = Component.translatable("gtlcore.source_tooltip.default.alt");

    public static final Component component_recommended = Component.translatable("gtlcore.source_tooltip.recommended");
    public static final Component component_early_stage = Component.translatable("gtlcore.source_tooltip.early_stage");
    public static final Component component_space = Component.literal(" ");

    public static Component component_default$custom(String key, String desc) {
        return Component.translatable("gtlcore.source_tooltip.default.custom", Component.literal(key).withStyle(ChatFormatting.GRAY), desc);
    }

    public static Component component_default$custom(String key, Component desc) {
        return Component.translatable("gtlcore.source_tooltip.default.custom", Component.literal(key).withStyle(ChatFormatting.GRAY), desc);
    }

    public static MutableComponent create(Component... components) {
        var result = Component.empty();
        for (Component component : components) {
            result = result.append(component);
        }
        return result;
    }

    public static final Component[] components_world_fragment = Arrays.stream(new MutableComponent[] {
            Component.translatable("item.gtlcore.world_fragments_overworld"),
            Component.translatable("item.gtlcore.world_fragments_reactor"),
            Component.translatable("item.gtlcore.world_fragments_moon"),
            Component.translatable("item.gtlcore.world_fragments_mars"),
            Component.translatable("item.gtlcore.world_fragments_venus"),
            Component.translatable("item.gtlcore.world_fragments_mercury"),
            Component.translatable("item.gtlcore.world_fragments_nether"),
            Component.translatable("item.gtlcore.world_fragments_ceres"),
            Component.translatable("item.gtlcore.world_fragments_io"),
            Component.translatable("item.gtlcore.world_fragments_ganymede"),
            Component.translatable("item.gtlcore.world_fragments_pluto"),
            Component.translatable("item.gtlcore.world_fragments_enceladus"),
            Component.translatable("item.gtlcore.world_fragments_titan"),
            Component.translatable("item.gtlcore.world_fragments_end"),
            Component.translatable("item.gtlcore.world_fragments_glacio"),
            Component.translatable("item.gtlcore.world_fragments_barnarda")
    }).map(it -> it.withStyle(color_world_fragment)).toArray(Component[]::new);

    public static final Component[] components_space_drone = Arrays.stream(new MutableComponent[] {
            Component.translatable("gtlcore.source_tooltip.series.space_drone", Component.literal("1").withStyle(ChatFormatting.AQUA)),
            Component.translatable("gtlcore.source_tooltip.series.space_drone", Component.literal("2").withStyle(ChatFormatting.AQUA)),
            Component.translatable("gtlcore.source_tooltip.series.space_drone", Component.literal("3").withStyle(ChatFormatting.AQUA)),
            Component.translatable("gtlcore.source_tooltip.series.space_drone", Component.literal("4").withStyle(ChatFormatting.AQUA)),
            Component.translatable("gtlcore.source_tooltip.series.space_drone", Component.literal("5").withStyle(ChatFormatting.AQUA)),
            Component.translatable("gtlcore.source_tooltip.series.space_drone", Component.literal("6").withStyle(ChatFormatting.AQUA)),
    }).map(it -> it.withStyle(color_space_drone)).toArray(Component[]::new);

    public static final Component[] components_fusion_reactor = {
            Component.translatable("gtlcore.source_tooltip.series.fusion_reactor", Component.literal("1").withStyle(ChatFormatting.DARK_PURPLE)).withStyle(color_recipe_type),
            Component.translatable("gtlcore.source_tooltip.series.fusion_reactor", Component.literal("2").withStyle(ChatFormatting.DARK_PURPLE)).withStyle(color_recipe_type),
            Component.translatable("gtlcore.source_tooltip.series.fusion_reactor", Component.literal("3").withStyle(ChatFormatting.DARK_PURPLE)).withStyle(color_recipe_type),
            Component.translatable("gtlcore.source_tooltip.series.fusion_reactor", Component.literal("4").withStyle(ChatFormatting.DARK_PURPLE)).withStyle(color_recipe_type),
            Component.translatable("gtlcore.source_tooltip.series.fusion_reactor", Component.literal("5").withStyle(ChatFormatting.DARK_PURPLE)).withStyle(color_recipe_type),
    };
}
