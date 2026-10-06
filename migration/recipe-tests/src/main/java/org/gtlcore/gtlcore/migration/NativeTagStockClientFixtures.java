package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.machine.multiblock.part.*;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import org.apache.logging.log4j.LogManager;
import java.util.UUID;

/** Actual integrated-server menus, stock S2C and native sidebar mouse events for both filtered parts. */
@EventBusSubscriber(modid = "gtlcore_recipe_test")
public final class NativeTagStockClientFixtures {
    private static volatile BlockPos position;
    private static volatile boolean ready, sorted;
    private static UUID owner;
    private static int stage;
    private static void prepare(net.minecraft.server.MinecraftServer server, UUID id, int next) {
        var player = server.getPlayerList().getPlayer(id); if (player == null) return;
        owner = id; stage = next; ready = sorted = false; var level = player.serverLevel(); position = player.blockPosition().offset(3 + 4 * next, 0, 0);
        level.setBlockAndUpdate(position, (next == 0 ? NativeAeGridFixtures.TAG_STOCK_ITEM : NativeAeGridFixtures.TAG_STOCK_FLUID).getBlock().defaultBlockState());
        var host = (MetaMachine) level.getBlockEntity(position); host.setFrontFacing(Direction.NORTH);
        if (next == 0) { var machine = (TagFilterMEStockBusPartMachine) host; machine.setTagWhite("c:ingots/*"); machine.setMEUpdateInterval(1); }
        else { var machine = (TagFilterMEStockHatchPartMachine) host; machine.setTagWhite("minecraft:water"); machine.setMEUpdateInterval(1); }
        level.setBlockAndUpdate(position.north(), appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(position.north(2), appeng.core.definitions.AEBlocks.DRIVE.block().defaultBlockState());
        var drive = (appeng.blockentity.storage.DriveBlockEntity) level.getBlockEntity(position.north(2));
        drive.getInternalInventory().setItemDirect(0, (next == 0 ? appeng.core.definitions.AEItems.ITEM_CELL_1K : appeng.core.definitions.AEItems.FLUID_CELL_1K).stack());
    }
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("gtlcore.tagStockValidation.enabled") || owner == null || position == null) return;
        var player = event.getServer().getPlayerList().getPlayer(owner); if (player == null) return;
        var host = player.serverLevel().getBlockEntity(position);
        if (stage == 0 && host instanceof TagFilterMEStockBusPartMachine machine && machine.isOnline()) {
            if (!ready) { var storage = machine.getMainNode().getGrid().getStorageService().getInventory(); storage.insert(AEItemKey.of(net.minecraft.world.item.Items.IRON_INGOT), 37, Actionable.MODULATE, IActionSource.ofPlayer(player)); machine.autoIO(); ready = true; }
            sorted = machine.isCountSort();
        } else if (stage == 1 && host instanceof TagFilterMEStockHatchPartMachine machine && machine.isOnline()) {
            if (!ready) { var storage = machine.getMainNode().getGrid().getStorageService().getInventory(); storage.insert(AEFluidKey.of(net.minecraft.world.level.material.Fluids.WATER), 5500, Actionable.MODULATE, IActionSource.ofPlayer(player)); machine.autoIO(); ready = true; }
            sorted = machine.isCountSort();
        }
    }
    @EventBusSubscriber(modid = "gtlcore_recipe_test", value = net.neoforged.api.distmarker.Dist.CLIENT)
    public static final class Client {
        private static boolean loading, prepared, opened, capturing;
        private static int completed, ticks;
        private static long started;
        @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
            if (!Boolean.getBoolean("gtlcore.tagStockValidation.enabled")) return;
            var client = net.minecraft.client.Minecraft.getInstance();
            if (started == 0) started = System.nanoTime();
            if (System.nanoTime() - started > java.util.concurrent.TimeUnit.MINUTES.toNanos(5)) throw new AssertionError("Tag stock client timed out at stage " + completed);
            if (!loading && client.getOverlay() == null && client.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
                loading = true; client.createWorldOpenFlows().openWorld("Native Recipe Validation", () -> { throw new AssertionError("Tag stock GUI world did not load"); }); return;
            }
            if (client.level == null || client.player == null || client.getSingleplayerServer() == null || capturing) return;
            var server = client.getSingleplayerServer();
            if (!prepared) { prepared = true; var id = client.player.getUUID(); int next = completed; server.execute(() -> prepare(server, id, next)); return; }
            if (!opened && ready && position != null && client.level.getBlockEntity(position) instanceof IMuiMachine) {
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(owner); var host = (IMuiMachine) player.serverLevel().getBlockEntity(position);
                    host.tryToOpenUI(player, InteractionHand.MAIN_HAND, new BlockHitResult(position.getCenter(), Direction.NORTH, position, false));
                }); opened = true; return;
            }
            if (!(client.screen instanceof brachy.modularui.api.IMuiScreen ui) || !ui.screen().getMainPanel().isValid() || ui.screen().getContext().getGraphics() == null) return;
            if (++ticks == 20) click(client, ui, "tagCountSortButton");
            if (ticks == 40) click(client, ui, "tagFilterButton");
            if (ticks < 100 || !sorted) return;
            var popup = ui.screen().getSyncManager().getPanelSyncManager("gtlcoreTagFilterPanel");
            var whitelist = popup.findSyncHandler("tagWhitelist", 0, brachy.modularui.value.sync.StringSyncValue.class).getStringValue();
            if (!whitelist.equals(completed == 0 ? "c:ingots/*" : "minecraft:water")) throw new AssertionError("Tag filter text lost native S2C settings");
            for (var panel : ui.screen().getPanelManager().getOpenPanels()) {
                var area = panel.getArea(); if (area.x < 0 || area.y < 0 || area.x + area.width > client.screen.width || area.y + area.height > client.screen.height) throw new AssertionError("Tag stock panel exceeds the scaled screen");
            }
            capturing = true; var name = completed == 0 ? "tag-stock-item" : "tag-stock-fluid";
            net.minecraft.client.Screenshot.grab(client.gameDirectory, "native-menu-" + name + ".png", client.getMainRenderTarget(), message -> client.execute(() -> {
                LogManager.getLogger().info("GTL_NATIVE_TAG_STOCK_MENU=PASS menu={} count_sort_c2s=true tag_popup_s2c=true screenshot={}", name, message.getString());
                if (++completed == 2) { LogManager.getLogger().info("GTL_NATIVE_TAG_STOCK_CLIENT=PASS menus=2 count_sort_c2s=true tag_popup_s2c=true"); client.stop(); return; }
                client.player.closeContainer(); prepared = opened = capturing = false; ticks = 0; ready = false;
            }));
        }
        private static void click(net.minecraft.client.Minecraft client, brachy.modularui.api.IMuiScreen ui, String name) {
            var widget = brachy.modularui.widget.WidgetTree.findFirstWithName(ui.screen().getMainPanel(), name); var area = widget.getArea();
            double x = area.x + area.width / 2.0, y = area.y + area.height / 2.0; var context = ui.screen().getContext(); context.updateState((int) x, (int) y, 0); ui.screen().onFrameUpdate();
            if (context.getTopHovered() != widget) throw new AssertionError("Native tag click failed to hover " + name);
            client.screen.afterMouseAction(); boolean handled = net.neoforged.neoforge.client.ClientHooks.onScreenMouseClickedPre(client.screen, x, y, 0);
            if (!handled) handled = net.neoforged.neoforge.client.ClientHooks.onScreenMouseClickedPost(client.screen, x, y, 0, client.screen.mouseClicked(x, y, 0));
            if (!handled) throw new AssertionError("Native tag sidebar rejected actual screen input");
            if (!net.neoforged.neoforge.client.ClientHooks.onScreenMouseReleasedPre(client.screen, x, y, 0)) net.neoforged.neoforge.client.ClientHooks.onScreenMouseReleasedPost(client.screen, x, y, 0, client.screen.mouseReleased(x, y, 0));
        }
    }
}
