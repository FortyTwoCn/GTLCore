package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.machine.multiblock.part.maintenance.*;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.apache.logging.log4j.LogManager;
import java.util.UUID;

/** Four production maintenance menus with real duration mouse events and native gravity C2S/S2C values. */
@EventBusSubscriber(modid = "gtlcore_recipe_test")
public final class NativeMaintenanceClientFixtures {
    private static volatile BlockPos position;
    private static volatile boolean ready, changed;
    private static volatile float serverDuration;
    private static volatile int serverGravity;
    private static UUID owner;
    private static int stage;
    private static void prepare(net.minecraft.server.MinecraftServer server, UUID id, int next) {
        var player = server.getPlayerList().getPlayer(id); if (player == null) return;
        owner = id; stage = next; ready = changed = false; var level = player.serverLevel();
        // Keep every stage inside MachineUIFactory's native eight-block interaction range.
        position = player.blockPosition().offset(3, 0, 0);
        var entry = switch (next) {
            case 0 -> NativeMaintenanceFixtures.AUTO;
            case 1 -> NativeMaintenanceFixtures.CLEAN;
            case 2 -> NativeMaintenanceFixtures.GRAVITY;
            case 3 -> NativeMaintenanceFixtures.GRAVITY_ONLY;
            default -> throw new AssertionError("Unexpected maintenance GUI stage");
        };
        level.setBlockAndUpdate(position, entry.getBlock().defaultBlockState());
        var host = (MetaMachine) level.getBlockEntity(position); host.setFrontFacing(Direction.NORTH);
        ((AutoConfigurationMaintenanceHatchPartMachine) host).setDurationMultiplier(1f);
        if (host instanceof GravityCleaningConfigurationMaintenancePartMachine gravity) gravity.setCurrentGravity(0);
    }
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("gtlcore.maintenanceValidation.enabled") || owner == null || position == null) return;
        var player = event.getServer().getPlayerList().getPlayer(owner); if (player == null) return;
        if (!(player.serverLevel().getBlockEntity(position) instanceof AutoConfigurationMaintenanceHatchPartMachine host)) return;
        ready = true; serverDuration = host.getDurationMultiplier(); serverGravity = host instanceof GravityCleaningConfigurationMaintenancePartMachine g ? g.getCurrentGravity() : -1;
        changed = stage == 3 ? host.getDurationMultiplier() == 1f : Math.abs(host.getDurationMultiplier() - 0.99f) < 0.00001f;
        if (stage >= 2) changed &= ((GravityCleaningConfigurationMaintenancePartMachine) host).getCurrentGravity() == 73;
    }
    @EventBusSubscriber(modid = "gtlcore_recipe_test", value = net.neoforged.api.distmarker.Dist.CLIENT)
    public static final class Client {
        private static boolean loading, prepared, opened, capturing;
        private static int completed, ticks;
        private static long started;
        @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
            if (!Boolean.getBoolean("gtlcore.maintenanceValidation.enabled")) return;
            var client = net.minecraft.client.Minecraft.getInstance();
            if (started == 0) started = System.nanoTime();
            if (System.nanoTime() - started > java.util.concurrent.TimeUnit.MINUTES.toNanos(5)) throw new AssertionError("Maintenance client timed out at stage " + completed);
            if (!loading && client.getOverlay() == null && client.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
                loading = true; client.createWorldOpenFlows().openWorld("Native Recipe Validation", () -> { throw new AssertionError("Maintenance GUI world did not load"); }); return;
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
            if (++ticks == 20 && completed < 3) click(client, ui, "maintenanceDecreaseButton");
            if (ticks == 40 && completed >= 2) ui.screen().getSyncManager().getPanelSyncManager(ui.screen().getMainPanel().getName())
                    .findSyncHandler("maintenanceGravity", 0, brachy.modularui.value.sync.IntSyncValue.class).setIntValue(73, true, true);
            if (ticks == 60 || ticks == 100) LogManager.getLogger().info("GTL_MAINTENANCE_DIAGNOSTIC stage={} duration={} gravity={} clientGravity={} menu={} ready={} changed={}", completed, serverDuration, serverGravity,
                    client.level.getBlockEntity(position) instanceof GravityCleaningConfigurationMaintenancePartMachine gravity ? gravity.getCurrentGravity() : -1,
                    ui.screen().getMainPanel().getName(), ready, changed);
            if (ticks == 160 && !changed) throw new AssertionError("Maintenance C2S failed at stage " + completed + " duration=" + serverDuration + " gravity=" + serverGravity);
            if (ticks < 100 || !changed) return;
            var manager = ui.screen().getSyncManager().getPanelSyncManager(ui.screen().getMainPanel().getName());
            if (completed < 3 && Math.abs(manager.findSyncHandler("maintenanceDuration", 0, brachy.modularui.value.sync.FloatSyncValue.class).getFloatValue() - 0.99f) > 0.00001f)
                throw new AssertionError("Maintenance duration lost native server-to-client synchronization");
            if (completed >= 2 && ((GravityCleaningConfigurationMaintenancePartMachine) client.level.getBlockEntity(position)).getCurrentGravity() != 73)
                throw new AssertionError("Native gravity SaveField synchronization did not reach the client entity");
            for (var panel : ui.screen().getPanelManager().getOpenPanels()) {
                var area = panel.getArea(); if (area.x < 0 || area.y < 0 || area.x + area.width > client.screen.width || area.y + area.height > client.screen.height) throw new AssertionError("Maintenance panel exceeds the scaled screen");
            }
            capturing = true; var name = java.util.List.of("auto-maintenance", "clean-maintenance", "gravity-maintenance", "gravity-only").get(completed);
            net.minecraft.client.Screenshot.grab(client.gameDirectory, "native-menu-" + name + ".png", client.getMainRenderTarget(), message -> client.execute(() -> {
                LogManager.getLogger().info("GTL_NATIVE_MAINTENANCE_MENU=PASS menu={} duration_c2s=true gravity_c2s_s2c=true screenshot={}", name, message.getString());
                if (++completed == 4) { LogManager.getLogger().info("GTL_NATIVE_MAINTENANCE_CLIENT=PASS menus=4 duration_click_c2s=true gravity_c2s_s2c=true"); client.stop(); return; }
                client.player.closeContainer(); prepared = opened = capturing = false; ticks = 0; ready = false;
            }));
        }
        private static void click(net.minecraft.client.Minecraft client, brachy.modularui.api.IMuiScreen ui, String name) {
            var widget = brachy.modularui.widget.WidgetTree.findFirstWithName(ui.screen().getMainPanel(), name); var area = widget.getArea();
            double x = area.x + area.width / 2.0, y = area.y + area.height / 2.0; var context = ui.screen().getContext(); context.updateState((int) x, (int) y, 0); ui.screen().onFrameUpdate();
            if (context.getTopHovered() != widget) throw new AssertionError("Native maintenance click failed to hover " + name);
            client.screen.afterMouseAction(); boolean handled = net.neoforged.neoforge.client.ClientHooks.onScreenMouseClickedPre(client.screen, x, y, 0);
            if (!handled) handled = net.neoforged.neoforge.client.ClientHooks.onScreenMouseClickedPost(client.screen, x, y, 0, client.screen.mouseClicked(x, y, 0));
            if (!handled) throw new AssertionError("Native maintenance control rejected actual screen input");
            if (!net.neoforged.neoforge.client.ClientHooks.onScreenMouseReleasedPre(client.screen, x, y, 0)) net.neoforged.neoforge.client.ClientHooks.onScreenMouseReleasedPost(client.screen, x, y, 0, client.screen.mouseReleased(x, y, 0));
        }
    }
}
