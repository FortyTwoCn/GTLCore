package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.machine.trait.MEPart.*;
import org.gtlcore.gtlcore.mixin.gtmt.MEOutputPartMachineAccessor;
import org.gtlcore.gtlcore.client.gui.widget.MEOutListGridWidget;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.integration.ae2.gui.*;
import com.gregtechceu.gtceu.integration.ae2.utils.KeyStorage;
import appeng.api.stacks.*;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.value.sync.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.apache.logging.log4j.LogManager;
import java.util.*;

/** Real output menus, readonly AE S2C lists, physical scrolling/hover, return C2S and interval popup C2S. */
@EventBusSubscriber(modid = "gtlcore_recipe_test")
public final class NativeGtOutputClientFixtures {
    private static final long LARGE = 9_007_199_254_740_993L;
    private static volatile BlockPos position;
    private static volatile boolean ready;
    private static volatile int time, interval;
    private static UUID owner;
    private static int stage;
    private static final List<String> NAMES = List.of("item", "fluid", "dual");
    private static AEKey key(boolean fluid, int slot) {
        var name = Component.literal("native component output " + slot);
        if (fluid) { var stack = new net.neoforged.neoforge.fluids.FluidStack(Fluids.WATER, 1); stack.set(DataComponents.CUSTOM_NAME, name); return AEFluidKey.of(stack); }
        var stack = new ItemStack(Items.IRON_INGOT); stack.set(DataComponents.CUSTOM_NAME, name); return AEItemKey.of(stack);
    }
    private static KeyStorage ledger(MetaMachine machine, boolean fluid) {
        if (machine instanceof IMENativeOutputBuffer owner) return owner.gtlcore$getOutputBuffer();
        var owner = (MEOutputPartMachineAccessor) machine; return fluid ? owner.gtlcore$getInternalTankBuffer() : owner.gtlcore$getInternalBuffer();
    }
    private static void prepare(net.minecraft.server.MinecraftServer server, UUID id, int next) {
        var player = server.getPlayerList().getPlayer(id); owner = id; stage = next; ready = false;
        position = player.blockPosition().offset(3, 0, 0);
        var definition = List.of(NativeGtOutputFixtures.ITEM, NativeGtOutputFixtures.FLUID, NativeGtOutputFixtures.DUAL).get(next);
        player.serverLevel().setBlockAndUpdate(position, definition.getBlock().defaultBlockState());
        var host = (MetaMachine) player.serverLevel().getBlockEntity(position); host.setFrontFacing(Direction.NORTH);
        ((com.gregtechceu.gtceu.api.capability.IControllable) host).setWorkingEnabled(false);
        ((IModifiableSyncOffset) host).setMEUpdateInterval(300); ((IMEOutputPart) host).setTime(278);
        for (boolean fluid : new boolean[]{false, true}) {
            if (fluid ? next == 0 : next == 1) continue;
            var buffer = ledger(host, fluid);
            for (int slot = 0; slot < 12; slot++) buffer.storage.put(key(fluid, slot), (fluid ? Long.MAX_VALUE : LARGE) - slot);
            buffer.onChanged();
        }
        ready = true;
    }
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("gtlcore.gtOutputValidation.enabled") || owner == null || position == null) return;
        var player = event.getServer().getPlayerList().getPlayer(owner);
        if (player != null && player.serverLevel().getBlockEntity(position) instanceof MetaMachine host) {
            time = ((IMEOutputPart) host).getTime(); interval = ((IModifiableSyncOffset) host).getMEUpdateInterval();
        }
    }
    @EventBusSubscriber(modid = "gtlcore_recipe_test", value = net.neoforged.api.distmarker.Dist.CLIENT)
    public static final class Client {
        private static boolean loading, prepared, opened, capturing, tooltipRendered;
        private static int completed, phase, ticks;
        private static long started;
        private static boolean fluid() { return completed != 0; }
        private static String syncName() { return fluid() ? "gtlcoreOutputFluids" : "gtlcoreOutputItems"; }
        @SubscribeEvent public static void tooltip(net.neoforged.neoforge.client.event.RenderTooltipEvent.GatherComponents event) {
            if (!Boolean.getBoolean("gtlcore.gtOutputValidation.enabled") || phase != 2) return;
            var stack = GenericStack.unwrapItemStack(event.getItemStack()); long amount = (fluid() ? Long.MAX_VALUE : LARGE) - 11;
            if (stack != null && stack.what().equals(key(fluid(), 11)) && stack.amount() == amount &&
                    event.getTooltipElements().stream().flatMap(value -> value.left().stream()).anyMatch(value -> value.getString().contains(AEGuiHelper.formatAmountFull(amount))))
                tooltipRendered = true;
        }
        @SubscribeEvent public static void rendered(net.neoforged.neoforge.client.event.ScreenEvent.Render.Post event) {
            if (!Boolean.getBoolean("gtlcore.gtOutputValidation.enabled") || phase != 2 || ticks < 10 || capturing) return;
            var client = net.minecraft.client.Minecraft.getInstance();
            if (!(event.getScreen() instanceof brachy.modularui.api.IMuiScreen ui) || !tooltipRendered) return;
            var hovered = ui.screen().getContext().getTopHovered();
            if (!(hovered instanceof MEOutListGridWidget.DisplaySlot row) || !row.displayed().what().equals(key(fluid(), 11)))
                throw new AssertionError("Last native output row is not physically hovered");
            event.getGuiGraphics().flush();
            capture(client, "last", () -> { click(client, ui, named(ui.screen().getMainPanel(), "gtlcoreMEOutputReturnButton")); phase = 3; ticks = 0; });
        }
        @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
            if (!Boolean.getBoolean("gtlcore.gtOutputValidation.enabled")) return;
            var client = net.minecraft.client.Minecraft.getInstance(); if (started == 0) started = System.nanoTime();
            if (System.nanoTime() - started > java.util.concurrent.TimeUnit.MINUTES.toNanos(5)) throw new AssertionError("Native output GUI timed out at " + completed + "/" + phase);
            if (!loading && client.getOverlay() == null && client.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
                loading = true; client.createWorldOpenFlows().openWorld("Native Recipe Validation", () -> { throw new AssertionError("Native output GUI world did not load"); }); return;
            }
            if (client.level == null || client.player == null || client.getSingleplayerServer() == null || capturing) return;
            var server = client.getSingleplayerServer();
            if (!prepared) { prepared = true; var id = client.player.getUUID(); int next = completed; server.execute(() -> prepare(server, id, next)); return; }
            if (!opened && ready && client.level.getBlockEntity(position) instanceof IMuiMachine) {
                server.execute(() -> { var player = server.getPlayerList().getPlayer(owner);
                    ((IMuiMachine) player.serverLevel().getBlockEntity(position)).tryToOpenUI(player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(position.getCenter(), Direction.NORTH, position, false)); }); opened = true; return;
            }
            if (!(client.screen instanceof brachy.modularui.api.IMuiScreen ui) || !ui.screen().getMainPanel().isValid() || ui.screen().getContext().getGraphics() == null) return;
            if (++ticks < 20) return;
            var main = ui.screen().getMainPanel(); var manager = ui.screen().getSyncManager().getPanelSyncManager(main.getName());
            for (var panel : ui.screen().getPanelManager().getOpenPanels()) bounded(client, panel);
            bounded(client, named(main, "gtlcoreMEOutputReturnButton")); bounded(client, named(main, "gtlcoreMERefreshButton"));
            if (phase == 0) {
                if (time != 278 || interval != 300) return;
                var readiness = manager.findSyncHandler("gtlcoreMEOutputReady", BooleanSyncValue.class);
                if (readiness.getBoolValue() || readiness.isAllowC2S()) throw new AssertionError("Native return readiness is editable or lost its saved phase");
                for (boolean fluid : new boolean[]{false, true}) {
                    if (fluid ? completed == 0 : completed == 1) continue;
                    var contents = manager.findSyncHandler(fluid ? "gtlcoreOutputFluids" : "gtlcoreOutputItems", AEKeyStorageSyncHandler.class);
                    if (contents.isAllowC2S() || contents.getValue().size() != 12) throw new AssertionError("Native output list is editable or omitted component keys");
                    for (int slot = 0; slot < 12; slot++) {
                        var stack = contents.getValue().get(slot);
                        if (!stack.what().equals(key(fluid, slot)) || stack.amount() != (fluid ? Long.MAX_VALUE : LARGE) - slot)
                            throw new AssertionError("Native output S2C lost exact long quantities or components");
                    }
                }
                capture(client, "main", () -> { phase = 1; ticks = 0; });
            } else if (phase == 1) {
                var list = (MEOutListGridWidget) named(main, syncName() + "List");
                var viewport = (brachy.modularui.widget.AbstractScrollWidget<?, ?>) list.getChildren().getFirst();
                var data = viewport.getScrollArea().getScrollY();
                if (viewport.getScrollY() < data.getScrollSize() - data.getVisibleSize(viewport.getScrollArea())) { scroll(client, ui, viewport); ticks = 15; return; }
                var last = (MEOutListGridWidget.DisplaySlot) named(main, syncName() + "Slot11");
                var point = point(last); moveNativeMouse(client, point[0], point[1]);
                ui.screen().getContext().updateState((int) point[0], (int) point[1], 0); ui.screen().onFrameUpdate();
                if (ui.screen().getContext().getTopHovered() != last) throw new AssertionError("Native output scroll did not expose its last row");
                tooltipRendered = false; phase = 2; ticks = 0;
            } else if (phase == 3 && time == 0 && manager.findSyncHandler("gtlcoreMEOutputReady", BooleanSyncValue.class).getBoolValue()) {
                click(client, ui, named(main, "gtlcoreMERefreshButton")); phase = 4; ticks = 0;
            } else if (phase == 4) {
                var popup = popup(ui); if (popup == null) return;
                ui.screen().getSyncManager().getPanelSyncManager(popup.getName()).findSyncHandler("gtlcoreMERefreshTicks", StringSyncValue.class).setStringValue("501", true, true);
                phase = 5; ticks = 0;
            } else if (phase == 5 && interval == 501) {
                var popup = popup(ui); if (popup == null) return;
                if (!ui.screen().getSyncManager().getPanelSyncManager(popup.getName()).findSyncHandler("gtlcoreMERefreshTicks", StringSyncValue.class).getStringValue().equals("501")) return;
                capture(client, "refresh", () -> {
                    if (++completed == 3) { LogManager.getLogger().info("GTL_NATIVE_GT_OUTPUT_CLIENT=PASS menus=3 keys_per_list=12 long_components_s2c=true readonly=true scroll_last=true exact_hover=true return_click_c2s_s2c=true interval_c2s_s2c=true bounded=true"); client.setScreen(null); client.stop(); return; }
                    client.player.closeContainer(); prepared = opened = capturing = false; ready = false; ticks = phase = 0;
                });
            }
        }
        private static IWidget named(IWidget parent, String name) { return brachy.modularui.widget.WidgetTree.findFirstWithName(parent, name); }
        private static brachy.modularui.screen.ModularPanel<?> popup(brachy.modularui.api.IMuiScreen ui) { return ui.screen().getPanelManager().getOpenPanels().stream().filter(panel -> panel.getName().equals("gtlcoreMERefreshPanel")).findFirst().orElse(null); }
        private static void bounded(net.minecraft.client.Minecraft client, IWidget widget) { var area = widget.getArea(); if (area.x < 0 || area.y < 0 || area.x + area.width > client.screen.width || area.y + area.height > client.screen.height) throw new AssertionError("Native output panel/control exceeds the screen: " + widget.getName() + " " + area); }
        private static double[] point(IWidget widget) { var area = widget.getArea(); double x = area.x + area.width / 2.0, y = area.y + area.height / 2.0;
            for (var parent = widget.getParent(); parent != null; parent = parent.getParent()) if (parent instanceof brachy.modularui.widget.AbstractScrollWidget<?, ?> viewport) { x -= viewport.getScrollX(); y -= viewport.getScrollY(); }
            return new double[]{x, y}; }
        private static void moveNativeMouse(net.minecraft.client.Minecraft client, double x, double y) {
            try { var callback = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onMove", long.class, double.class, double.class); callback.setAccessible(true);
                callback.invoke(client.mouseHandler, client.getWindow().getWindow(), x * client.getWindow().getScreenWidth() / client.screen.width, y * client.getWindow().getScreenHeight() / client.screen.height);
            } catch (ReflectiveOperationException error) { throw new AssertionError("Native output mouse callback failed", error); }
        }
        private static void capture(net.minecraft.client.Minecraft client, String name, Runnable next) { capturing = true;
            net.minecraft.client.Screenshot.grab(client.gameDirectory, "native-gt-output-" + NAMES.get(completed) + "-" + name + ".png", client.getMainRenderTarget(), message -> client.execute(() -> { capturing = false; next.run(); })); }
        private static void click(net.minecraft.client.Minecraft client, brachy.modularui.api.IMuiScreen ui, IWidget widget) {
            var point = point(widget); double x = point[0], y = point[1]; ui.screen().getContext().updateState((int) x, (int) y, 0); ui.screen().onFrameUpdate();
            if (ui.screen().getContext().getTopHovered() != widget) throw new AssertionError("Native output click did not hover its control");
            client.screen.afterMouseAction(); boolean handled = net.neoforged.neoforge.client.ClientHooks.onScreenMouseClickedPre(client.screen, x, y, 0);
            if (!handled) handled = net.neoforged.neoforge.client.ClientHooks.onScreenMouseClickedPost(client.screen, x, y, 0, client.screen.mouseClicked(x, y, 0));
            if (!handled) throw new AssertionError("Native output screen rejected the physical click");
            if (!net.neoforged.neoforge.client.ClientHooks.onScreenMouseReleasedPre(client.screen, x, y, 0)) net.neoforged.neoforge.client.ClientHooks.onScreenMouseReleasedPost(client.screen, x, y, 0, client.screen.mouseReleased(x, y, 0));
        }
        private static void scroll(net.minecraft.client.Minecraft client, brachy.modularui.api.IMuiScreen ui, IWidget viewport) {
            var point = point(viewport); double x = point[0], y = point[1]; ui.screen().getContext().updateState((int) x, (int) y, 0); ui.screen().onFrameUpdate(); client.screen.afterMouseAction();
            boolean handled = net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.ScreenEvent.MouseScrolled.Pre(client.screen, x, y, 0, -1)).isCanceled();
            if (!handled) { handled = client.screen.mouseScrolled(x, y, 0, -1); net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.ScreenEvent.MouseScrolled.Post(client.screen, x, y, 0, -1)); }
            if (!handled) throw new AssertionError("Native output screen did not scroll its list");
        }
    }
}
