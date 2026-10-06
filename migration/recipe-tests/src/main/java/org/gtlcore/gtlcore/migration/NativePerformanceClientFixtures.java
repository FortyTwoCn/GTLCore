package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.machine.PerformanceMonitorMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import brachy.modularui.api.IMuiScreen;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.value.sync.GenericListSyncHandler;
import brachy.modularui.widget.WidgetTree;
import brachy.modularui.widgets.ListWidget;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.apache.logging.log4j.LogManager;
import java.util.*;

/** Deterministic server timings travel through the real menu S2C path; native tick sampling has separate world tests. */
@EventBusSubscriber(modid = "gtlcore_recipe_test")
public final class NativePerformanceClientFixtures {
    private static volatile BlockPos position;
    private static volatile boolean ready;
    private static volatile int epoch;
    private static UUID owner;
    private static final List<NativePerformanceFixtures.TickProbe> probes = new ArrayList<>();
    private static int timing(int index) { return (epoch + 1) * 100000 + 12 - Math.max(1, index); }
    private static void prepare(net.minecraft.server.MinecraftServer server, UUID id) {
        var player = server.getPlayerList().getPlayer(id); owner = id; position = player.blockPosition().offset(4, 0, 0);
        var level = player.serverLevel(); level.setBlockAndUpdate(position, NativePerformanceFixtures.MONITOR.getBlock().defaultBlockState());
        probes.clear();
        for (int i = 0; i < 12; i++) {
            var pos = position.offset(i % 4, 0, 2 + i / 4); level.setBlockAndUpdate(pos, NativePerformanceFixtures.PROBE.getBlock().defaultBlockState());
            var machine = (NativePerformanceFixtures.TickProbe) level.getBlockEntity(pos); machine.cancelled = true; probes.add(machine);
        }
        publish(); ready = true;
    }
    private static void publish() {
        synchronized (PerformanceMonitorMachine.PERFORMANCE_MAP) {
            PerformanceMonitorMachine.PERFORMANCE_MAP.clear();
            for (int i = 0; i < probes.size(); i++) PerformanceMonitorMachine.PERFORMANCE_MAP.put(probes.get(i), timing(i));
        }
    }
    private static void open(net.minecraft.server.MinecraftServer server) {
        var player = server.getPlayerList().getPlayer(owner);
        ((IMuiMachine) player.serverLevel().getBlockEntity(position)).tryToOpenUI(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(position.getCenter(), Direction.NORTH, position, false));
    }
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) {
        if (Boolean.getBoolean("gtlcore.performanceValidation.enabled") && ready) publish();
    }

    @EventBusSubscriber(modid = "gtlcore_recipe_test", value = net.neoforged.api.distmarker.Dist.CLIENT)
    public static final class Client {
        private static boolean loading, prepared, opened, capturing;
        private static int phase, ticks;
        private static long started;
        private static ListWidget<?, ?> originalList;
        @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
            if (!Boolean.getBoolean("gtlcore.performanceValidation.enabled")) return;
            var client = net.minecraft.client.Minecraft.getInstance();
            if (started == 0) started = System.nanoTime();
            if (System.nanoTime() - started > java.util.concurrent.TimeUnit.MINUTES.toNanos(5)) throw new AssertionError("Performance client timed out at phase " + phase);
            if (!loading && client.getOverlay() == null && client.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
                loading = true; client.createWorldOpenFlows().openWorld("Native Recipe Validation", () -> { throw new AssertionError("Performance world did not load"); }); return;
            }
            if (client.level == null || client.player == null || client.getSingleplayerServer() == null || capturing) return;
            var server = client.getSingleplayerServer();
            if (!prepared) { prepared = true; var id = client.player.getUUID(); server.execute(() -> prepare(server, id)); return; }
            if (!opened && ready && client.level.getBlockEntity(position) instanceof PerformanceMonitorMachine) {
                opened = true; server.execute(() -> open(server)); return;
            }
            if (!(client.screen instanceof IMuiScreen ui) || !ui.screen().getMainPanel().isValid()
                    || ui.screen().getContext().getGraphics() == null || ++ticks < 20) return;
            var panel = ui.screen().getMainPanel(); var manager = ui.screen().getSyncManager().getPanelSyncManager(panel.getName());
            var handler = manager.findSyncHandler("performanceRows", 0, GenericListSyncHandler.class);
            @SuppressWarnings("unchecked") var rows = (List<PerformanceMonitorMachine.Row>) handler.getValue();
            var list = (ListWidget<?, ?>) WidgetTree.findFirstWithName(panel, "performanceRowsList");
            if (list == null || rows.size() < 12) return;
            var area = panel.getArea();
            if (client.options.guiScale().get() != 2 || area.x < 0 || area.y < 0
                    || area.x + area.width > client.getWindow().getGuiScaledWidth() || area.y + area.height > client.getWindow().getGuiScaledHeight())
                throw new AssertionError("Native performance monitor exceeded the GUI scale 2 screen");
            if (phase == 0 && rows.getFirst().micros() == 100011) {
                checkRows(rows, 0); originalList = list;
                click(client, ui, panel, list, 0); checkHighlight(rows.getFirst());
                capture(client, "native-performance-monitor.png", () -> { phase = 1; ticks = 0; });
            } else if (phase == 1) {
                var viewport = list.getArea(); double x = viewport.x + viewport.width / 2.0, y = viewport.y + viewport.height / 2.0;
                ui.screen().getContext().updateState((int) x, (int) y, 0); ui.screen().onFrameUpdate();
                client.screen.afterMouseAction();
                // Native scroll views use one fixed step per event, regardless of the wheel delta's magnitude.
                for (int step = 0; step < 4; step++) {
                    boolean handled = net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                            new net.neoforged.neoforge.client.event.ScreenEvent.MouseScrolled.Pre(client.screen, x, y, 0, -1)).isCanceled();
                    if (!handled) { handled = client.screen.mouseScrolled(x, y, 0, -1);
                        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.ScreenEvent.MouseScrolled.Post(client.screen, x, y, 0, -1)); }
                    if (!handled) throw new AssertionError("Native performance list rejected actual wheel input");
                }
                phase = 2; ticks = 0;
            } else if (phase == 2) {
                if (list.getScrollY() <= 0) throw new AssertionError("Native performance wheel input did not move the viewport");
                click(client, ui, panel, list, 11); checkHighlight(rows.get(11));
                capture(client, "native-performance-monitor-scrolled.png", () -> {
                    phase = 3; ticks = 0; server.execute(() -> { epoch = 1; publish(); });
                });
            } else if (phase == 3 && rows.getFirst().micros() == 200011) {
                checkRows(rows, 1);
                if (list == originalList || originalList.isValid()) throw new AssertionError("Native dynamic list did not dispose and replace stale timing rows");
                capture(client, "native-performance-monitor-updated.png", () -> {
                    LogManager.getLogger().info("GTL_NATIVE_PERFORMANCE_CLIENT=PASS rows=12 equal_timings=true native_s2c=true refresh=true dispose=true scroll=true highlight_dimension=true highlight_15s=true bounded=true");
                    client.setScreen(null); client.stop();
                });
            }
        }
        private static void checkRows(List<PerformanceMonitorMachine.Row> rows, int expectedEpoch) {
            var expected = new HashSet<BlockPos>(); for (var probe : probes) expected.add(probe.getBlockPos());
            var actual = new HashSet<BlockPos>();
            for (int i = 0; i < 12; i++) {
                var row = rows.get(i); actual.add(row.position());
                if (!row.dimension().equals(net.minecraft.client.Minecraft.getInstance().level.dimension().location())
                        || row.micros() != (expectedEpoch + 1) * 100000 + 12 - Math.max(1, i))
                    throw new AssertionError("Native timing S2C rows changed dimensions, timing or ordering");
            }
            if (!actual.equals(expected)) throw new AssertionError("Native timing list omitted or duplicated an equal-time machine");
        }
        private static void checkHighlight(PerformanceMonitorMachine.Row row) {
            long now = System.currentTimeMillis();
            if (com.glodblock.github.glodium.client.render.highlight.HighlightHandler.getBlockData().stream().noneMatch(value ->
                    value.pos().equals(row.position()) && value.dim().location().equals(row.dimension())
                            && value.time() >= now + 14000 && value.time() <= now + 16000))
                throw new AssertionError("Actual performance button did not register the correct dimension/position for 15-second highlighting");
        }
        private static void click(net.minecraft.client.Minecraft client, IMuiScreen ui, ModularPanel<?> panel, ListWidget<?, ?> list, int index) {
            var widget = WidgetTree.findFirstWithName(panel, "performanceHighlight" + index); var area = widget.getArea();
            double x = area.x + area.width / 2.0 - list.getScrollX(), y = area.y + area.height / 2.0 - list.getScrollY();
            ui.screen().getContext().updateState((int) x, (int) y, 0); ui.screen().onFrameUpdate();
            if (ui.screen().getContext().getTopHovered() != widget) throw new AssertionError("Native performance button could not be hovered: " + index);
            client.screen.afterMouseAction();
            boolean handled = net.neoforged.neoforge.client.ClientHooks.onScreenMouseClickedPre(client.screen, x, y, 0);
            if (!handled) handled = net.neoforged.neoforge.client.ClientHooks.onScreenMouseClickedPost(client.screen, x, y, 0, client.screen.mouseClicked(x, y, 0));
            if (!handled) throw new AssertionError("Actual performance screen rejected highlight mouse click");
            if (!net.neoforged.neoforge.client.ClientHooks.onScreenMouseReleasedPre(client.screen, x, y, 0))
                net.neoforged.neoforge.client.ClientHooks.onScreenMouseReleasedPost(client.screen, x, y, 0, client.screen.mouseReleased(x, y, 0));
        }
        private static void capture(net.minecraft.client.Minecraft client, String name, Runnable next) {
            capturing = true; net.minecraft.client.Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(),
                    message -> client.execute(() -> { capturing = false; next.run(); }));
        }
    }
}
