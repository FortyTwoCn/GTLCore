package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.integration.ftb.NativeProspectorCoordinates;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.mui.widgets.prospector.ProspectorMapHandler;
import brachy.modularui.api.IMuiScreen;
import brachy.modularui.widget.WidgetTree;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.apache.logging.log4j.LogManager;
import java.util.UUID;

/** Actual GT handheld menu and FTB client waypoints/registered teleport packet on an integrated server. */
@EventBusSubscriber(modid = "gtlcore_recipe_test", value = net.neoforged.api.distmarker.Dist.CLIENT)
public final class NativeProspectorClientFixtures {
    private static boolean loading, prepared, opened, capturing;
    private static volatile boolean ready;
    private static volatile BlockPos serverPosition;
    private static UUID owner;
    private static int phase, ticks;
    private static long started;
    private static BlockPos target, afterMoving;

    private static void prepare(net.minecraft.server.MinecraftServer server, UUID id) {
        owner = id; var player = server.getPlayerList().getPlayer(id); var level = player.serverLevel();
        for (var center : new BlockPos[]{new BlockPos(-18, 64, -35), new BlockPos(14, 64, -35), new BlockPos(-51, 64, -57)})
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) level.setBlockAndUpdate(center.offset(x, 0, z), Blocks.STONE.defaultBlockState());
        player.teleportTo(-17.5, 65, -34.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, GTItems.PROSPECTOR_HV.asStack());
        player.inventoryMenu.broadcastChanges(); ready = true;
    }

    @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("gtlcore.prospectorValidation.enabled")) return;
        var client = Minecraft.getInstance(); if (started == 0) started = System.nanoTime();
        if (System.nanoTime() - started > java.util.concurrent.TimeUnit.MINUTES.toNanos(5)) throw new AssertionError("Native prospector client timed out at " + phase + " position=" + serverPosition);
        if (!loading && client.getOverlay() == null && client.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
            loading = true; client.createWorldOpenFlows().openWorld("Native Recipe Validation", () -> { throw new AssertionError("Native prospector world did not load"); }); return;
        }
        if (client.level == null || client.player == null || client.getSingleplayerServer() == null || capturing) return;
        var server = client.getSingleplayerServer();
        if (!prepared) { prepared = true; var id = client.player.getUUID(); server.execute(() -> prepare(server, id)); return; }
        if (owner != null) server.execute(() -> serverPosition = server.getPlayerList().getPlayer(owner).blockPosition());
        if (!opened && ready && client.player.getMainHandItem().is(GTItems.PROSPECTOR_HV.asItem()) && client.player.getBlockX() < 0) {
            opened = true; server.execute(() -> brachy.modularui.factory.PlayerInventoryUIFactory.INSTANCE.openFromHand(server.getPlayerList().getPlayer(owner), InteractionHand.MAIN_HAND)); return;
        }
        if (!(client.screen instanceof IMuiScreen ui) || !ui.screen().getMainPanel().isValid() || ui.screen().getContext().getGraphics() == null || ++ticks < 20) return;
        var panel = ui.screen().getMainPanel();
        if (!panel.getName().equals("prospector_scanner")) return;
        var map = findMap(panel);
        if (map == null) throw new AssertionError("Original native prospector map is absent");
        var area = map.getArea();
        var manager = FTBChunksAPI.clientApi().getWaypointManager(client.level.dimension()).orElseThrow();
        if (phase == 0) {
            manager.getAllWaypoints().stream().filter(waypoint -> NativeProspectorCoordinates.isGeneratedWaypoint(waypoint.getName())).toList().forEach(manager::removeWaypoint);
            manager.addWaypointAt(new BlockPos(-60, 0, -70), "prospect_pointcustom");
            manager.addWaypointAt(new BlockPos(-61, 0, -71), "native manual waypoint");
            target = NativeProspectorCoordinates.point(map.getPlayerChunkPos(), map.getChunkRadius(), 13, 23).orElseThrow();
            click(client, ui, area.x + 13, area.y + 23, 1); phase = 1; ticks = 0;
        } else if (phase == 1) {
            if (manager.getAllWaypoints().stream().noneMatch(waypoint -> waypoint.getName().equals("prospect_point1") && waypoint.getPos().equals(target))) throw new AssertionError("Native right-click waypoint lost negative map coordinates");
            bounded(client, panel);
            capture(client, "native-prospector-waypoint.png", () -> { server.execute(() -> server.getPlayerList().getPlayer(owner).teleportTo(14.5, 65, -34.5)); phase = 2; ticks = 0; });
        } else if (phase == 2 && client.player.getBlockX() == 14 && serverPosition != null && serverPosition.getX() == 14) {
            afterMoving = NativeProspectorCoordinates.point(map.getPlayerChunkPos(), map.getChunkRadius(), 25, 28).orElseThrow();
            click(client, ui, area.x + 25, area.y + 28, 1); phase = 3; ticks = 0;
        } else if (phase == 3) {
            if (manager.getAllWaypoints().stream().noneMatch(waypoint -> waypoint.getName().equals("prospect_point2") && waypoint.getPos().equals(afterMoving))) throw new AssertionError("Moving the player changed the native map anchor");
            capture(client, "native-prospector-moved.png", () -> {
                var clear = WidgetTree.findFirstWithName(panel, "gtlcore_clear_prospecting_waypoints").getArea();
                click(client, ui, clear.x + 9, clear.y + 9, 0); phase = 4; ticks = 0;
            });
        } else if (phase == 4) {
            var names = manager.getAllWaypoints().stream().map(waypoint -> waypoint.getName()).toList();
            if (names.stream().anyMatch(NativeProspectorCoordinates::isGeneratedWaypoint) || !names.contains("prospect_pointcustom") || !names.contains("native manual waypoint")) throw new AssertionError("Native clear button removed manual waypoints or retained generated points");
            capture(client, "native-prospector-cleared.png", () -> { click(client, ui, area.x + 13, area.y + 23, 0); phase = 5; ticks = 0; });
        } else if (phase == 5 && serverPosition != null && serverPosition.getX() == target.getX() && serverPosition.getZ() == target.getZ() && client.player.getBlockX() == target.getX() && client.player.getBlockZ() == target.getZ()) {
            capture(client, "native-prospector-teleported.png", () -> {
                LogManager.getLogger().info("GTL_NATIVE_PROSPECTOR_CLIENT=PASS original_item_menu=true native_map=true negative_coordinates=true fixed_opening_anchor=true right_waypoints=true clear_preserves_manual=true teleport_c2s_s2c=true bounded=true");
                client.player.closeContainer(); client.stop();
            });
        }
    }
    private static void bounded(Minecraft client, brachy.modularui.screen.ModularPanel<?> panel) {
        var area = panel.getArea(); var clear = WidgetTree.findFirstWithName(panel, "gtlcore_clear_prospecting_waypoints").getArea();
        if (area.x < 0 || area.y < 0 || area.x + area.width > client.screen.width || area.y + area.height > client.screen.height || clear.x < 0 || clear.y < 0 || clear.x + clear.width > client.screen.width || clear.y + clear.height > client.screen.height) throw new AssertionError("Native prospector panel or clear button exceeds the scaled screen");
    }
    private static ProspectorMapHandler<?> findMap(brachy.modularui.api.widget.IWidget widget) {
        if (widget instanceof ProspectorMapHandler<?> map) return map;
        for (var child : widget.getChildren()) { var found = findMap(child); if (found != null) return found; }
        return null;
    }
    private static void click(Minecraft client, IMuiScreen ui, double x, double y, int button) {
        try { var move = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onMove", long.class, double.class, double.class); move.setAccessible(true); move.invoke(client.mouseHandler, client.getWindow().getWindow(), x * client.getWindow().getScreenWidth() / client.screen.width, y * client.getWindow().getScreenHeight() / client.screen.height); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        ui.screen().getContext().updateState((int) x, (int) y, 0); ui.screen().onFrameUpdate(); client.screen.afterMouseAction();
        boolean handled = net.neoforged.neoforge.client.ClientHooks.onScreenMouseClickedPre(client.screen, x, y, button);
        if (!handled) handled = net.neoforged.neoforge.client.ClientHooks.onScreenMouseClickedPost(client.screen, x, y, button, client.screen.mouseClicked(x, y, button));
        if (!handled) throw new AssertionError("Native prospector click was rejected at " + phase);
        if (!net.neoforged.neoforge.client.ClientHooks.onScreenMouseReleasedPre(client.screen, x, y, button)) net.neoforged.neoforge.client.ClientHooks.onScreenMouseReleasedPost(client.screen, x, y, button, client.screen.mouseReleased(x, y, button));
    }
    private static void capture(Minecraft client, String name, Runnable next) { capturing = true; net.minecraft.client.Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), text -> client.execute(() -> { capturing = false; next.run(); })); }
}
