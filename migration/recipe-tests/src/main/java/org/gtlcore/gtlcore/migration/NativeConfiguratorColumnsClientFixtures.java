package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.machine.mui.MachineUIPanel;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.widgets.layout.Flow;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.apache.logging.log4j.LogManager;
import java.util.UUID;

/** Native production pattern-buffer menus; real mouse clicks reach all three configuration columns. */
@EventBusSubscriber(modid = "gtlcore_recipe_test")
public final class NativeConfiguratorColumnsClientFixtures {
    private static volatile BlockPos position;
    private static volatile boolean ready, hidden;
    private static volatile String name = "";
    private static volatile int serverPage;
    private static volatile boolean lastServerSlotActive;
    private static UUID owner;
    private static void prepare(net.minecraft.server.MinecraftServer server, UUID id, boolean large) {
        var player = server.getPlayerList().getPlayer(id); owner = id; position = player.blockPosition().offset(3, 0, 0);
        player.serverLevel().setBlockAndUpdate(position, (large ? NativeAePatternFixtures.LARGE_BUFFER : NativeAePatternFixtures.BUFFER).getBlock().defaultBlockState());
        var host = (MEPatternBufferPartMachine) player.serverLevel().getBlockEntity(position);
        host.setFrontFacing(Direction.NORTH); host.isHiddenTerminal = false; host.setCustomName("");
        if (large) host.getTerminalPatternInventory().setItemDirect(107, appeng.api.crafting.PatternDetailsHelper.encodeProcessingPattern(
                java.util.List.of(new appeng.api.stacks.GenericStack(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.IRON_INGOT), 1)),
                java.util.List.of(new appeng.api.stacks.GenericStack(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.GOLD_INGOT), 1))));
        ready = true;
    }
    private static void open(net.minecraft.server.MinecraftServer server) {
        var player = server.getPlayerList().getPlayer(owner);
        ((IMuiMachine) player.serverLevel().getBlockEntity(position)).tryToOpenUI(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(position.getCenter(), Direction.NORTH, position, false));
    }
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("gtlcore.columnsValidation.enabled") || owner == null || position == null) return;
        var player = event.getServer().getPlayerList().getPlayer(owner);
        if (player != null && player.serverLevel().getBlockEntity(position) instanceof MEPatternBufferPartMachine host) {
            hidden = host.isHiddenTerminal; name = host.getCustomName();
            if (player.containerMenu instanceof brachy.modularui.screen.ModularContainerMenu menu) {
                var page = menu.getSyncManager().findSyncHandlerNullable("molecularPageIndex", 0);
                if (page instanceof brachy.modularui.value.sync.IntSyncValue value) serverPage = value.getIntValue();
                var slot = menu.getSyncManager().findSyncHandlerNullable("molecularPatternSlot", 107);
                lastServerSlotActive = slot instanceof brachy.modularui.value.sync.ItemSlotSyncHandler value && value.getSlot().isActive();
            }
        }
    }
    @EventBusSubscriber(modid = "gtlcore_recipe_test", value = net.neoforged.api.distmarker.Dist.CLIENT)
    public static final class Client {
        private static boolean loading, prepared, opened, capturing;
        private static int phase, ticks;
        private static long started;
        @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
            if (!Boolean.getBoolean("gtlcore.columnsValidation.enabled")) return;
            var client = net.minecraft.client.Minecraft.getInstance();
            if (started == 0) started = System.nanoTime();
            if (System.nanoTime() - started > java.util.concurrent.TimeUnit.MINUTES.toNanos(5)) throw new AssertionError("Native columns client timed out at phase " + phase);
            if (!loading && client.getOverlay() == null && client.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
                loading = true; client.createWorldOpenFlows().openWorld("Native Recipe Validation", () -> { throw new AssertionError("Column validation world did not load"); }); return;
            }
            if (client.level == null || client.player == null || client.getSingleplayerServer() == null || capturing) return;
            var server = client.getSingleplayerServer();
            if (!prepared) { prepared = true; var id = client.player.getUUID(); server.execute(() -> prepare(server, id, false)); return; }
            // Allow the old native menu's vanilla C2S close to finish before placing/opening the larger buffer.
            if (phase == 7) {
                if (client.screen == null && ++ticks >= 20) {
                    phase = 8; server.execute(() -> prepare(server, owner, true));
                }
                return;
            }
            if (phase == 8) {
                if (ready && client.level.getBlockState(position).is(NativeAePatternFixtures.LARGE_BUFFER.getBlock())) {
                    phase = 9; ticks = 0; server.execute(() -> open(server));
                }
                return;
            }
            if (!opened && ready && client.level.getBlockEntity(position) instanceof MEPatternBufferPartMachine) {
                opened = true; server.execute(() -> open(server)); return;
            }
            if (!(client.screen instanceof brachy.modularui.api.IMuiScreen ui) || !ui.screen().getMainPanel().isValid()
                    || ui.screen().getContext().getGraphics() == null || ++ticks < 20) return;
            var main = (MachineUIPanel) ui.screen().getMainPanel();
            for (var panel : ui.screen().getPanelManager().getOpenPanels()) {
                bounded(client, panel);
                for (var child : panel.getChildren()) if (child.isEnabled()) bounded(client, child);
            }
            var columns = (Flow) main.getLeftConfiguratorPanel().getChildren().getFirst();
            if (columns.getChildren().size() != 3) throw new AssertionError("Actual native pattern buffer did not render three columns");
            for (var column : columns.getChildren()) for (var control : column.getChildren()) {
                bounded(client, control);
            }
            if (phase == 0) {
                click(client, ui, control(columns, 6)); phase = 1; ticks = 0;
            } else if (phase == 1 && hidden && client.level.getBlockEntity(position) instanceof MEPatternBufferPartMachine host && host.isHiddenTerminal) {
                capture(client, "native-ae-columns.png", () -> { phase = 2; ticks = 0; });
            } else if (phase == 2) {
                click(client, ui, control(columns, 0)); phase = 3; ticks = 0;
            } else if (phase == 3) {
                var popup = popup(ui, "patternSharedItemsPanel"); if (popup == null) return;
                capture(client, "native-ae-columns-items.png", () -> {
                    click(client, ui, popup.getChildren().getFirst()); phase = 4; ticks = 0;
                });
            } else if (phase == 4) {
                click(client, ui, control(columns, 8)); phase = 5; ticks = 0;
            } else if (phase == 5) {
                var popup = popup(ui, "patternRenamePanel"); if (popup == null) return;
                ui.screen().getSyncManager().getPanelSyncManager(popup.getName())
                        .findSyncHandler("patternCustomName", 0, brachy.modularui.value.sync.StringSyncValue.class).setStringValue("native column edit", true, true);
                phase = 6; ticks = 0;
            } else if (phase == 6 && name.equals("native column edit")
                    && client.level.getBlockEntity(position) instanceof MEPatternBufferPartMachine host && host.getCustomName().equals(name)) {
                capture(client, "native-ae-columns-rename.png", () -> {
                    ready = false; phase = 7; ticks = 0; client.setScreen(null);
                });
            } else if (phase == 9 || phase == 10) {
                var pages = (brachy.modularui.widgets.PagedWidget<?>) brachy.modularui.widget.WidgetTree.findFirstWithName(main, "molecularPatternPages");
                var viewport = (brachy.modularui.widget.ScrollWidget<?>) pages.getCurrentPage();
                var data = viewport.getScrollArea().getScrollY();
                int lastRowOffset = data.getScrollSize() - data.getVisibleSize(viewport.getScrollArea());
                if (viewport.getScrollY() < lastRowOffset) { scroll(client, ui, viewport); phase = 10; ticks = 0; return; }
                capture(client, "native-ae-columns-scrolled.png", () -> {
                    click(client, ui, viewport.getChildren().getFirst().getChildren().get(71), 2); phase = 11; ticks = 0;
                });
            } else if (phase == 11) {
                var popup = popup(ui, "patternCatalystsPanel71"); if (popup == null) return;
                capture(client, "native-ae-columns-catalysts.png", () -> {
                    click(client, ui, popup.getChildren().getFirst()); phase = 12; ticks = 0;
                });
            } else if (phase == 12) {
                click(client, ui, brachy.modularui.widget.WidgetTree.findFirstWithName(main, "molecularPatternNext")); phase = 13; ticks = 0;
            } else if (phase == 13 && serverPage == 1 && lastServerSlotActive) {
                var manager = ui.screen().getSyncManager().getPanelSyncManager(main.getName());
                var last = manager.findSyncHandler("molecularPatternSlot", 107, brachy.modularui.value.sync.ItemSlotSyncHandler.class).getSlot();
                if (manager.findSyncHandler("molecularPageIndex", brachy.modularui.value.sync.IntSyncValue.class).getIntValue() != 1
                        || !last.isActive() || manager.findSyncHandler("molecularPatternSlot", 0, brachy.modularui.value.sync.ItemSlotSyncHandler.class).getSlot().isActive()
                        || !(last.getItem().getItem() instanceof appeng.crafting.pattern.EncodedPatternItem<?> pattern)
                        || !pattern.getOutput(last.getItem()).is(net.minecraft.world.item.Items.GOLD_INGOT))
                    throw new AssertionError("Native pagination lost the real last slot, processing-pattern components or active-page sync");
                var pages = (brachy.modularui.widgets.PagedWidget<?>) brachy.modularui.widget.WidgetTree.findFirstWithName(main, "molecularPatternPages");
                var viewport = (brachy.modularui.widget.ScrollWidget<?>) pages.getCurrentPage();
                var data = viewport.getScrollArea().getScrollY();
                if (viewport.getScrollY() < data.getScrollSize() - data.getVisibleSize(viewport.getScrollArea())) {
                    scroll(client, ui, viewport); ticks = 0; return;
                }
                var outputSlot = (org.gtlcore.gtlcore.integration.ae2.gui.PatternBufferItemSlot) viewport.getChildren().getFirst().getChildren().get(35);
                if (!outputSlot.getLastDisplayedStack().is(net.minecraft.world.item.Items.GOLD_INGOT))
                    throw new AssertionError("Native pattern slot did not draw its processing output while preserving the encoded pattern");
                capture(client, "native-ae-columns-large.png", () -> {
                    click(client, ui, viewport.getChildren().getFirst().getChildren().get(35), 2); phase = 14; ticks = 0;
                });
            } else if (phase == 14) {
                var popup = popup(ui, "patternCatalystsPanel107"); if (popup == null) return;
                capture(client, "native-ae-columns-last-catalysts.png", () -> {
                    LogManager.getLogger().info("GTL_NATIVE_CONFIGURATOR_COLUMNS_CLIENT=PASS controls=9 columns=3 first_popup=true second_c2s_s2c=true third_popup=true rename_c2s_s2c=true bounded=true slots=108 scroll=true last_row_popup=true page_c2s_s2c=true last_slot_popup=true output_preview=true");
                    client.setScreen(null); client.stop();
                });
            }
        }
        private static void bounded(net.minecraft.client.Minecraft client, IWidget widget) {
            var area = widget.getArea();
            if (area.x < 0 || area.y < 0 || area.x + area.width > client.screen.width || area.y + area.height > client.screen.height)
                throw new AssertionError("Native configuration panel/control exceeds the scaled screen: " + widget.getName() + " " + area);
        }
        private static IWidget control(Flow columns, int index) {
            return columns.getChildren().get(index / 4).getChildren().get(index % 4);
        }
        private static ModularPanel<?> popup(brachy.modularui.api.IMuiScreen ui, String name) {
            return ui.screen().getPanelManager().getOpenPanels().stream().filter(p -> p.getName().equals(name)).findFirst().orElse(null);
        }
        private static void capture(net.minecraft.client.Minecraft client, String name, Runnable next) {
            capturing = true;
            net.minecraft.client.Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), message -> client.execute(() -> { capturing = false; next.run(); }));
        }
        private static void click(net.minecraft.client.Minecraft client, brachy.modularui.api.IMuiScreen ui, IWidget widget) {
            click(client, ui, widget, 0);
        }
        private static void click(net.minecraft.client.Minecraft client, brachy.modularui.api.IMuiScreen ui, IWidget widget, int button) {
            var area = widget.getArea(); double x = area.x + area.width / 2.0, y = area.y + area.height / 2.0;
            for (var parent = widget.getParent(); parent != null; parent = parent.getParent())
                if (parent instanceof brachy.modularui.widget.AbstractScrollWidget<?, ?> viewport) { x -= viewport.getScrollX(); y -= viewport.getScrollY(); }
            ui.screen().getContext().updateState((int) x, (int) y, 0); ui.screen().onFrameUpdate();
            if (ui.screen().getContext().getTopHovered() != widget) throw new AssertionError("Native column control did not receive hover");
            client.screen.afterMouseAction();
            boolean handled = net.neoforged.neoforge.client.ClientHooks.onScreenMouseClickedPre(client.screen, x, y, button);
            if (!handled) handled = net.neoforged.neoforge.client.ClientHooks.onScreenMouseClickedPost(client.screen, x, y, button, client.screen.mouseClicked(x, y, button));
            if (!handled) throw new AssertionError("Native column screen click was not handled");
            if (!net.neoforged.neoforge.client.ClientHooks.onScreenMouseReleasedPre(client.screen, x, y, button))
                net.neoforged.neoforge.client.ClientHooks.onScreenMouseReleasedPost(client.screen, x, y, button, client.screen.mouseReleased(x, y, button));
        }
        private static void scroll(net.minecraft.client.Minecraft client, brachy.modularui.api.IMuiScreen ui, IWidget viewport) {
            var area = viewport.getArea(); double x = area.x + area.width / 2.0, y = area.y + area.height / 2.0;
            ui.screen().getContext().updateState((int) x, (int) y, 0); ui.screen().onFrameUpdate(); client.screen.afterMouseAction();
            boolean handled = net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                    new net.neoforged.neoforge.client.event.ScreenEvent.MouseScrolled.Pre(client.screen, x, y, 0, -1)).isCanceled();
            if (!handled) {
                handled = client.screen.mouseScrolled(x, y, 0, -1);
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                        new net.neoforged.neoforge.client.event.ScreenEvent.MouseScrolled.Post(client.screen, x, y, 0, -1));
            }
            if (!handled) throw new AssertionError("Native pattern viewport rejected actual screen wheel input");
        }
    }
}
