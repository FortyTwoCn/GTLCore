package org.gtlcore.gtlcore.client.ae2.wireless;

import org.gtlcore.gtlcore.integration.ae2.chamber.MEChamberConfigurator;
import org.gtlcore.gtlcore.integration.ae2.chamber.MEChamberConfigurator.Kind;
import org.gtlcore.gtlcore.migration.NativeAeGridFixtures;
import org.gtlcore.gtlcore.common.machine.multiblock.part.TagFilterMEStockBusPartMachine;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import brachy.modularui.widget.WidgetTree;
import io.netty.buffer.Unpooled;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.glfw.GLFW;
import java.util.function.Consumer;

/** Exercises the production embed and widgets. Commands use an explicit server fixture callback. */
@EventBusSubscriber(modid = "gtlcore_recipe_test", value = net.neoforged.api.distmarker.Dist.CLIENT)
public final class NativeChamberConfiguratorClientFixtures {
    private static volatile boolean ready;
    private static volatile int circuit = -1, interval;
    private static volatile String white = "", black = "";
    private static BlockPos position;
    private static boolean loading, preparing, opened, capturing;
    private static int stage, ticks;
    private static long started;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("gtlcore.chamberConfiguratorValidation.enabled")) return;
        var client = Minecraft.getInstance();
        if (started == 0) started = System.nanoTime();
        if (System.nanoTime() - started > java.util.concurrent.TimeUnit.MINUTES.toNanos(5))
            throw new AssertionError("Chamber client timed out at " + stage + ":" + ticks);
        if (!loading && client.getOverlay() == null && client.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
            loading = true;
            client.createWorldOpenFlows().openWorld("Native Recipe Validation", () -> { throw new AssertionError("Chamber world did not load"); });
            return;
        }
        if (client.level == null || client.player == null || client.getSingleplayerServer() == null || capturing) return;
        if (!preparing) {
            preparing = true; var server = client.getSingleplayerServer(); var id = client.player.getUUID();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(id); position = player.blockPosition().offset(3, 0, 0);
                player.serverLevel().setBlockAndUpdate(position, NativeAeGridFixtures.TAG_STOCK_ITEM.getBlock().defaultBlockState());
                ready = true;
            });
            return;
        }
        if (!ready) return;
        if (!opened) { opened = true; client.setScreen(new FixtureScreen(Kind.values()[stage], client.player.containerMenu)); return; }
        if (!(client.screen instanceof FixtureScreen screen) || screen.widgets.embeddedScreen().getContext().getGraphics() == null) return;
        ++ticks;
        if (stage == 0) {
            if (ticks == 20) click(screen, "chamberCircuit0");
            if (ticks == 35 && circuit != 0) throw new AssertionError("Native click lost configured circuit zero");
            if (ticks == 40) click(screen, "chamberClearCircuit");
            if (ticks == 55 && circuit != -1) throw new AssertionError("Native clear button did not clear the real circuit");
            if (ticks == 60) click(screen, "chamberCircuit32");
        } else if (stage == 1 && ticks == 20) {
            type(screen, "chamberRefreshTicks", "2147483647");
        } else if (stage == 2 && ticks == 20) {
            type(screen, "chamberTagWhitelist", "c:ingots/* & !c:ingots/gold");
            type(screen, "chamberTagBlacklist", "过滤".repeat(128));
        }
        if (ticks < 100) return;
        if (stage == 0 && circuit != 32 || stage == 1 && interval != Integer.MAX_VALUE ||
                stage == 2 && (!white.equals("c:ingots/* & !c:ingots/gold") || !black.equals("过滤".repeat(128))))
            throw new AssertionError("Native input lost fixture command at stage " + stage +
                    ": circuit=" + circuit + " interval=" + interval + " white=" + white + " blackLength=" + black.length());
        var area = screen.widgets.embeddedScreen().getMainPanel().getArea();
        if (area.x < 0 || area.y < 22 || area.x + area.width + 6 > screen.width || area.y + area.height + 6 > screen.height)
            throw new AssertionError("Native chamber panel exceeds scaled screen");
        capturing = true; String name = new String[]{"circuits", "refresh", "tags"}[stage];
        net.minecraft.client.Screenshot.grab(client.gameDirectory, "native-chamber-" + name + ".png", client.getMainRenderTarget(), message -> client.execute(() -> {
            screen.mouseClicked(area.x + area.width - 5, area.y - 11, 0);
            if (screen.widgets.isOpen() || client.screen != screen || client.player.containerMenu != screen.parentMenu)
                throw new AssertionError("Closing the native embed replaced or closed its parent menu");
            LogManager.getLogger().info("GTL_NATIVE_CHAMBER_PANEL=PASS kind={} native_inputs=true fixture_commands=true parent_retained=true screenshot={}", name, message.getString());
            if (++stage == 3) {
                LogManager.getLogger().info("GTL_NATIVE_CHAMBER_CONFIGURATOR_CLIENT=PASS panels=3 native_inputs=true fixture_commands=true bounded=true parent_retained=true");
                client.stop(); return;
            }
            opened = capturing = false; ticks = 0; client.setScreen(null);
        }));
    }

    private static void command(Kind kind, Consumer<FriendlyByteBuf> write) {
        var client = Minecraft.getInstance(); var buf = new FriendlyByteBuf(Unpooled.buffer());
        byte[] bytes;
        try { write.accept(buf); bytes = new byte[buf.readableBytes()]; buf.readBytes(bytes); }
        finally { buf.release(); }
        var server = client.getSingleplayerServer(); var id = client.player.getUUID();
        server.execute(() -> {
            var machine = (TagFilterMEStockBusPartMachine) server.getPlayerList().getPlayer(id).serverLevel().getBlockEntity(position);
            var payload = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
            try { MEChamberConfigurator.handleClientAction(machine, kind, 1, payload); }
            finally { payload.release(); }
            var stack = machine.getCircuitSlot().storage.getStackInSlot(0);
            circuit = stack.isEmpty() ? -1 : IntCircuitBehaviour.getCircuitConfiguration(stack);
            interval = machine.getMEUpdateInterval(); white = machine.getTagWhite(); black = machine.getTagBlack();
        });
    }
    private static void click(FixtureScreen screen, String name) {
        var widget = WidgetTree.findFirstWithName(screen.widgets.embeddedScreen().getMainPanel(), name);
        var area = widget.getArea(); double x = area.x + area.width / 2.0, y = area.y + area.height / 2.0;
        if (!screen.mouseClicked(x, y, 0) || !screen.mouseReleased(x, y, 0)) throw new AssertionError("Native embed rejected mouse input " + name);
    }
    private static void type(FixtureScreen screen, String name, String value) {
        click(screen, name);
        // Native ModularUI does not handle End; move to the actual end before deleting existing text.
        for (int i = 0; i < 256; ++i) screen.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0);
        for (int i = 0; i < 256; ++i) screen.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0);
        for (char character : value.toCharArray()) screen.charTyped(character, 0);
        // Commit via a real focus change, as the production terminal does.
        var area = screen.widgets.embeddedScreen().getMainPanel().getArea();
        screen.mouseClicked(area.x + 2, area.y + 2, 0); screen.mouseReleased(area.x + 2, area.y + 2, 0);
    }
    private static final class FixtureScreen extends Screen {
        private final MEChamberConfiguratorWidgetHost widgets = new MEChamberConfiguratorWidgetHost();
        private final Kind kind;
        private final AbstractContainerMenu parentMenu;
        private FixtureScreen(Kind kind, AbstractContainerMenu parentMenu) { super(Component.literal("Native chamber configurator")); this.kind = kind; this.parentMenu = parentMenu; }
        @Override protected void init() {
            for (String name : new String[]{"inset_panel", "button_normal", "button_hover", "button_selected", "button_warning", "button_disabled"}) {
                var resource = net.minecraft.resources.ResourceLocation.parse("gtlcore:textures/gui/wireless/" + name + ".png");
                if (Minecraft.getInstance().getResourceManager().getResource(resource).isEmpty())
                    throw new AssertionError("Missing original wireless UI texture: " + resource);
            }
            widgets.open(MEChamberConfiguratorPanels.create(kind, circuit, interval, white, black, write -> command(kind, write)), width, height);
        }
        @Override public void render(GuiGraphics graphics, int x, int y, float tick) { widgets.render(graphics, width, height, x, y, tick); }
        @Override public boolean mouseClicked(double x, double y, int button) { return widgets.mouseClicked(x, y, button); }
        @Override public boolean mouseReleased(double x, double y, int button) { return widgets.mouseReleased(x, y, button); }
        @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return widgets.mouseDragged(x, y, button, dx, dy); }
        @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) { return widgets.mouseScrolled(x, y, horizontal, vertical); }
        @Override public boolean keyPressed(int key, int scan, int mods) { return widgets.keyPressed(key, scan, mods); }
        @Override public boolean keyReleased(int key, int scan, int mods) { return widgets.keyReleased(key, scan, mods); }
        @Override public boolean charTyped(char character, int mods) { return widgets.charTyped(character, mods); }
        @Override public void removed() { widgets.close(); }
        @Override public boolean isPauseScreen() { return false; }
    }
}
