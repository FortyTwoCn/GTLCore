package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.integration.wildcard.*;
import org.gtlcore.gtlcore.client.gui.widget.PatternCycleWidget;
import org.gtlcore.gtlcore.client.gui.widget.AEDualConfigWidget;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.machine.mui.MachineUIPanel;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import appeng.api.stacks.*;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.integration.recipeviewer.handlers.IngredientProvider;
import brachy.modularui.value.sync.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.apache.logging.log4j.LogManager;
import java.util.*;

/** Actual production menus and physical screen interactions; the external pattern editor is outside this run. */
@EventBusSubscriber(modid = "gtlcore_recipe_test")
public final class NativeWildcardClientFixtures {
    private static final long AMOUNT = 9_007_199_254_740_993L;
    private static volatile BlockPos position;
    private static volatile boolean ready, hidden, restoredStock;
    private static volatile String name = "";
    private static volatile int page = 1, interval;
    private static UUID owner;
    private static ItemStack item() { var value = new ItemStack(Items.IRON_INGOT); value.set(DataComponents.CUSTOM_NAME, Component.literal("native wildcard long input")); return value; }
    private static net.neoforged.neoforge.fluids.FluidStack fluid() { var value = new net.neoforged.neoforge.fluids.FluidStack(Fluids.WATER, 1); value.set(DataComponents.CUSTOM_NAME, Component.literal("native wildcard component fluid")); return value; }
    private static CompoundTag entry(String type, CompoundTag data) { var value = new CompoundTag(); value.putString("type", type); value.put("data", data); return value; }
    private static ItemStack pattern(net.minecraft.server.level.ServerLevel level) {
        var in = new ListTag(); in.add(entry("simple", GenericStack.writeTag(level.registryAccess(), new GenericStack(AEItemKey.of(item()), AMOUNT))));
        in.add(entry("simple", GenericStack.writeTag(level.registryAccess(), new GenericStack(AEFluidKey.of(fluid()), Long.MAX_VALUE))));
        var out = new ListTag(); var data = new CompoundTag(); var tag = new CompoundTag(); tag.putString("item", "dust"); data.put("tag", tag); data.putLong("amount", 2); out.add(entry("tag_prefix", data));
        var filters = new ListTag(); var filter = new CompoundTag(); filter.putString("flag", "ingot"); filter.putBoolean("whitelist", true); filters.add(entry("property", filter));
        var settings = new CompoundTag(); settings.put("in", in); settings.put("out", out); settings.put("filter", filters);
        var stack = NativeWildcardFixtures.PATTERN.asStack(); stack.set(DataComponents.CUSTOM_DATA, CustomData.of(settings)); return stack;
    }
    private static void prepare(net.minecraft.server.MinecraftServer server, UUID id, boolean stocking) {
        var player = server.getPlayerList().getPlayer(id); owner = id; position = player.blockPosition().offset(3, 0, 0);
        var definition = stocking ? NativeWildcardFixtures.STOCKING : NativeWildcardFixtures.BUFFER;
        player.serverLevel().setBlockAndUpdate(position, definition.getBlock().defaultBlockState());
        var host = (MEWildcardPatternBufferPartMachine) player.serverLevel().getBlockEntity(position); host.setFrontFacing(Direction.NORTH);
        host.getTerminalPatternInventory().setItemDirect(0, pattern(player.serverLevel()));
        if (stocking) {
            var stocked = (MEStockingWildcardPatternBufferPartMachine) host; stocked.configureStock(31, new GenericStack(AEFluidKey.of(fluid()), 1000)); stocked.setMEUpdateInterval(3);
            var registry = player.serverLevel().registryAccess(); var saved = stocked.saveWithoutMetadata(registry);
            var copy = (MEStockingWildcardPatternBufferPartMachine) definition.getBlockEntityType().create(position, host.getBlockState());
            player.serverLevel().removeBlockEntity(position); copy.loadAdditional(saved, registry); player.serverLevel().setBlockEntity(copy);
            restoredStock = copy.getConfiguredStock(31).what().equals(AEFluidKey.of(fluid())) && copy.getMEUpdateInterval() == 3;
        }
        ready = true;
    }
    private static void open(net.minecraft.server.MinecraftServer server) {
        var player = server.getPlayerList().getPlayer(owner);
        ((IMuiMachine) player.serverLevel().getBlockEntity(position)).tryToOpenUI(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(position.getCenter(), Direction.NORTH, position, false));
    }
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("gtlcore.wildcardValidation.enabled") || owner == null || position == null) return;
        var player = event.getServer().getPlayerList().getPlayer(owner);
        if (player != null && player.serverLevel().getBlockEntity(position) instanceof MEWildcardPatternBufferPartMachine host) {
            hidden = host.isHiddenTerminal; name = host.getCustomName();
            if (host instanceof MEStockingWildcardPatternBufferPartMachine stocked) {
                interval = stocked.getMEUpdateInterval(); page = stocked.getPage();
            }
        }
    }
    @EventBusSubscriber(modid = "gtlcore_recipe_test", value = net.neoforged.api.distmarker.Dist.CLIENT)
    public static final class Client {
        private static boolean loading, prepared, opened, capturing;
        private static int phase, ticks, pageClicks;
        private static long started;
        private static AEKey firstOutput;
        private static boolean rotated, fluidTooltipRendered;
        @SubscribeEvent public static void tooltip(net.neoforged.neoforge.client.event.RenderTooltipEvent.GatherComponents event) {
            if (!Boolean.getBoolean("gtlcore.wildcardValidation.enabled") || phase != 12) return;
            var stack = GenericStack.unwrapItemStack(event.getItemStack());
            if (stack != null && stack.what().equals(AEFluidKey.of(fluid())) && stack.amount() == Long.MAX_VALUE &&
                    event.getTooltipElements().stream().flatMap(value -> value.left().stream()).anyMatch(value -> value.getString().contains("9,223,372,036,854,775,807 mB")))
                fluidTooltipRendered = true;
        }
        @SubscribeEvent public static void screenRendered(net.neoforged.neoforge.client.event.ScreenEvent.Render.Post event) {
            if (!Boolean.getBoolean("gtlcore.wildcardValidation.enabled") || phase != 12 || ticks < 20 || capturing) return;
            var client = net.minecraft.client.Minecraft.getInstance();
            if (!(event.getScreen() instanceof brachy.modularui.api.IMuiScreen ui)) return;
            if (!fluidTooltipRendered) throw new AssertionError("Native component fluid tooltip was not rendered");
            var hovered = ui.screen().getContext().getTopHovered();
            if (hovered == null || !(hovered instanceof IngredientProvider<?> provider) || provider.getIngredients().isEmpty())
                throw new AssertionError("Native fluid tooltip capture has no hovered ingredient");
            event.getGuiGraphics().flush();
            capture(client, "native-wildcard-fluid-tooltip.png", () -> {
                click(client, ui, named(ui.screen().getMainPanel(), "wildcardTerminalVisibility")); phase = 1; ticks = 0;
            });
        }
        @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
            if (!Boolean.getBoolean("gtlcore.wildcardValidation.enabled")) return;
            var client = net.minecraft.client.Minecraft.getInstance(); if (started == 0) started = System.nanoTime();
            if (System.nanoTime() - started > java.util.concurrent.TimeUnit.MINUTES.toNanos(5)) throw new AssertionError("Native wildcard client timed out at phase " + phase);
            if (!loading && client.getOverlay() == null && client.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
                loading = true; client.createWorldOpenFlows().openWorld("Native Recipe Validation", () -> { throw new AssertionError("Wildcard validation world did not load"); }); return;
            }
            if (client.level == null || client.player == null || client.getSingleplayerServer() == null || capturing) return;
            var server = client.getSingleplayerServer();
            if (!prepared) { prepared = true; var id = client.player.getUUID(); server.execute(() -> prepare(server, id, false)); return; }
            if (phase == 5) {
                if (client.screen == null && ++ticks >= 20) { phase = 6; ready = false; opened = false; server.execute(() -> prepare(server, owner, true)); }
                return;
            }
            if (!opened && ready && client.level.getBlockEntity(position) instanceof MEWildcardPatternBufferPartMachine) { opened = true; server.execute(() -> open(server)); return; }
            if (!(client.screen instanceof brachy.modularui.api.IMuiScreen ui) || !ui.screen().getMainPanel().isValid() || ui.screen().getContext().getGraphics() == null || ++ticks < 20) return;
            var main = (MachineUIPanel) ui.screen().getMainPanel(); var manager = ui.screen().getSyncManager().getPanelSyncManager(main.getName());
            for (var panel : ui.screen().getPanelManager().getOpenPanels()) bounded(client, panel);
            var preview = manager.findSyncHandler("wildcardExpandedPreview", GenericSyncValue.class);
            if (!(preview.getValue() instanceof PatternCycleWidget.Preview value) || value.inputs().size() != 2) return;
            if (value.inputs().stream().noneMatch(stack -> stack.what().equals(AEItemKey.of(item())) && stack.amount() == AMOUNT) ||
                    value.inputs().stream().noneMatch(stack -> stack.what().equals(AEFluidKey.of(fluid())) && stack.amount() == Long.MAX_VALUE)) throw new AssertionError("Native wildcard S2C preview lost long component keys");
            if (!value.outputs().isEmpty()) { if (firstOutput == null) firstOutput = value.outputs().getFirst().what(); else if (!firstOutput.equals(value.outputs().getFirst().what())) rotated = true; }
            bounded(client, named(main, "wildcardTitle"));
            var group = brachy.modularui.widget.WidgetTree.findFirstWithName(main, "wildcardExpandedPreview"); bounded(client, group);
            for (var cell : group.getChildren()) {
                bounded(client, cell);
                if (cell instanceof IngredientProvider<?> provider && !provider.getIngredients().isEmpty() && cell instanceof brachy.modularui.widgets.slot.ItemSlot)
                    throw new AssertionError("Wildcard preview exposed an editable item slot");
            }
            if (phase == 0) capture(client, "native-wildcard-buffer.png", () -> {
                int index = java.util.stream.IntStream.range(0, value.inputs().size()).filter(i -> value.inputs().get(i).what() instanceof AEFluidKey).findFirst().orElseThrow();
                var cell = group.getChildren().get(index * 2); var area = cell.getArea();
                var window = client.getWindow();
                org.lwjgl.glfw.GLFW.glfwFocusWindow(window.getWindow());
                org.lwjgl.glfw.GLFW.glfwSetCursorPos(window.getWindow(),
                        (area.x + area.width / 2.0) * window.getScreenWidth() / client.screen.width,
                        (area.y + area.height / 2.0) * window.getScreenHeight() / client.screen.height);
                moveNativeMouse(client, area.x + area.width / 2.0, area.y + area.height / 2.0);
                ui.screen().getContext().updateState(area.x + area.width / 2, area.y + area.height / 2, 0); ui.screen().onFrameUpdate();
                if (ui.screen().getContext().getTopHovered() != cell) throw new AssertionError("Native fluid preview did not receive hover");
                phase = 12; ticks = 0;
            });
            else if (phase == 12) return;
            else if (phase == 1 && hidden && client.level.getBlockEntity(position) instanceof MEWildcardPatternBufferPartMachine host && host.isHiddenTerminal) {
                click(client, ui, named(main, "wildcardRenameButton")); phase = 2; ticks = 0;
            } else if (phase == 2) {
                var popup = popup(ui, "wildcardRenamePanel"); if (popup == null) return;
                ui.screen().getSyncManager().getPanelSyncManager(popup.getName()).findSyncHandler("wildcardCustomName", StringSyncValue.class).setStringValue("native wildcard client name", true, true);
                phase = 3; ticks = 0;
            } else if (phase == 3 && name.equals("native wildcard client name")) {
                capture(client, "native-wildcard-rename.png", () -> { click(client, ui, popup(ui, "wildcardRenamePanel").getChildren().getFirst()); phase = 4; ticks = 0; });
            } else if (phase == 4 && client.level.getBlockEntity(position) instanceof MEWildcardPatternBufferPartMachine host && host.getCustomName().equals(name)) {
                client.setScreen(null); phase = 5; ticks = 0;
            } else if (phase == 6) capture(client, "native-wildcard-stocking.png", () -> { click(client, ui, named(main, "wildcardStockConfigurationButton")); phase = 7; ticks = 0; });
            else if (phase == 7) {
                var panel = popup(ui, "wildcardStockConfigurationPanel"); if (panel == null) return;
                click(client, ui, named(panel, "dualConfig_next_page")); pageClicks++; phase = 8; ticks = 0;
            } else if (phase == 8 && page == pageClicks + 1 && ui.screen().getSyncManager().getPanelSyncManager("wildcardStockConfigurationPanel").findSyncHandler("dualConfigPage", IntSyncValue.class).getIntValue() == page) {
                if (pageClicks < 3) { phase = 7; ticks = 0; return; }
                var dual = (AEDualConfigWidget) brachy.modularui.widget.WidgetTree.findFirst(popup(ui, "wildcardStockConfigurationPanel"), widget -> widget instanceof AEDualConfigWidget);
                var config = dual.getDisplayStack(31, false);
                if (config == null || !config.what().equals(AEFluidKey.of(fluid())) || !restoredStock) throw new AssertionError("Last native wildcard stock position or reloaded components were not synchronized");
                capture(client, "native-wildcard-stock-last-page.png", () -> { click(client, ui, popup(ui, "wildcardStockConfigurationPanel").getChildren().getFirst()); phase = 11; ticks = 0; });
            } else if (phase == 11) {
                click(client, ui, named(main, "gtlcoreMERefreshButton")); phase = 9; ticks = 0;
            } else if (phase == 9) {
                var popup = popup(ui, "gtlcoreMERefreshPanel"); if (popup == null) return;
                ui.screen().getSyncManager().getPanelSyncManager(popup.getName()).findSyncHandler("gtlcoreMERefreshTicks", StringSyncValue.class).setStringValue("7", true, true); phase = 10; ticks = 0;
            } else if (phase == 10 && interval == 7) capture(client, "native-wildcard-refresh.png", () -> {
                if (!rotated) throw new AssertionError("Actual native wildcard preview never rotated through server synchronization");
                LogManager.getLogger().info("GTL_NATIVE_WILDCARD_CLIENT=PASS menus=2 long_components=true rotation_s2c=true readonly_preview=true visibility_c2s_s2c=true rename_c2s_s2c=true pages=4 last_config=true stock_reload=true interval_c2s=true bounded=true fluid_tooltip=true");
                client.setScreen(null); client.stop();
            });
        }
        private static IWidget named(IWidget root, String name) { var value = brachy.modularui.widget.WidgetTree.findFirstWithName(root, name); if (value == null) throw new AssertionError("Native wildcard widget missing: " + name); return value; }
        private static void moveNativeMouse(net.minecraft.client.Minecraft client, double x, double y) {
            // Drive the vanilla input callback when the headless display has no window manager to deliver pointer events.
            try {
                var callback = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onMove", long.class, double.class, double.class);
                callback.setAccessible(true);
                callback.invoke(client.mouseHandler, client.getWindow().getWindow(),
                        x * client.getWindow().getScreenWidth() / client.screen.width,
                        y * client.getWindow().getScreenHeight() / client.screen.height);
            } catch (ReflectiveOperationException error) { throw new AssertionError("Native mouse movement failed", error); }
        }
        private static brachy.modularui.screen.ModularPanel<?> popup(brachy.modularui.api.IMuiScreen ui, String name) {
            return ui.screen().getPanelManager().getOpenPanels().stream().filter(panel -> panel.getName().equals(name)).findFirst().orElse(null);
        }
        private static void bounded(net.minecraft.client.Minecraft client, IWidget widget) { var area = widget.getArea(); if (area.x < 0 || area.y < 0 || area.x + area.width > client.screen.width || area.y + area.height > client.screen.height) throw new AssertionError("Native wildcard widget exceeds screen: " + widget.getName() + " " + area); }
        private static void capture(net.minecraft.client.Minecraft client, String name, Runnable next) { capturing = true; net.minecraft.client.Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), message -> client.execute(() -> { capturing = false; next.run(); })); }
        private static void click(net.minecraft.client.Minecraft client, brachy.modularui.api.IMuiScreen ui, IWidget widget) {
            click(client, ui, widget, 0);
        }
        private static void click(net.minecraft.client.Minecraft client, brachy.modularui.api.IMuiScreen ui, IWidget widget, int button) {
            var area = widget.getArea(); double x = area.x + area.width / 2.0, y = area.y + area.height / 2.0;
            for (var parent = widget.getParent(); parent != null; parent = parent.getParent())
                if (parent instanceof brachy.modularui.widget.AbstractScrollWidget<?, ?> viewport) { x -= viewport.getScrollX(); y -= viewport.getScrollY(); }
            ui.screen().getContext().updateState((int) x, (int) y, 0); ui.screen().onFrameUpdate();
            if (ui.screen().getContext().getTopHovered() != widget) throw new AssertionError("Native wildcard control did not receive hover");
            client.screen.afterMouseAction();
            boolean handled = net.neoforged.neoforge.client.ClientHooks.onScreenMouseClickedPre(client.screen, x, y, button);
            if (!handled) handled = net.neoforged.neoforge.client.ClientHooks.onScreenMouseClickedPost(client.screen, x, y, button, client.screen.mouseClicked(x, y, button));
            if (!handled) throw new AssertionError("Native wildcard screen click was not handled");
            if (!net.neoforged.neoforge.client.ClientHooks.onScreenMouseReleasedPre(client.screen, x, y, button))
                net.neoforged.neoforge.client.ClientHooks.onScreenMouseReleasedPost(client.screen, x, y, button, client.screen.mouseReleased(x, y, button));
        }
    }
}
