package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.gui.MachineModeConfigurator;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.apache.logging.log4j.LogManager;
import java.util.UUID;

/** Real native machine menus, screen mouse input, C2S selection and S2C display on an integrated server. */
@EventBusSubscriber(modid = "gtlcore_recipe_test")
public final class NativeRecipeModeClientFixtures {
    private static volatile BlockPos position;
    private static volatile boolean ready, restored;
    private static volatile int serverMode;
    private static UUID owner;

    private static void prepare(net.minecraft.server.MinecraftServer server, UUID id) {
        var player = server.getPlayerList().getPlayer(id); owner = id;
        position = player.blockPosition().offset(3, 0, 0);
        player.serverLevel().setBlockAndUpdate(position, NativeTerminalFixtures.RECIPE_MODES.getBlock().defaultBlockState());
        var machine = (WorkableElectricMultiblockMachine) player.serverLevel().getBlockEntity(position);
        machine.setFrontFacing(Direction.NORTH); machine.checkAndFormStructure(); machine.setWorkingEnabled(false); machine.setActiveRecipeType(0);
        ready = true;
    }

    private static void open(net.minecraft.server.MinecraftServer server) {
        var player = server.getPlayerList().getPlayer(owner);
        ((IMuiMachine) player.serverLevel().getBlockEntity(position)).tryToOpenUI(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(position.getCenter(), Direction.NORTH, position, false));
    }

    private static void restore(net.minecraft.server.MinecraftServer server) {
        var player = server.getPlayerList().getPlayer(owner); player.closeContainer(); var level = player.serverLevel();
        var machine = (WorkableElectricMultiblockMachine) level.getBlockEntity(position);
        var tag = machine.saveWithoutMetadata(level.registryAccess()); machine.onUnload(); level.removeBlockEntity(position);
        var replacement = (WorkableElectricMultiblockMachine) NativeTerminalFixtures.RECIPE_MODES.getBlockEntityType().create(position, level.getBlockState(position));
        replacement.loadAdditional(tag, level.registryAccess()); level.setBlockEntity(replacement); replacement.onLoad();
        if (replacement.getActiveRecipeType() != 6) throw new AssertionError("Native machine mode failed saved entity recreation");
        restored = true;
    }

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("gtlcore.modeValidation.enabled") || owner == null || position == null) return;
        var player = event.getServer().getPlayerList().getPlayer(owner);
        if (player != null && player.serverLevel().getBlockEntity(position) instanceof WorkableElectricMultiblockMachine machine)
            serverMode = machine.getActiveRecipeType();
    }

    @EventBusSubscriber(modid = "gtlcore_recipe_test", value = net.neoforged.api.distmarker.Dist.CLIENT)
    public static final class Client {
        private static boolean loading, prepared, opened, capturing;
        private static int phase, ticks, reopenTicks;
        private static long started;
        @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
            if (!Boolean.getBoolean("gtlcore.modeValidation.enabled")) return;
            var client = net.minecraft.client.Minecraft.getInstance();
            if (started == 0) started = System.nanoTime();
            if (System.nanoTime() - started > java.util.concurrent.TimeUnit.MINUTES.toNanos(5)) throw new AssertionError("Mode client timed out at phase " + phase + " serverMode=" + serverMode);
            if (!loading && client.getOverlay() == null && client.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
                loading = true; client.createWorldOpenFlows().openWorld("Native Recipe Validation", () -> { throw new AssertionError("Mode validation world did not load"); }); return;
            }
            if (client.level == null || client.player == null || client.getSingleplayerServer() == null || capturing) return;
            var server = client.getSingleplayerServer();
            if (!prepared) { prepared = true; var id = client.player.getUUID(); server.execute(() -> prepare(server, id)); return; }
            // A server close also produces a vanilla C2S close. Let the old screen finish before opening a new menu.
            boolean canReopen = phase != 6 || restored && client.screen == null && ++reopenTicks >= 20;
            if (!opened && ready && canReopen && client.level.getBlockEntity(position) instanceof IMuiMachine) {
                opened = true; server.execute(() -> open(server)); return;
            }
            if (!(client.screen instanceof brachy.modularui.api.IMuiScreen ui) || !ui.screen().getMainPanel().isValid()
                    || ui.screen().getContext().getGraphics() == null || ++ticks < 20) return;
            var popup = ui.screen().getPanelManager().getOpenPanels().stream()
                    .filter(panel -> panel.getName().equals(MachineModeConfigurator.PANEL)).findFirst();
            if (phase == 0 || phase == 6) {
                click(client, ui, ui.screen().getMainPanel(), "gtlcoreRecipeModeButton"); phase = phase == 0 ? 1 : 7; ticks = 0; return;
            }
            if (popup.isEmpty()) return;
            var panel = popup.get(); var manager = ui.screen().getSyncManager().getPanelSyncManager(panel.getName());
            var current = manager.findSyncHandler(MachineModeConfigurator.CURRENT, 0, brachy.modularui.value.sync.IntSyncValue.class);
            if (phase == 1) { click(client, ui, panel, "gtlcoreRecipeMode2"); phase = 2; ticks = 0; }
            else if (phase == 2 && serverMode == 2 && current.getIntValue() == 2) {
                checkSelected(panel, 2); capture(client, "native-menu-recipe-modes.png", () -> { phase = 3; ticks = 0; });
            } else if (phase == 3) {
                var row = brachy.modularui.widget.WidgetTree.findFirstWithName(panel, "gtlcoreRecipeMode2"); var area = row.getArea();
                double x = area.x + area.width / 2.0, y = area.y + area.height / 2.0;
                ui.screen().getContext().updateState((int) x, (int) y, 0); ui.screen().onFrameUpdate();
                scroll(client, x, y);
                phase = 4; ticks = 0;
            } else if (phase == 4) { click(client, ui, panel, "gtlcoreRecipeMode6"); phase = 5; ticks = 0; }
            else if (phase == 5 && serverMode == 6 && current.getIntValue() == 6) {
                checkSelected(panel, 6); capture(client, "native-menu-recipe-modes-scrolled.png", () -> {
                    phase = 6; ticks = 0; opened = false; server.execute(() -> restore(server));
                });
            } else if (phase == 7 && restored && current.getIntValue() == 6) {
                var row = brachy.modularui.widget.WidgetTree.findFirstWithName(panel, "gtlcoreRecipeMode2"); var area = row.getArea();
                double x = area.x + area.width / 2.0, y = area.y + area.height / 2.0;
                ui.screen().getContext().updateState((int) x, (int) y, 0); ui.screen().onFrameUpdate();
                scroll(client, x, y);
                phase = 8; ticks = 0;
            } else if (phase == 8 && restored && current.getIntValue() == 6) {
                checkSelected(panel, 6); capture(client, "native-menu-recipe-modes-restored.png", () -> {
                    LogManager.getLogger().info("GTL_NATIVE_RECIPE_MODE_CLIENT=PASS types=7 popup=true scroll=true selection_c2s=true highlight_s2c=true saved_recreation=true");
                    client.setScreen(null); client.stop();
                });
            }
        }
        private static void checkSelected(brachy.modularui.screen.ModularPanel<?> panel, int index) {
            var button = (brachy.modularui.widgets.ToggleButton) brachy.modularui.widget.WidgetTree.findFirstWithName(panel, "gtlcoreRecipeMode" + index);
            if (!button.isValueSelected()) throw new AssertionError("Native mode selected-row highlight ignored S2C value");
        }
        private static void capture(net.minecraft.client.Minecraft client, String name, Runnable next) {
            capturing = true;
            net.minecraft.client.Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), message -> client.execute(() -> { capturing = false; next.run(); }));
        }
        private static void click(net.minecraft.client.Minecraft client, brachy.modularui.api.IMuiScreen ui,
                                  brachy.modularui.screen.ModularPanel<?> panel, String name) {
            var widget = brachy.modularui.widget.WidgetTree.findFirstWithName(panel, name); var area = widget.getArea();
            double x = area.x + area.width / 2.0, y = area.y + area.height / 2.0;
            // Native viewport scrolling transforms children at draw/pick time; their layout Areas stay unchanged.
            if (widget.getParent() instanceof MachineModeConfigurator list) { x -= list.getScrollX(); y -= list.getScrollY(); }
            ui.screen().getContext().updateState((int) x, (int) y, 0); ui.screen().onFrameUpdate();
            if (ui.screen().getContext().getTopHovered() != widget) throw new AssertionError("Native mode click could not hover " + name);
            client.screen.afterMouseAction();
            boolean handled = net.neoforged.neoforge.client.ClientHooks.onScreenMouseClickedPre(client.screen, x, y, 0);
            if (!handled) handled = net.neoforged.neoforge.client.ClientHooks.onScreenMouseClickedPost(client.screen, x, y, 0, client.screen.mouseClicked(x, y, 0));
            if (!handled) throw new AssertionError("Native mode screen rejected " + name);
            if (!net.neoforged.neoforge.client.ClientHooks.onScreenMouseReleasedPre(client.screen, x, y, 0))
                net.neoforged.neoforge.client.ClientHooks.onScreenMouseReleasedPost(client.screen, x, y, 0, client.screen.mouseReleased(x, y, 0));
        }
        private static void scroll(net.minecraft.client.Minecraft client, double x, double y) {
            client.screen.afterMouseAction();
            boolean handled = net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                    new net.neoforged.neoforge.client.event.ScreenEvent.MouseScrolled.Pre(client.screen, x, y, 0, -3)).isCanceled();
            if (!handled) {
                handled = client.screen.mouseScrolled(x, y, 0, -3);
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                        new net.neoforged.neoforge.client.event.ScreenEvent.MouseScrolled.Post(client.screen, x, y, 0, -3));
            }
            if (!handled) throw new AssertionError("Native mode list rejected screen wheel input");
        }
    }
}
