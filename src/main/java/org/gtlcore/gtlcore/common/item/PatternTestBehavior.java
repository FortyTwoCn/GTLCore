package org.gtlcore.gtlcore.common.item;

import org.gtlcore.gtlcore.api.item.tool.ae2.patternTool.*;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.mui.IItemUIHolder;
import com.gregtechceu.gtceu.common.mui.GTGuiTextures;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.context.UseOnContext;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.factory.PlayerInventoryGuiData;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.*;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.textfield.TextFieldWidget;
import org.slf4j.LoggerFactory;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/** Each physical tool owns its settings; generation and analysis execute only on the server. */
public class PatternTestBehavior implements IItemUIHolder {
    public static final PatternTestBehavior INSTANCE = new PatternTestBehavior();
    private static final String DATA = "gtlcore_pattern_test";
    private static final String[] KEYS = {"analysisType", "analysisCircuit", "generatorType", "generatorCircuit", "scale",
            "inputWhite", "inputBlack", "outputWhite", "outputBlack"};
    private static final String[] LABELS = {"set_recipe_type", "set_programmed_circuit", "set_recipe_type", "set_programmed_circuit",
            "set_pattern_multiplier", "input_whitelist_keywords", "input_blacklist_keywords",
            "output_whitelist_keywords", "output_blacklist_keywords"};

    public record Settings(String analysisType, int analysisCircuit, String generatorType, int generatorCircuit,
                           int scale, String inputWhite, String inputBlack, String outputWhite, String outputBlack) {}
    private static CompoundTag settings(ItemStack item) {
        return item.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(DATA);
    }
    private static boolean numeric(int index) { return index == 1 || index == 3 || index == 4; }
    private static long clamp(int index, long number) {
        return Math.clamp(number, index == 4 ? 1 : 0, index == 4 ? Integer.MAX_VALUE : 32);
    }
    private static String getSetting(ItemStack item, int index) {
        var settings = settings(item);
        return numeric(index) ? Long.toString(clamp(index, settings.contains(KEYS[index]) ? settings.getInt(KEYS[index]) : index == 4 ? 1 : 0)) :
                settings.getString(KEYS[index]);
    }
    public static Settings getSettings(ItemStack item) {
        return new Settings(getSetting(item, 0), Integer.parseInt(getSetting(item, 1)), getSetting(item, 2),
                Integer.parseInt(getSetting(item, 3)), Integer.parseInt(getSetting(item, 4)),
                getSetting(item, 5), getSetting(item, 6), getSetting(item, 7), getSetting(item, 8));
    }
    public static void setSetting(ItemStack item, String key, String value) {
        int index = Arrays.asList(KEYS).indexOf(key);
        if (index < 0) throw new IllegalArgumentException("Unknown pattern test setting: " + key);
        var tag = item.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        var settings = tag.getCompound(DATA);
        if (numeric(index)) {
            try { settings.putInt(key, (int) clamp(index, Long.parseLong(value))); }
            catch (NumberFormatException ignored) { return; }
        } else {
            int limit = index == 0 || index == 2 ? 256 : 1024;
            settings.putString(key, value.substring(0, Math.min(limit, value.length())).trim());
        }
        tag.put(DATA, settings);
        item.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
    @Override public ModularPanel<?> buildUI(PlayerInventoryGuiData<?> data, PanelSyncManager sync, UISettings uiSettings) {
        var panel = ModularPanel.defaultPanel("patternTest", 416, 240).background(GTGuiTextures.BACKGROUND)
                .child(Text.lang("gtlcore.gui.recipe_conflict_analysis").asWidget().pos(8, 7))
                .child(Text.lang("gtlcore.gui.pattern_debug").asWidget().pos(8, 79));
        for (int i = 0; i < KEYS.length; i++) {
            int index = i;
            int y = i < 2 ? 23 + 18 * i : 92 + 18 * (i - 2);
            var value = SyncHandlers.string(() -> getSetting(data.getUsedItemStack(), index), text -> {
                var item = data.getUsedItemStack();
                setSetting(item, KEYS[index], text);
                data.setUsedItemStack(item);
            }).allowC2S();
            sync.syncValue("patternTest_" + KEYS[i], value);
            String label = i < 5 ? "tooltip.gtlcore." + LABELS[i] : "gui.gtlcore.pattern_test." + KEYS[i];
            panel.child(Text.lang(label).asWidget().pos(8, y + 4).width(158).height(12)
                    .tooltip(tooltip -> tooltip.addLine(Component.translatable("tooltip.gtlcore." + LABELS[index]))));
            var field = new TextFieldWidget().value(value).size(238, 16).pos(170, y);
            if (numeric(i)) field.setNumbersLong(number -> clamp(index, number));
            panel.child(field);
        }
        action(panel, sync, "analyze", "gtlcore.gui.start_analysis", 59, data, item -> useAnalysisRecipesBaby((ServerPlayer) data.getPlayer(), item));
        action(panel, sync, "generate", "gtlcore.gui.get_pattern", 220, data, item -> useAe2PatternGenerator((ServerPlayer) data.getPlayer(), item));
        return panel;
    }
    private static void action(ModularPanel<?> panel, PanelSyncManager sync, String name, String label, int y,
                               PlayerInventoryGuiData<?> data, Consumer<ItemStack> action) {
        var handler = new InteractionSyncHandler().setOnMousePressed(mouse -> {
            if (mouse.isLeftMouseButton() && data.getPlayer() instanceof ServerPlayer) action.accept(data.getUsedItemStack());
        });
        sync.syncValue("patternTest_" + name, handler);
        panel.child(new ButtonWidget<>().name("patternTest_" + name).background(GTGuiTextures.BUTTON).syncHandler(handler).overlay(Text.lang(label)).pos(170, y).size(238, 16));
    }
    private static GTRecipeType recipeType(ServerPlayer player, String name) {
        var id = name.isBlank() ? null : ResourceLocation.tryParse(name.contains(":") ? name : "gtceu:" + name);
        var type = id == null ? null : BuiltInRegistries.RECIPE_TYPE.getOptional(id).orElse(null);
        if (type instanceof GTRecipeType gtType) return gtType;
        player.sendSystemMessage(Component.translatable("message.gtlcore.pattern_test.invalid_type", name));
        return null;
    }
    private static void filter(String keywords, Consumer<String[]> filter) {
        if (!keywords.isBlank()) filter.accept(keywords.trim().split("\\s+"));
    }
    public void useAe2PatternGenerator(ServerPlayer player, ItemStack item) {
        var settings = getSettings(item);
        var type = recipeType(player, settings.generatorType());
        if (type == null) return;
        var recipes = new GTRecipeManager(player.server.getRecipeManager().getRecipes());
        recipes.filterRecipesByType(type);
        recipes.filterRecipesByCircuit(settings.generatorCircuit());
        filter(settings.inputWhite(), words -> recipes.filterRecipesByInputsIdArray(words, true));
        filter(settings.inputBlack(), words -> recipes.filterRecipesByInputsIdArray(words, false));
        filter(settings.outputWhite(), words -> recipes.filterRecipesByOutputsIdArray(words, true));
        filter(settings.outputBlack(), words -> recipes.filterRecipesByOutputsIdArray(words, false));
        int generated = 0, skipped = 0;
        for (var recipe : recipes.getRecipes()) {
            try {
                var pattern = createPattern(recipe, player, settings.scale());
                // Inventory.placeItemBackInInventory returns overflow to the world without losing patterns.
                player.getInventory().placeItemBackInInventory(pattern);
                generated++;
            } catch (IllegalArgumentException | ArithmeticException exception) {
                skipped++;
                LoggerFactory.getLogger("gtlcore.pattern_tool").warn("Cannot generate AE pattern for {}: {}", recipe.id, exception.getMessage());
            }
        }
        player.sendSystemMessage(Component.translatable("message.gtlcore.pattern_test.generated", generated, skipped));
    }
    public static ItemStack createPattern(GTRecipe recipe, ServerPlayer player, int scale) {
        var pattern = Ae2GtmProcessingPattern.of(recipe, player);
        pattern.setDefaultFilter();
        var decoded = Ae2BaseProcessingPatternHelper.decodeToAEProcessingPattern(pattern.patternStack, player);
        var scaled = decoded == null ? null : Ae2BaseProcessingPatternHelper.multiplyScale(scale, false, decoded, Long.MAX_VALUE, Long.MAX_VALUE);
        if (scaled == null) throw new IllegalArgumentException("Invalid or overflowing pattern multiplier");
        int tier = Math.clamp(RecipeHelper.getRecipeEUtTier(recipe), 0, GTValues.VN.length - 1);
        scaled.set(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable("gui.gtlcore.machine_colon").append(Component.translatable("gtceu." + recipe.recipeType.id.getPath()))
                        .append(Component.translatable("gui.gtlcore.circuit_format", Integer.toString(GTRecipeManager.circuit(recipe)))),
                Component.translatable("gui.gtlcore.voltage_colon", GTValues.VN[tier], Long.toString(recipe.getInputEUt().getTotalEU())))));
        return scaled;
    }
    public void useAnalysisRecipesBaby(ServerPlayer player, ItemStack item) {
        var settings = getSettings(item);
        var type = recipeType(player, settings.analysisType());
        if (type == null) return;
        var recipes = new GTRecipeManager(player.server.getRecipeManager().getRecipes());
        recipes.filterRecipesByType(type);
        var results = new ConflictAnalysisManager(recipes.getRecipes()).useFindConflictForAll(settings.analysisCircuit());
        results.forEach(ConflictAnalysisResult::exportToPrint);
        LoggerFactory.getLogger("gtlcore.pattern_tool").info("Potential recipe conflicts: {} / {} ({}, circuit {})",
                results.size(), recipes.getRecipes().size(), type.id, settings.analysisCircuit());
        player.sendSystemMessage(Component.translatable("message.gtlcore.pattern_test.analyzed", results.size(), recipes.getRecipes().size()));
    }
    /** Retained entry point for callers that request an analysis without a held GUI. */
    public void analysisRecipesBaby(GTRecipeType type, int circuit) {
        var recipes = new GTRecipeManager(); recipes.filterRecipesByType(type);
        new ConflictAnalysisManager(recipes.getRecipes()).useFindConflictForAll(circuit).forEach(ConflictAnalysisResult::exportToPrint);
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer player) player.displayClientMessage(Component.translatable("message.gtlcore.right_click_air_gui"), true);
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
}
