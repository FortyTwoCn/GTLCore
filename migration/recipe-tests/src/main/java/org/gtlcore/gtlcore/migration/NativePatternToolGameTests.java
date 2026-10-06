package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.item.PatternTestBehavior;
import org.gtlcore.gtlcore.api.item.tool.ae2.patternTool.Ae2BaseProcessingPatternHelper;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.integration.ae2.pattern.PatternQuickUploadMetadata;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import brachy.modularui.factory.PlayerInventoryGuiData;
import brachy.modularui.factory.PlayerInventoryUIFactory;
import brachy.modularui.factory.inventory.InventoryTypes;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.*;
import brachy.modularui.widget.WidgetTree;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativePatternToolGameTests {
    private static net.neoforged.neoforge.common.util.FakePlayer player(GameTestHelper helper, String name) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)), name));
        player.getInventory().clearContent(); return player;
    }
    private static PanelSyncManager menu(net.minecraft.server.level.ServerPlayer player, int slot) {
        var data = PlayerInventoryGuiData.of(player, InventoryTypes.PLAYER, null, slot);
        var sync = new PanelSyncManager(new ModularSyncManager(false), true); data.addSyncHandlers(sync);
        var panel = PlayerInventoryUIFactory.INSTANCE.createPanel(data, sync, new UISettings(brachy.modularui.api.RecipeViewerSettings.DUMMY));
        WidgetTree.collectSyncValues(sync, panel); return sync;
    }
    private static void text(PanelSyncManager sync, RegistryFriendlyByteBuf packet, String field, String text) {
        packet.clear(); brachy.modularui.utils.NetworkUtils.writeStringSafe(packet, text, Short.MAX_VALUE - 74);
        sync.receiveWidgetUpdate("patternTest_" + field + ":0", false, 0, packet);
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void offhandNativeMenuPacketsBoundAndPersistIndependentPatternToolSettings(GameTestHelper helper) {
        var player = player(helper, "pattern_tool_settings"); var item = NativeAePatternFixtures.PATTERN_TEST.asStack(); var independent = item.copy();
        var unrelated = new net.minecraft.nbt.CompoundTag(); unrelated.putString("unrelated", "keep"); item.set(DataComponents.CUSTOM_DATA, CustomData.of(unrelated));
        player.getInventory().setItem(40, item); player.getInventory().setItem(0, new ItemStack(Items.APPLE));
        var sync = menu(player, 40); var packet = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            text(sync, packet, "generatorType", "chemical_reactor"); text(sync, packet, "generatorCircuit", "99999"); text(sync, packet, "scale", "-3");
            text(sync, packet, "inputWhite", "iron water"); text(sync, packet, "outputBlack", "gold");
            var settings = PatternTestBehavior.getSettings(item);
            helper.assertTrue(settings.generatorCircuit() == 32 && settings.scale() == 1 && settings.generatorType().equals("chemical_reactor"), "Native pattern tool accepted unbounded numbers or lost its offhand target");
            text(sync, packet, "generatorCircuit", "invalid");
            helper.assertTrue(PatternTestBehavior.getSettings(item).generatorCircuit() == 32, "Invalid packet corrupted the saved circuit");
            var loaded = ItemStack.parse(helper.getLevel().registryAccess(), item.save(helper.getLevel().registryAccess())).orElseThrow();
            helper.assertTrue(settings.equals(PatternTestBehavior.getSettings(loaded)) && loaded.get(DataComponents.CUSTOM_DATA).copyTag().getString("unrelated").equals("keep"), "Native item save lost configuration or unrelated components");
            helper.assertTrue(PatternTestBehavior.getSettings(independent).generatorType().isEmpty() && player.getMainHandItem().is(Items.APPLE), "Settings leaked to another physical tool or the main hand");
            var data = PlayerInventoryGuiData.of(player, InventoryTypes.PLAYER, null, 40);
            var manager = new ModularSyncManager(false); var main = new PanelSyncManager(manager, true); data.addSyncHandlers(main);
            var uiSettings = new UISettings(brachy.modularui.api.RecipeViewerSettings.DUMMY);
            var panel = PlayerInventoryUIFactory.INSTANCE.createPanel(data, main, uiSettings); WidgetTree.collectSyncValues(main, panel);
            var menu = new brachy.modularui.screen.ModularContainerMenu(63); menu.construct(player, manager, uiSettings, panel.getName(), data);
            var appleSlot = main.findSyncHandler("player", 0, ItemSlotSyncHandler.class).getSlot();
            menu.clicked(menu.slots.indexOf(appleSlot), 40, net.minecraft.world.inventory.ClickType.SWAP, player);
            helper.assertTrue(player.getInventory().getItem(40) == item && player.getMainHandItem().is(Items.APPLE), "Offhand swap packet moved the open pattern tool");
        } finally { packet.release(); }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void serverButtonFiltersRealRecipeHoldersAndGivesExactlyOneEncodedPattern(GameTestHelper helper) {
        var player = player(helper, "pattern_tool_generation"); var item = NativeAePatternFixtures.PATTERN_TEST.asStack(); player.getInventory().setItem(0, item);
        var recipeManager = helper.getLevel().getRecipeManager(); var previous = List.copyOf(recipeManager.getRecipes());
        var input = new ItemStack(Items.IRON_INGOT); input.set(DataComponents.CUSTOM_NAME, Component.literal("world generator input"));
        var good = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("world_pattern_good").inputItems(LongIngredient.create(input, 9_007_199_254_740_993L)).circuitMeta(17).outputItems(Items.DIAMOND, 2).build();
        var blacklist = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("world_pattern_blacklist").inputItems(Items.IRON_INGOT).circuitMeta(17).outputItems(Items.GOLD_INGOT).build();
        var overflow = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("world_pattern_overflow").inputItems(LongIngredient.create(input, Long.MAX_VALUE)).circuitMeta(17).outputItems(Items.DIAMOND).build();
        var otherCircuit = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("world_pattern_other_circuit").inputItems(Items.IRON_INGOT).circuitMeta(18).outputItems(Items.DIAMOND).build();
        var packet = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            // A synchronous replacement is restored before other GameTests can tick.
            recipeManager.replaceRecipes(List.of(new RecipeHolder<>(good.id, good), new RecipeHolder<>(blacklist.id, blacklist), new RecipeHolder<>(overflow.id, overflow), new RecipeHolder<>(otherCircuit.id, otherCircuit)));
            var sync = menu(player, 0);
            text(sync, packet, "generatorType", GTRecipeTypes.CHEMICAL_RECIPES.value().id.toString()); text(sync, packet, "generatorCircuit", "17"); text(sync, packet, "scale", "3");
            text(sync, packet, "inputWhite", "iron"); text(sync, packet, "inputBlack", "gold"); text(sync, packet, "outputWhite", "diamond"); text(sync, packet, "outputBlack", "gold");
            packet.clear(); packet.writeVarInt(0); packet.writeByte(0); sync.receiveWidgetUpdate("patternTest_generate:0", false, 1, packet);
            var patterns = player.getInventory().items.stream().filter(appeng.api.crafting.PatternDetailsHelper::isEncodedPattern).toList();
            helper.assertTrue(patterns.size() == 1 && patterns.getFirst().getCount() == 1, "Native server action gave an incorrect number of physical patterns");
            var encoded = patterns.getFirst(); var decoded = Ae2BaseProcessingPatternHelper.decodeToAEProcessingPattern(encoded, player);
            helper.assertTrue(decoded.getSparseInputs().size() == 1 && decoded.getSparseInputs().getFirst().amount() == 27_021_597_764_222_979L && decoded.getSparseInputs().getFirst().what().equals(appeng.api.stacks.AEItemKey.of(input)), "Generated pattern lost its long amount, input components or circuit filtering");
            helper.assertTrue(decoded.getSparseOutputs().getFirst().amount() == 6 && PatternQuickUploadMetadata.readRecipeTypeIds(encoded).contains(good.recipeType.id) && encoded.get(DataComponents.LORE).lines().size() == 2, "Generated pattern lost outputs, voltage lore or quick-upload metadata");
            text(sync, packet, "analysisType", good.recipeType.id.toString()); text(sync, packet, "analysisCircuit", "17");
            packet.clear(); packet.writeVarInt(0); packet.writeByte(0); sync.receiveWidgetUpdate("patternTest_analyze:0", false, 1, packet);
            helper.assertTrue(player.getInventory().getItem(0) == item, "Server button replaced its held physical tool");
        } finally { recipeManager.replaceRecipes(previous); packet.release(); }
        helper.succeed();
    }
}
