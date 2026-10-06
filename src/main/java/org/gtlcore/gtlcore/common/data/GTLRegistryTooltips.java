package org.gtlcore.gtlcore.common.data;

import com.gregtechceu.gtceu.client.util.TooltipHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.BiConsumer;

/** Tooltip callbacks without triggering machine registration during GT's static initialization. */
public final class GTLRegistryTooltips {
    private GTLRegistryTooltips() {}

    public static final BiConsumer<ItemStack, List<Component>> MODIFY = (stack, components) -> components
            .add(Component.translatable("gtlcore.registry.modify")
                    .withStyle(style -> style.withColor(TooltipHelper.RAINBOW.getCurrent())));
    public static final BiConsumer<ItemStack, List<Component>> ADD = (stack, components) -> components
            .add(Component.translatable("gtlcore.registry.add")
                    .withStyle(style -> style.withColor(TooltipHelper.RAINBOW_SLOW.getCurrent())));
}
