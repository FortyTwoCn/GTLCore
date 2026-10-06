package org.gtlcore.gtlcore.integration.ae2.tag;

import org.gtlcore.gtlcore.integration.ae2.wireless.GTLWirelessAeContent;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.BlockHitResult;
import appeng.menu.locator.ItemMenuHostLocator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import appeng.api.implementations.menuobjects.IMenuItem;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class TagViewCellItem extends Item implements IMenuItem {

    public static final int MAX_EXPRESSION_LENGTH = 256;
    private static final String FILTER_TAG = "TagFilter";
    private static final String WHITELIST_TAG = "Whitelist";
    private static final String BLACKLIST_TAG = "Blacklist";

    public TagViewCellItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) {
            MenuOpener.open(
                    GTLWirelessAeContent.TAG_VIEW_CELL_MENU.get(),
                    player,
                    MenuLocators.forHand(player, hand));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    @Override
    public ItemMenuHost<TagViewCellItem> getMenuHost(Player player, ItemMenuHostLocator locator, @Nullable BlockHitResult hit) {
        return new ItemMenuHost<>(this, player, locator);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.gtlcore.tag_view_cell.configure").withStyle(ChatFormatting.GRAY));
        appendExpression(tooltip, "gui.gtlcore.tag_whitelist", getWhitelist(stack));
        appendExpression(tooltip, "gui.gtlcore.tag_blacklist", getBlacklist(stack));
    }

    public static boolean isTagViewCell(ItemStack stack) {
        return stack.getItem() instanceof TagViewCellItem;
    }

    public static String getWhitelist(ItemStack stack) {
        return getExpression(stack, WHITELIST_TAG);
    }

    public static String getBlacklist(ItemStack stack) {
        return getExpression(stack, BLACKLIST_TAG);
    }

    public static void setExpressions(ItemStack stack, String whitelist, String blacklist) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag filter = data.getCompound(FILTER_TAG);
        putExpression(filter, WHITELIST_TAG, whitelist);
        putExpression(filter, BLACKLIST_TAG, blacklist);
        if (filter.isEmpty()) {
            data.remove(FILTER_TAG);
        } else {
            data.put(FILTER_TAG, filter);
        }
        if (data.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
        else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    }

    public static String normalizeExpression(@Nullable String expression) {
        if (expression == null) {
            return "";
        }
        String normalized = expression.trim();
        return normalized.substring(0, Math.min(normalized.length(), MAX_EXPRESSION_LENGTH));
    }

    private static String getExpression(ItemStack stack, String key) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return data.getCompound(FILTER_TAG).getString(key);
    }

    private static void putExpression(CompoundTag filter, String key, String expression) {
        String normalized = normalizeExpression(expression);
        if (normalized.isEmpty()) {
            filter.remove(key);
        } else {
            filter.putString(key, normalized);
        }
    }

    private static void appendExpression(List<Component> tooltip, String labelKey, String expression) {
        if (!expression.isBlank()) {
            tooltip.add(Component.translatable(labelKey)
                    .append(": ")
                    .append(Component.literal(expression))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
