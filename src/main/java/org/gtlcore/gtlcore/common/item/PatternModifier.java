package org.gtlcore.gtlcore.common.item;

import org.gtlcore.gtlcore.api.item.tool.ae2.patternTool.Ae2BaseProcessingPattern;
import com.gregtechceu.gtceu.api.mui.IItemUIHolder;
import com.gregtechceu.gtceu.common.mui.GTGuiTextures;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.factory.PlayerInventoryGuiData;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.SyncHandlers;
import brachy.modularui.widgets.textfield.TextFieldWidget;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

/** Native per-item settings and component-preserving processing-pattern multiplication/division. */
public class PatternModifier implements IItemUIHolder {
    public static final PatternModifier INSTANCE = new PatternModifier();
    private static final String SETTINGS = "gtlcore_pattern_modifier";
    private static final String[] KEYS = {"scale", "divisor", "maxItem", "maxFluid", "applications"};
    private static final long[] DEFAULTS = {1, 1, 1_000_000, 1_000_000, 1};
    private static final long[] MAXIMUMS = {Integer.MAX_VALUE, Integer.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, 16};
    private static final String[] LABELS = {"pattern_multiplier_scale", "pattern_divider_scale", "pattern_max_item_stack", "pattern_max_fluid_stack", "pattern_applied_number"};

    public record Settings(int scale, int divisor, long maxItem, long maxFluid, int applications) {}

    public static Settings getSettings(ItemStack item) {
        return new Settings((int) getSetting(item, 0), (int) getSetting(item, 1), getSetting(item, 2), getSetting(item, 3), (int) getSetting(item, 4));
    }

    private static long getSetting(ItemStack item, int index) {
        var data = item.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(SETTINGS);
        return data.contains(KEYS[index]) ? Math.clamp(data.getLong(KEYS[index]), 1, MAXIMUMS[index]) : DEFAULTS[index];
    }

    public static void setSetting(ItemStack item, String key, long value) {
        int index = java.util.Arrays.asList(KEYS).indexOf(key);
        if (index < 0) throw new IllegalArgumentException("Unknown pattern modifier setting: " + key);
        var tag = item.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        var data = tag.getCompound(SETTINGS);
        data.putLong(key, Math.clamp(value, 1, MAXIMUMS[index])); tag.put(SETTINGS, data);
        item.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public ModularPanel<?> buildUI(PlayerInventoryGuiData<?> data, PanelSyncManager sync, UISettings settings) {
        var panel = ModularPanel.defaultPanel("patternModifier", 290, 224).background(GTGuiTextures.BACKGROUND)
                .child(Text.lang("gui.gtlcore.pattern_modifier.title").asWidget().pos(8, 6).width(274).height(12))
                .child(Text.lang("gui.gtlcore.pattern_modifier.usage").asWidget().pos(8, 23).width(274).height(28))
                .child(Text.lang("gui.gtlcore.pattern_modifier.order").asWidget().pos(8, 55).width(274).height(28));
        for (int i = 0; i < KEYS.length; i++) {
            int index = i;
            var value = SyncHandlers.string(() -> Long.toString(getSetting(data.getUsedItemStack(), index)), text -> {
                try {
                    var item = data.getUsedItemStack(); setSetting(item, KEYS[index], Long.parseLong(text)); data.setUsedItemStack(item);
                } catch (NumberFormatException ignored) {
                    // An incomplete number is UI editing state; it must not corrupt the saved setting.
                }
            }).allowC2S();
            sync.syncValue("patternModifier_" + KEYS[i], value);
            panel.child(Text.lang("gui.gtlcore.pattern_modifier." + KEYS[i]).asWidget().pos(8, 94 + i * 24).width(136).height(18)
                    .tooltip(tooltip -> tooltip.addLine(Component.translatable("tooltip.gtlcore." + LABELS[index]))));
            panel.child(new TextFieldWidget().setNumbersLong(number -> Math.clamp(number, 1, MAXIMUMS[index]))
                    .value(value).size(130, 16).pos(152, 90 + i * 24));
        }
        return panel;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer player) {
            if (player.isShiftKeyDown()) {
                var inventory = PatternBoxBehavior.resolveProviderInventory(context);
                if (inventory == null) {
                    player.displayClientMessage(Component.translatable("message.gtlcore.pattern_provider_only"), true);
                    return InteractionResult.FAIL;
                }
                var settings = getSettings(context.getItemInHand());
                modifyInventory(inventory, player, settings);
                player.displayClientMessage(Component.translatable("message.gtlcore.pattern_updated", settings.applications()), true);
            } else {
                player.displayClientMessage(Component.translatable("message.gtlcore.right_click_air_gui"), true);
            }
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    /** Precompute replacements, preserving stack counts and leaving unencoded or rejected slots untouched. */
    public static boolean modifyInventory(InternalInventory inventory, ServerPlayer player, Settings settings) {
        if (settings.scale() < 1 || settings.divisor() < 1 || settings.maxItem() < 1 || settings.maxFluid() < 1 ||
                settings.applications() < 1 || settings.applications() > 16) throw new IllegalArgumentException("Invalid pattern modifier settings");
        var replacements = new Int2ObjectOpenHashMap<ItemStack>();
        for (int slot = 0; slot < inventory.size(); slot++) {
            var original = inventory.getStackInSlot(slot);
            if (original.isEmpty() || !PatternDetailsHelper.isEncodedPattern(original)) continue;
            var pattern = new Ae2BaseProcessingPattern(original.copy(), player);
            for (int step = 0; step < settings.applications(); step++) {
                pattern.setScale(settings.scale(), false, settings.maxItem(), settings.maxFluid());
                pattern.setScale(settings.divisor(), true);
            }
            var updated = pattern.getPatternItemStack().copyWithCount(original.getCount());
            if (!ItemStack.matches(original, updated) && inventory.isItemValid(slot, updated)) replacements.put(slot, updated);
        }
        for (var entry : replacements.int2ObjectEntrySet()) inventory.setItemDirect(entry.getIntKey(), entry.getValue());
        return !replacements.isEmpty();
    }
}
