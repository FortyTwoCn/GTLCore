package org.gtlcore.gtlcore.migration;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.client.renderer.AABBHighlightRenderer;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.hepdd.gtmthings.api.gui.HighlightTextWidget;
import com.hepdd.gtmthings.api.misc.WirelessEnergyContainer;
import com.hepdd.gtmthings.data.CreativeMachines;
import com.hepdd.gtmthings.data.WirelessMachines;
import brachy.modularui.api.IMuiScreen;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.value.sync.*;
import brachy.modularui.widget.ScrollWidget;
import brachy.modularui.widget.WidgetTree;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.ToggleButton;
import brachy.modularui.widgets.textfield.TextFieldWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.apache.logging.log4j.LogManager;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Original GTMThings registrations, native block menus and actual ModularUI keyboard/mouse packets. */
@EventBusSubscriber(modid = "gtlcore_recipe_test")
public final class NativeEnergyClientFixtures {
    private static final long ODD = 9_007_199_254_740_993L;
    private static volatile boolean ready;
    private static volatile BlockPos position;
    private static volatile long serverVoltage;
    private static volatile int serverAmps;
    private static volatile boolean serverAll;
    private static int stage;
    private static UUID owner, other;
    private static final List<MetaMachine> sources = new ArrayList<>();

    private static void prepare(net.minecraft.server.MinecraftServer server, UUID id) {
        ready = false; owner = id; sources.clear();
        var player = server.getPlayerList().getPlayer(id); position = player.blockPosition().offset(3, 0, 0);
        var entry = stage == 0 ? CreativeMachines.CREATIVE_ENERGY_INPUT_HATCH : stage == 1 ?
                CreativeMachines.CREATIVE_LASER_INPUT_HATCH : WirelessMachines.WIRELESS_ENERGY_MONITOR;
        player.serverLevel().setBlockAndUpdate(position, entry.getBlock().defaultBlockState());
        var machine = (MetaMachine) player.serverLevel().getBlockEntity(position); machine.setOwnerUUID(owner);
        if (stage == 2) {
            other = UUID.randomUUID();
            WirelessEnergyContainer.getOrCreateContainer(owner).setStorage(BigInteger.TEN.pow(80));
            WirelessEnergyContainer.getOrCreateContainer(other).setStorage(BigInteger.TEN.pow(40));
            for (int index = 0; index < 3; index++) {
                var pos = position.offset(2 + index, 0, 0);
                player.serverLevel().setBlockAndUpdate(pos, GTMachines.HULL[GTValues.HV].getBlock().defaultBlockState());
                sources.add((MetaMachine) player.serverLevel().getBlockEntity(pos));
            }
        }
        ready = true;
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void tickServer(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("gtlcore.energyValidation.enabled") || !ready || owner == null) return;
        var player = event.getServer().getPlayerList().getPlayer(owner);
        if (player == null || !(player.serverLevel().getBlockEntity(position) instanceof MetaMachine machine)) return;
        if (stage < 2) {
            var energy = machine.getTrait(NotifiableEnergyContainer.class);
            serverVoltage = energy.getInputVoltage(); serverAmps = (int) energy.getInputAmperage();
        } else {
            serverAll = machine.saveWithoutMetadata(player.serverLevel().registryAccess()).getBoolean("all");
            for (int index = 0; index < sources.size(); index++)
                WirelessEnergyContainer.getOrCreateContainer(index < 2 ? owner : other).addEnergy(100 + index, sources.get(index));
        }
    }

    private static void open(net.minecraft.server.MinecraftServer server) {
        var player = server.getPlayerList().getPlayer(owner);
        ((IMuiMachine) player.serverLevel().getBlockEntity(position)).tryToOpenUI(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(position.getCenter(), Direction.NORTH, position, false));
    }

    private static void reload(net.minecraft.server.MinecraftServer server) {
        ready = false;
        var level = server.getPlayerList().getPlayer(owner).serverLevel();
        var original = (MetaMachine) level.getBlockEntity(position);
        var entry = stage == 0 ? CreativeMachines.CREATIVE_ENERGY_INPUT_HATCH : CreativeMachines.CREATIVE_LASER_INPUT_HATCH;
        var saved = original.saveWithoutMetadata(level.registryAccess());
        var restored = (MetaMachine) entry.getBlockEntityType().create(position, original.getBlockState());
        level.removeBlockEntity(position); restored.loadAdditional(saved, level.registryAccess()); level.setBlockEntity(restored);
        ready = true;
    }

    @EventBusSubscriber(modid = "gtlcore_recipe_test", value = net.neoforged.api.distmarker.Dist.CLIENT)
    public static final class Client {
        private static boolean loading, prepared, opened, capturing;
        private static int phase, ticks;
        private static long started;
        private static BlockPos highlightTarget;
        @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
            if (!Boolean.getBoolean("gtlcore.energyValidation.enabled")) return;
            var client = Minecraft.getInstance(); if (started == 0) started = System.nanoTime();
            if (System.nanoTime() - started > java.util.concurrent.TimeUnit.MINUTES.toNanos(5))
                throw new AssertionError("Native energy menus timed out at " + stage + "/" + phase + " voltage=" + serverVoltage + " amps=" + serverAmps);
            if (!loading && client.getOverlay() == null && client.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
                loading = true; client.createWorldOpenFlows().openWorld("Native Recipe Validation", () -> { throw new AssertionError("Native energy world did not load"); }); return;
            }
            if (client.level == null || client.player == null || client.getSingleplayerServer() == null || capturing) return;
            var server = client.getSingleplayerServer();
            if (!prepared) { prepared = true; var id = client.player.getUUID(); server.execute(() -> prepare(server, id)); return; }
            var block = stage == 0 ? CreativeMachines.CREATIVE_ENERGY_INPUT_HATCH.getBlock() : stage == 1 ?
                    CreativeMachines.CREATIVE_LASER_INPUT_HATCH.getBlock() : WirelessMachines.WIRELESS_ENERGY_MONITOR.getBlock();
            if (!opened && ready && client.level.getBlockState(position).is(block) && client.level.getBlockEntity(position) instanceof IMuiMachine) {
                opened = true; server.execute(() -> open(server)); return;
            }
            if (stage == 2 && phase == 3) {
                if (++ticks < 20) return;
                checkHighlight(highlightTarget);
                capture(client, "native-wireless-monitor-highlight.png", () -> {
                    LogManager.getLogger().info("GTL_NATIVE_ENERGY_CLIENT=PASS original_menus=3 dropdown_c2s_s2c=true exact_odd_long=true typed_max_long=true zero_amps=true plus_c2s_s2c=true creative_reload=true monitor_scope_c2s_s2c=true native_rich_rows=true physical_scroll=true native_highlight=true bounded=true");
                    client.stop();
                }); return;
            }
            if (!(client.screen instanceof IMuiScreen ui) || !ui.screen().getMainPanel().isValid() || ui.screen().getContext().getGraphics() == null || ++ticks < 20) return;
            var panel = ui.screen().getMainPanel(); bounded(client, panel);
            if (stage == 2) bounded(client, WidgetTree.findFirstWithName(panel, "gtlcore_wireless_title"));
            var manager = ui.screen().getSyncManager().getPanelSyncManager(panel.getName());
            if (stage == 2) { monitor(client, ui, manager); return; }
            var voltage = manager.findSyncHandler("voltage", StringSyncValue.class);
            var amps = manager.findSyncHandler("amps", IntSyncValue.class);
            String prefix = stage == 0 ? "native-creative-energy-" : "native-creative-laser-";
            if (phase == 0 && serverVoltage == Long.MAX_VALUE && voltage.getStringValue().equals(Long.toString(Long.MAX_VALUE))) {
                var dropdown = WidgetTree.findFirstWithName(panel, "creative_energy_tiers"); click(client, ui, dropdown); phase = 1; ticks = 0;
            } else if (phase == 1) {
                var menu = ui.screen().getPanelManager().getOpenPanels().stream().filter(p -> p.getName().equals("creative_energy_tiers")).findFirst().orElse(null);
                if (menu == null) return;
                bounded(client, menu);
                var options = all(menu).stream().filter(ButtonWidget.class::isInstance).toList();
                if (options.size() != (stage == 0 ? 31 : 26)) throw new AssertionError("Native creative dropdown lost its upper tiers");
                capture(client, prefix + "tiers.png", () -> { click(client, ui, options.getFirst()); phase = 2; ticks = 0; });
            } else if (phase == 2 && serverVoltage == GTValues.VEX[stage == 0 ? 0 : 5] && voltage.getStringValue().equals(Long.toString(serverVoltage))) {
                type(client, ui, (TextFieldWidget) WidgetTree.findFirstWithName(panel, "creative_voltage"), Long.toString(ODD)); phase = 3; ticks = 0;
            } else if (phase == 3 && serverVoltage == ODD && voltage.getStringValue().equals(Long.toString(ODD))) {
                capture(client, prefix + "odd.png", () -> { type(client, ui, (TextFieldWidget) WidgetTree.findFirstWithName(panel, "creative_voltage"), Long.toString(Long.MAX_VALUE)); phase = 4; ticks = 0; });
            } else if (phase == 4 && serverVoltage == Long.MAX_VALUE && voltage.getStringValue().equals(Long.toString(Long.MAX_VALUE))) {
                capture(client, prefix + "maximum.png", () -> { type(client, ui, (TextFieldWidget) WidgetTree.findFirstWithName(panel, "creative_amperage"), "0"); phase = 5; ticks = 0; });
            } else if (phase == 5 && serverAmps == 0 && amps.getIntValue() == 0) {
                click(client, ui, WidgetTree.findFirstWithName(panel, "creative_amp_increase")); phase = 6; ticks = 0;
            } else if (phase == 6 && serverAmps == 1 && amps.getIntValue() == 1) {
                client.player.closeContainer(); opened = false; phase = 7; ticks = 0; ready = false;
                server.execute(() -> reload(server));
            } else if (phase == 7 && serverVoltage == Long.MAX_VALUE && serverAmps == 1 && voltage.getStringValue().equals(Long.toString(Long.MAX_VALUE)) && amps.getIntValue() == 1) {
                capture(client, prefix + "reload.png", () -> { client.player.closeContainer(); phase = ticks = 0; prepared = opened = ready = false; stage++; });
            }
        }

        private static void monitor(Minecraft client, IMuiScreen ui, PanelSyncManager manager) {
            var allValue = manager.findSyncHandler("all", BooleanSyncValue.class);
            var handler = manager.findSyncHandler("monitor", GenericListSyncHandler.class);
            @SuppressWarnings("unchecked") var rows = (List<Component>) handler.getValue();
            long transfers = rows.stream().filter(row -> HighlightTextWidget.findAction(row) != null).count();
            if (phase == 0 && !allValue.getBoolValue() && !serverAll && transfers == 2) {
                capture(client, "native-wireless-monitor-team.png", () -> {
                    click(client, ui, all(ui.screen().getMainPanel()).stream().filter(ToggleButton.class::isInstance).findFirst().orElseThrow()); phase = 1; ticks = 0;
                });
            } else if (phase == 1 && allValue.getBoolValue() && serverAll && transfers == 3) {
                capture(client, "native-wireless-monitor-all.png", () -> {
                    var scroll = (ScrollWidget<?>) all(ui.screen().getMainPanel()).stream().filter(ScrollWidget.class::isInstance).findFirst().orElseThrow();
                    var area = scroll.getArea(); double x = area.x + area.width / 2.0, y = area.y + area.height / 2.0;
                    hover(client, ui, x, y);
                    boolean moved = false;
                    for (int step = 0; step < 12; step++) {
                        boolean handled = net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.ScreenEvent.MouseScrolled.Pre(client.screen, x, y, 0, -1)).isCanceled();
                        if (!handled) handled = client.screen.mouseScrolled(x, y, 0, -1);
                        moved |= handled;
                        if (!handled) break;
                    }
                    if (!moved) throw new AssertionError("Native wireless viewport rejected wheel input");
                    phase = 2; ticks = 0;
                });
            } else if (phase == 2) {
                var scroll = (ScrollWidget<?>) all(ui.screen().getMainPanel()).stream().filter(ScrollWidget.class::isInstance).findFirst().orElseThrow();
                if (scroll.getScrollY() <= 0) throw new AssertionError("Native wireless wheel input did not move its viewport");
                var viewport = scroll.getArea();
                var widget = all(scroll).stream().filter(HighlightTextWidget.class::isInstance).filter(w ->
                        w.getArea().y - scroll.getScrollY() >= viewport.y &&
                        w.getArea().y - scroll.getScrollY() + w.getArea().height <= viewport.y + viewport.height).findFirst().orElseThrow();
                try {
                    var action = HighlightTextWidget.class.getDeclaredField("action"); action.setAccessible(true);
                    String[] parts = ((String) action.get(widget)).split(";", 2); highlightTarget = BlockPos.of(Long.parseLong(parts[1]));
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                var area = widget.getArea();
                click(client, ui, area.x + area.width / 2.0 - scroll.getScrollX(),
                        area.y + area.height / 2.0 - scroll.getScrollY());
                phase = 3; ticks = 0;
            }
        }
        private static void checkHighlight(BlockPos pos) {
            try {
                var field = AABBHighlightRenderer.class.getDeclaredField("highlights"); field.setAccessible(true);
                @SuppressWarnings("unchecked") var highlights = (List<AABBHighlightRenderer.AABBHighlight>) field.get(AABBHighlightRenderer.INSTANCE);
                if (highlights.stream().noneMatch(highlight -> highlight.aabb().equals(new AABB(pos)) && highlight.durationMillis() == 100_000))
                    throw new AssertionError("Actual native monitor click did not highlight its dimension/position");
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        }
        private static List<IWidget> all(IWidget parent) { var widgets = new ArrayList<IWidget>(); widgets.add(parent); for (var child : parent.getChildren()) widgets.addAll(all(child)); return widgets; }
        private static void bounded(Minecraft client, IWidget widget) { var area = widget.getArea(); if (area.x < 0 || area.y < 0 || area.x + area.width > client.screen.width || area.y + area.height > client.screen.height) throw new AssertionError("Native energy menu exceeds scaled screen"); }
        private static void hover(Minecraft client, IMuiScreen ui, double x, double y) {
            try { var move = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onMove", long.class, double.class, double.class); move.setAccessible(true); move.invoke(client.mouseHandler, client.getWindow().getWindow(), x * client.getWindow().getScreenWidth() / client.screen.width, y * client.getWindow().getScreenHeight() / client.screen.height); }
            catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            ui.screen().getContext().updateState((int) x, (int) y, 0); ui.screen().onFrameUpdate();
        }
        private static void click(Minecraft client, IMuiScreen ui, IWidget widget) {
            var area = widget.getArea(); double x = area.x + area.width / 2.0, y = area.y + area.height / 2.0;
            click(client, ui, x, y);
        }
        private static void click(Minecraft client, IMuiScreen ui, double x, double y) {
            var screen = client.screen;
            hover(client, ui, x, y); client.screen.afterMouseAction();
            boolean handled = net.neoforged.neoforge.client.ClientHooks.onScreenMouseClickedPre(client.screen, x, y, 0);
            if (!handled) handled = net.neoforged.neoforge.client.ClientHooks.onScreenMouseClickedPost(client.screen, x, y, 0, client.screen.mouseClicked(x, y, 0));
            if (!handled) throw new AssertionError("Native energy click rejected at " + stage + "/" + phase);
            if (client.screen != screen) return; // Native highlight clicks close their own menu immediately.
            if (!net.neoforged.neoforge.client.ClientHooks.onScreenMouseReleasedPre(client.screen, x, y, 0)) net.neoforged.neoforge.client.ClientHooks.onScreenMouseReleasedPost(client.screen, x, y, 0, client.screen.mouseReleased(x, y, 0));
        }
        private static void type(Minecraft client, IMuiScreen ui, TextFieldWidget field, String text) {
            click(client, ui, field);
            if (!field.isFocused()) throw new AssertionError("Native creative voltage input did not receive focus");
            for (int step = 0; step < 32; step++) key(client, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT);
            for (int step = 0; step < 32; step++) key(client, org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE);
            for (char character : text.toCharArray()) if (!net.neoforged.neoforge.client.ClientHooks.onScreenCharTypedPre(client.screen, character, 0) && !client.screen.charTyped(character, 0)) net.neoforged.neoforge.client.ClientHooks.onScreenCharTypedPost(client.screen, character, 0);
            if (!field.getText().equals(text)) throw new AssertionError("Native creative input rounded typed digits: " + field.getText());
            key(client, org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
        }
        private static void key(Minecraft client, int key) { if (!net.neoforged.neoforge.client.ClientHooks.onScreenKeyPressedPre(client.screen, key, 0, 0) && !client.screen.keyPressed(key, 0, 0)) net.neoforged.neoforge.client.ClientHooks.onScreenKeyPressedPost(client.screen, key, 0, 0); }
        private static void capture(Minecraft client, String name, Runnable next) { capturing = true; net.minecraft.client.Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), text -> client.execute(() -> { capturing = false; next.run(); })); }
    }
}
