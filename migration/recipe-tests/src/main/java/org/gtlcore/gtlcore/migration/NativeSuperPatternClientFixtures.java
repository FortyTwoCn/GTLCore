package org.gtlcore.gtlcore.migration;

import com.gtladd.gtladditions.common.machine.multiblock.part.MESuperPatternBufferPartMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.machine.mui.MachineUIPanel;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.api.IMuiScreen;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.value.sync.*;
import brachy.modularui.widgets.PagedWidget;
import brachy.modularui.widget.*;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.apache.logging.log4j.LogManager;
import java.util.*;

/** Physical native Additions menus at default, maximum and minimum configured capacities. */
@EventBusSubscriber(modid = "gtlcore_recipe_test")
public final class NativeSuperPatternClientFixtures {
    private static final List<String> NAMES = List.of("default", "wide", "tiny");
    private static final int[] SIZES = {162, 1800, 1}, PAGE_SIZES = {54, 180, 1};
    private static volatile BlockPos position;
    private static UUID owner;
    private static volatile boolean ready, serverFOA, serverLastActive;
    private static volatile int serverPage, serverMultiplier;
    private static void prepare(net.minecraft.server.MinecraftServer server, UUID id, int variant) {
        ready = false; owner = id; serverPage = 0; serverFOA = false;
        var player = server.getPlayerList().getPlayer(id); position = player.blockPosition().offset(3, 0, 0);
        var definition = variant == 0 ? NativeSuperPatternFixtures.BUFFER : variant == 1 ? NativeSuperPatternFixtures.WIDE_BUFFER : NativeSuperPatternFixtures.TINY_BUFFER;
        player.serverLevel().setBlockAndUpdate(position, definition.getBlock().defaultBlockState());
        var host = (MESuperPatternBufferPartMachine) player.serverLevel().getBlockEntity(position); host.setFrontFacing(Direction.NORTH);
        var output = new net.minecraft.world.item.ItemStack(Items.GOLD_INGOT); output.set(DataComponents.CUSTOM_NAME, Component.literal("Native super final output"));
        var pattern = PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(AEItemKey.of(Items.COPPER_INGOT), 1)), List.of(new GenericStack(AEItemKey.of(output), 1)));
        pattern.set(DataComponents.CUSTOM_NAME, Component.literal("Native super final pattern")); host.getTerminalPatternInventory().setItemDirect(SIZES[variant] - 1, pattern); ready = true;
    }
    private static void open(net.minecraft.server.MinecraftServer server) {
        var player = server.getPlayerList().getPlayer(owner);
        ((IMuiMachine) player.serverLevel().getBlockEntity(position)).tryToOpenUI(player, InteractionHand.MAIN_HAND, new BlockHitResult(position.getCenter(), Direction.NORTH, position, false));
    }
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("gtlcore.superPatternValidation.enabled") || owner == null || position == null) return;
        var player = event.getServer().getPlayerList().getPlayer(owner);
        if (player != null && player.serverLevel().getBlockEntity(position) instanceof MESuperPatternBufferPartMachine host) {
            serverFOA = host.isFOAModeEnabled(); serverMultiplier = host.getFOAPatternOutputMultiplier();
            if (player.containerMenu instanceof brachy.modularui.screen.ModularContainerMenu menu) {
                var page = menu.getSyncManager().findSyncHandlerNullable("molecularPageIndex", 0); if (page instanceof IntSyncValue value) serverPage = value.getIntValue();
                var slot = menu.getSyncManager().findSyncHandlerNullable("molecularPatternSlot", host.getPatternInventory().getSlots() - 1); serverLastActive = slot instanceof ItemSlotSyncHandler value && value.getSlot().isActive();
            }
        }
    }
    @EventBusSubscriber(modid = "gtlcore_recipe_test", value = net.neoforged.api.distmarker.Dist.CLIENT)
    public static final class Client {
        private static boolean loading, prepared, opened, capturing;
        private static int variant, phase, ticks, previousPhase = -1;
        private static long started, phaseStarted, lastProgress;
        @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
            if (!Boolean.getBoolean("gtlcore.superPatternValidation.enabled")) return;
            var client = net.minecraft.client.Minecraft.getInstance(); if (started == 0) started = System.nanoTime();
            if (System.nanoTime() - started > java.util.concurrent.TimeUnit.MINUTES.toNanos(5)) throw new AssertionError("Native super client timeout variant=" + variant + " phase=" + phase);
            if (!loading && client.getOverlay() == null && client.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
                loading = true; client.createWorldOpenFlows().openWorld("Native Recipe Validation", () -> { throw new AssertionError("Native super validation world did not load"); }); return;
            }
            if (client.level == null || client.player == null || client.getSingleplayerServer() == null || capturing) return;
            var server = client.getSingleplayerServer();
            if (!prepared) { prepared = true; var id = client.player.getUUID(); server.execute(() -> prepare(server, id, variant)); return; }
            if (phase == 8) { if (client.screen == null && ++ticks >= 20) { prepared = false; opened = false; phase = 0; ticks = 0; } return; }
            if (!opened && ready && client.level.getBlockEntity(position) instanceof MESuperPatternBufferPartMachine host && host.getPatternInventory().getSlots() == SIZES[variant]) { opened = true; server.execute(() -> open(server)); return; }
            if (!(client.screen instanceof IMuiScreen ui) || !ui.screen().getMainPanel().isValid() || ui.screen().getContext().getGraphics() == null || ++ticks < 15) return;
            for (var panel : ui.screen().getPanelManager().getOpenPanels()) { bounded(client, panel); for (var child : panel.getChildren()) if (child.isEnabled()) bounded(client, child); }
            var main = (MachineUIPanel) ui.screen().getMainPanel();
            for (var column : main.getLeftConfiguratorPanel().getChildren().getFirst().getChildren()) for (var control : column.getChildren()) bounded(client, control);
            var page = ui.screen().getSyncManager().findSyncHandler("molecularPageIndex", IntSyncValue.class);
            if (previousPhase != phase) { previousPhase = phase; phaseStarted = System.nanoTime(); }
            if (System.nanoTime() - phaseStarted > java.util.concurrent.TimeUnit.SECONDS.toNanos(45)) throw new AssertionError("Native super phase stalled variant=" + NAMES.get(variant) + " phase=" + phase + " clientPage=" + page.getIntValue() + " serverPage=" + serverPage + " lastActive=" + serverLastActive);
            if (phase == 0) {
                for (int index = 0; index < SIZES[variant]; index++) if (ui.screen().getSyncManager().findSyncHandlerNullable("molecularPatternSlot", index) == null) throw new AssertionError("Native super menu lost real slot " + index);
                if (variant == 0) { click(client, ui, named(main, "superPatternFOAButton"), 0); phase = 1; ticks = 0; }
                else capture(client, "main", () -> { phase = 4; ticks = 0; });
            } else if (phase == 1) {
                var popup = popup(ui, "superPatternFOAConfigPanel"); if (popup == null) return;
                click(client, ui, named(popup, "superPatternFOAToggle"), 0);
                ui.screen().getSyncManager().getPanelSyncManager(popup.getName()).findSyncHandler("superPatternFOAMultiplier", StringSyncValue.class).setStringValue("23", true, true); phase = 2; ticks = 0;
            } else if (phase == 2 && serverFOA && serverMultiplier == 23 && client.level.getBlockEntity(position) instanceof MESuperPatternBufferPartMachine host && host.isFOAModeEnabled() && host.getFOAPatternOutputMultiplier() == 23) {
                capture(client, "foa", () -> { click(client, ui, popup(ui, "superPatternFOAConfigPanel").getChildren().getFirst(), 0); phase = 3; ticks = 0; });
            } else if (phase == 3 && popup(ui, "superPatternFOAConfigPanel") == null) capture(client, "main", () -> { phase = 4; ticks = 0; });
            else if (phase == 4) {
                int lastPage = (SIZES[variant] - 1) / PAGE_SIZES[variant];
                if (System.nanoTime() - lastProgress > java.util.concurrent.TimeUnit.SECONDS.toNanos(10)) {
                    lastProgress = System.nanoTime();
                    var viewport = (ScrollWidget<?>) ((PagedWidget<?>) named(main, "molecularPatternPages")).getCurrentPage(); var scroll = viewport.getScrollArea();
                    LogManager.getLogger().info("Native super scroll variant={} clientPage={} serverPage={} lastActive={} x={} y={} width={} height={} xSize={} ySize={} xVisible={} yVisible={}", NAMES.get(variant), page.getIntValue(), serverPage, serverLastActive, viewport.getScrollX(), viewport.getScrollY(), scroll.width, scroll.height, scroll.getScrollX() == null ? -1 : scroll.getScrollX().getScrollSize(), scroll.getScrollY() == null ? -1 : scroll.getScrollY().getScrollSize(), scroll.getScrollX() == null ? -1 : scroll.getScrollX().getFullVisibleSize(scroll), scroll.getScrollY() == null ? -1 : scroll.getScrollY().getFullVisibleSize(scroll));
                }
                if (page.getIntValue() < lastPage) { click(client, ui, named(main, "molecularPatternNext"), 0); ticks = 0; return; }
                if (serverPage != lastPage || !serverLastActive) return;
                var pages = (PagedWidget<?>) named(main, "molecularPatternPages"); var viewport = (ScrollWidget<?>) pages.getCurrentPage(); var scroll = viewport.getScrollArea();
                if (scroll.getScrollY() != null && viewport.getScrollY() < scroll.getScrollY().getScrollSize() - scroll.getScrollY().getFullVisibleSize(scroll)) { wheel(client, ui, viewport, 0, -12); ticks = 0; return; }
                if (scroll.getScrollX() != null && viewport.getScrollX() < scroll.getScrollX().getScrollSize() - scroll.getScrollX().getFullVisibleSize(scroll)) { wheel(client, ui, viewport, -12, 0); ticks = 0; return; }
                var last = viewport.getChildren().getFirst().getChildren().get((SIZES[variant] - 1) % PAGE_SIZES[variant]); var point = point(last); move(client, point[0], point[1]); phase = 5; ticks = 0;
            } else if (phase == 5) {
                var handler = ui.screen().getSyncManager().findSyncHandler("molecularPatternSlot", SIZES[variant] - 1, ItemSlotSyncHandler.class);
                if (!handler.getSlot().isActive() || !handler.getSlot().getItem().has(DataComponents.CUSTOM_NAME)) throw new AssertionError("Native super last pattern lost its components or page activation");
                var pages = (PagedWidget<?>) named(main, "molecularPatternPages"); var viewport = (ScrollWidget<?>) pages.getCurrentPage(); var last = viewport.getChildren().getFirst().getChildren().get((SIZES[variant] - 1) % PAGE_SIZES[variant]);
                if (!(last instanceof org.gtlcore.gtlcore.integration.ae2.gui.PatternBufferItemSlot slot) || !slot.getLastDisplayedStack().is(Items.GOLD_INGOT)) throw new AssertionError("Native super final pattern output was not actually rendered");
                capture(client, "last", () -> {
                    if (variant == 2) finish(client);
                    else { click(client, ui, last, 2); phase = 6; ticks = 0; }
                });
            } else if (phase == 6 && popup(ui, "patternCatalystsPanel" + (SIZES[variant] - 1)) != null) capture(client, "catalysts", () -> { client.setScreen(null); variant++; ready = false; phase = 8; ticks = 0; });
        }
        private static void finish(net.minecraft.client.Minecraft client) {
            LogManager.getLogger().info("GTL_NATIVE_SUPER_PATTERN_CLIENT=PASS menus=3 default_slots=162 max_slots=1800 min_slots=1 foa_c2s_s2c=true page_c2s_s2c=true scroll_both_axes=true last_middle_popup=true component_pattern=true output_preview=true bounded=true"); client.setScreen(null); client.stop();
        }
        private static void bounded(net.minecraft.client.Minecraft client, IWidget widget) { var area = widget.getArea(); if (area.x < 0 || area.y < 0 || area.x + area.width > client.screen.width || area.y + area.height > client.screen.height) throw new AssertionError("Native super UI exceeds scaled screen variant=" + NAMES.get(variant) + " phase=" + phase + " widget=" + widget.getClass().getName() + "/" + widget.getName() + " " + area); }
        private static IWidget named(IWidget root, String name) { var widget = WidgetTree.findFirstWithName(root, name); if (widget == null) throw new AssertionError("Native super widget missing: " + name); return widget; }
        private static ModularPanel<?> popup(IMuiScreen ui, String name) { return ui.screen().getPanelManager().getOpenPanels().stream().filter(panel -> panel.getName().equals(name)).findFirst().orElse(null); }
        private static void capture(net.minecraft.client.Minecraft client, String stage, Runnable next) { capturing = true; net.minecraft.client.Screenshot.grab(client.gameDirectory, "native-super-pattern-" + NAMES.get(variant) + "-" + stage + ".png", client.getMainRenderTarget(), message -> client.execute(() -> { capturing = false; next.run(); })); }
        private static double[] point(IWidget widget) { var area = widget.getArea(); double x = area.x + area.width / 2.0, y = area.y + area.height / 2.0; for (var parent = widget.getParent(); parent != null; parent = parent.getParent()) if (parent instanceof AbstractScrollWidget<?, ?> viewport) { x -= viewport.getScrollX(); y -= viewport.getScrollY(); } return new double[]{x, y}; }
        private static void move(net.minecraft.client.Minecraft client, double x, double y) {
            try { var callback = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onMove", long.class, double.class, double.class); callback.setAccessible(true); callback.invoke(client.mouseHandler, client.getWindow().getWindow(), x * client.getWindow().getScreenWidth() / client.screen.width, y * client.getWindow().getScreenHeight() / client.screen.height); }
            catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        }
        private static void click(net.minecraft.client.Minecraft client, IMuiScreen ui, IWidget widget, int button) {
            var point = point(widget); double x = point[0], y = point[1]; move(client, x, y); ui.screen().getContext().updateState((int) x, (int) y, 0); ui.screen().onFrameUpdate();
            if (ui.screen().getContext().getTopHovered() != widget) throw new AssertionError("Native super click did not hover variant=" + NAMES.get(variant) + " phase=" + phase + " widget=" + widget.getClass().getName() + "/" + widget.getName() + " area=" + widget.getArea() + " point=" + x + "," + y + " hovered=" + ui.screen().getContext().getTopHovered()); client.screen.afterMouseAction();
            boolean handled = net.neoforged.neoforge.client.ClientHooks.onScreenMouseClickedPre(client.screen, x, y, button); if (!handled) handled = net.neoforged.neoforge.client.ClientHooks.onScreenMouseClickedPost(client.screen, x, y, button, client.screen.mouseClicked(x, y, button)); if (!handled) throw new AssertionError("Native super screen click was not handled");
            if (!net.neoforged.neoforge.client.ClientHooks.onScreenMouseReleasedPre(client.screen, x, y, button)) net.neoforged.neoforge.client.ClientHooks.onScreenMouseReleasedPost(client.screen, x, y, button, client.screen.mouseReleased(x, y, button));
        }
        private static void wheel(net.minecraft.client.Minecraft client, IMuiScreen ui, IWidget viewport, double dx, double dy) {
            var point = point(viewport); double x = point[0], y = point[1]; move(client, x, y); ui.screen().getContext().updateState((int) x, (int) y, 0); ui.screen().onFrameUpdate(); client.screen.afterMouseAction();
            boolean handled = net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.ScreenEvent.MouseScrolled.Pre(client.screen, x, y, dx, dy)).isCanceled(); if (!handled) { handled = client.screen.mouseScrolled(x, y, dx, dy); net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.ScreenEvent.MouseScrolled.Post(client.screen, x, y, dx, dy)); } if (!handled) throw new AssertionError("Native super viewport rejected wheel input");
        }
    }
}
