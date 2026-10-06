package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.item.PatternBoxBehavior;
import org.gtlcore.gtlcore.config.ConfigHolder;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.*;
import brachy.modularui.factory.PlayerInventoryGuiData;
import brachy.modularui.factory.PlayerInventoryUIFactory;
import brachy.modularui.factory.inventory.InventoryTypes;
import brachy.modularui.screen.ModularContainerMenu;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.*;
import brachy.modularui.widget.WidgetTree;
import io.netty.buffer.Unpooled;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativePatternBoxGameTests {
    private static ItemStack pattern() {
        var stack = PatternDetailsHelper.encodeProcessingPattern(
                java.util.List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 9_007_199_254_740_993L)),
                java.util.List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1)));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("native pattern box world payload")); return stack;
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void modifierNativeMenuPacketsAndRealProviderReplacementKeepIndependentItemSettings(GameTestHelper helper) {
        var player = FakePlayerFactory.getMinecraft(helper.getLevel()); var previous = player.getInventory().getItem(0);
        var packet = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            var item = NativeAePatternFixtures.PATTERN_MODIFIER.asStack(); var independent = item.copy(); player.getInventory().setItem(0, item);
            var data = PlayerInventoryGuiData.of(player, InventoryTypes.PLAYER, null, 0);
            var manager = new PanelSyncManager(new ModularSyncManager(false), true); data.addSyncHandlers(manager);
            var panel = PlayerInventoryUIFactory.INSTANCE.createPanel(data, manager, new UISettings(brachy.modularui.api.RecipeViewerSettings.DUMMY)); WidgetTree.collectSyncValues(manager, panel);
            brachy.modularui.utils.NetworkUtils.writeStringSafe(packet, "2", Short.MAX_VALUE - 74);
            manager.receiveWidgetUpdate("patternModifier_scale:0", false, 0, packet);
            packet.clear(); brachy.modularui.utils.NetworkUtils.writeStringSafe(packet, "999", Short.MAX_VALUE - 74);
            manager.receiveWidgetUpdate("patternModifier_applications:0", false, 0, packet);
            var settings = org.gtlcore.gtlcore.common.item.PatternModifier.getSettings(item);
            helper.assertTrue(settings.scale() == 2 && settings.applications() == 16 && org.gtlcore.gtlcore.common.item.PatternModifier.getSettings(independent).scale() == 1, "Native modifier packet changed another item's settings or accepted an out-of-range value");
            packet.clear(); brachy.modularui.utils.NetworkUtils.writeStringSafe(packet, "invalid", Short.MAX_VALUE - 74);
            manager.receiveWidgetUpdate("patternModifier_scale:0", false, 0, packet);
            helper.assertTrue(org.gtlcore.gtlcore.common.item.PatternModifier.getSettings(item).scale() == 2, "Invalid C2S text corrupted the saved modifier setting");
            var relative = new BlockPos(2, 1, 2); helper.setBlock(relative, NativeAePatternFixtures.BUFFER.getBlock().defaultBlockState());
            var buffer = (MEPatternBufferPartMachine) helper.getBlockEntity(relative);
            var source = PatternDetailsHelper.encodeProcessingPattern(java.util.List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 8)), java.util.List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1)));
            source.set(DataComponents.CUSTOM_NAME, Component.literal("native modifier world pattern")); buffer.getTerminalPatternInventory().setItemDirect(26, source);
            org.gtlcore.gtlcore.common.item.PatternModifier.setSetting(item, "applications", 2); player.getInventory().selected = 0; player.setShiftKeyDown(true);
            var context = new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(buffer.getBlockPos().getCenter(), Direction.UP, buffer.getBlockPos(), false));
            org.gtlcore.gtlcore.common.item.PatternModifier.INSTANCE.useOn(context);
            var result = buffer.getTerminalPatternInventory().getStackInSlot(26);
            var decoded = org.gtlcore.gtlcore.api.item.tool.ae2.patternTool.Ae2BaseProcessingPatternHelper.decodeToAEProcessingPattern(result, player);
            helper.assertTrue(decoded.getSparseInputs().getFirst().amount() == 32 && decoded.getSparseOutputs().getFirst().amount() == 4 && result.getCount() == 1 && source.get(DataComponents.CUSTOM_NAME).equals(result.get(DataComponents.CUSTOM_NAME)), "Production modifier interaction lost quantities, components or a physical pattern");
        } finally { player.setShiftKeyDown(false); player.getInventory().setItem(0, previous); packet.release(); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void maximumNativeBoxPagesUseRealVanillaSlotsAndPersistLastSlot(GameTestHelper helper) {
        int before = ConfigHolder.INSTANCE.patternBoxPages;
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        var old = player.getInventory().getItem(0); var packet = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            ConfigHolder.INSTANCE.patternBoxPages = 10;
            player.getInventory().setItem(0, NativeAePatternFixtures.PATTERN_BOX.asStack());
            var data = PlayerInventoryGuiData.of(player, InventoryTypes.PLAYER, null, 0);
            var sync = new ModularSyncManager(false); var manager = new PanelSyncManager(sync, true);
            data.addSyncHandlers(manager);
            var settings = new UISettings(brachy.modularui.api.RecipeViewerSettings.DUMMY);
            var panel = PlayerInventoryUIFactory.INSTANCE.createPanel(data, manager, settings);
            WidgetTree.collectSyncValues(manager, panel);
            var menu = new ModularContainerMenu(51); menu.construct(player, sync, settings, panel.getName(), data);
            helper.assertTrue(menu.slots.size() == 396, "Native pattern box omitted real off-page vanilla slots");
            var held = manager.findSyncHandler("player", 0, ItemSlotSyncHandler.class).getSlot();
            helper.assertTrue(!held.mayPickup(player) && !held.mayPlace(pattern()), "The open box can be moved out of its held slot");
            packet.writeVarInt(9); manager.receiveWidgetUpdate("molecularPageIndex:0", false, 0, packet);
            var last = manager.findSyncHandler("molecularPatternSlot", 359, ItemSlotSyncHandler.class).getSlot();
            helper.assertTrue(last.isActive() && !manager.findSyncHandler("molecularPatternSlot", 0, ItemSlotSyncHandler.class).getSlot().isActive(), "Native box pagination enabled the wrong slots");
            menu.setCarried(pattern()); menu.clicked(menu.slots.indexOf(last), 0, ClickType.PICKUP, player);
            helper.assertTrue(menu.getCarried().isEmpty(), "Last-page vanilla pattern insertion did not complete");
            ConfigHolder.INSTANCE.patternBoxPages = 1;
            var reloaded = ItemStack.parse(helper.getLevel().registryAccess(), data.getUsedItemStack().save(helper.getLevel().registryAccess())).orElseThrow();
            var savedInventory = PatternBoxBehavior.getInventory(reloaded, helper.getLevel().registryAccess());
            helper.assertTrue(savedInventory.getSlots() == 360 && ItemStack.matches(pattern(), savedInventory.getStackInSlot(359)), "Native box save/reload lost last-page pattern components");
        } finally { ConfigHolder.INSTANCE.patternBoxPages = before; player.getInventory().setItem(0, old); packet.release(); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void actualBoxInteractionMovesProductionPatternInventoryWithoutDuplication(GameTestHelper helper) {
        var relative = new BlockPos(2, 1, 2); helper.setBlock(relative, NativeAePatternFixtures.BUFFER.getBlock().defaultBlockState());
        var buffer = (MEPatternBufferPartMachine) helper.getBlockEntity(relative);
        buffer.getTerminalPatternInventory().setItemDirect(26, pattern());
        var player = FakePlayerFactory.getMinecraft(helper.getLevel()); var old = player.getMainHandItem();
        try {
            var box = NativeAePatternFixtures.PATTERN_BOX.asStack(); player.setItemInHand(InteractionHand.MAIN_HAND, box); player.setShiftKeyDown(false);
            var context = new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(buffer.getBlockPos().getCenter(), Direction.UP, buffer.getBlockPos(), false));
            PatternBoxBehavior.handleBlockUse(box, context);
            helper.assertTrue(buffer.getTerminalPatternInventory().getStackInSlot(26).isEmpty() && ItemStack.matches(pattern(), PatternBoxBehavior.getInventory(box, helper.getLevel().registryAccess()).getStackInSlot(0)), "Native box extraction lost or duplicated a production pattern");
            player.setShiftKeyDown(true); PatternBoxBehavior.handleBlockUse(box, context);
            helper.assertTrue(ItemStack.matches(pattern(), buffer.getTerminalPatternInventory().getStackInSlot(0)) && PatternBoxBehavior.getInventory(box, helper.getLevel().registryAccess()).getStackInSlot(0).isEmpty(), "Native box insertion failed to empty the source");
            PatternBoxBehavior.handleBlockUse(box, context);
            int found = 0; for (var stack : buffer.getTerminalPatternInventory()) if (!stack.isEmpty()) found += stack.getCount();
            helper.assertTrue(found == 1, "Repeated native box insertion duplicated a pattern");
        } finally { player.setShiftKeyDown(false); player.setItemInHand(InteractionHand.MAIN_HAND, old); }
        helper.succeed();
    }
}
