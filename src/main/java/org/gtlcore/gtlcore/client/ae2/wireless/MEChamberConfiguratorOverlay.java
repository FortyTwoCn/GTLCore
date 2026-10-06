package org.gtlcore.gtlcore.client.ae2.wireless;

import org.gtlcore.gtlcore.integration.ae2.chamber.MEChamberConfigurator;
import org.gtlcore.gtlcore.integration.ae2.chamber.MEChamberManagerTerminalMenu;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAePackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.PacketDistributor;
import io.netty.buffer.Unpooled;
import org.jetbrains.annotations.Nullable;
import java.util.function.Consumer;

/** Sends native panel edits through the existing, server-authorized terminal channel. */
final class MEChamberConfiguratorOverlay {
    private final MEChamberManagerTerminalMenu menu;
    private final MEChamberConfiguratorWidgetHost widgets = new MEChamberConfiguratorWidgetHost();
    private @Nullable MEChamberManagerTerminalMenu.Address address;
    private @Nullable MEChamberConfigurator.Kind kind;

    MEChamberConfiguratorOverlay(MEChamberManagerTerminalMenu menu) { this.menu = menu; }

    void open(MEChamberManagerTerminalMenu.Address address, MEChamberConfigurator.Kind kind,
              MEChamberManagerTerminalMenu.ChamberDetails details, int width, int height) {
        if (Minecraft.getInstance().player == null) return;
        close();
        this.address = address;
        this.kind = kind;
        var view = MEChamberConfiguratorPanels.create(kind,
                details.circuitSet() ? details.circuitConfiguration() : -1, details.syncOffset(),
                details.tagWhite(), details.tagBlack(), this::sendAction);
        widgets.open(view, width, height);
    }
    void close() { widgets.close(); address = null; kind = null; }
    boolean isOpen() { return widgets.isOpen(); }
    void render(GuiGraphics graphics, int width, int height, int mouseX, int mouseY, float tick) {
        widgets.render(graphics, width, height, mouseX, mouseY, tick);
    }
    boolean mouseClicked(double x, double y, int button) { return widgets.mouseClicked(x, y, button); }
    boolean mouseDragged(double x, double y, int button, double dx, double dy) { return widgets.mouseDragged(x, y, button, dx, dy); }
    boolean mouseReleased(double x, double y, int button) { return widgets.mouseReleased(x, y, button); }
    boolean mouseScrolled(double x, double y, double horizontal, double vertical) { return widgets.mouseScrolled(x, y, horizontal, vertical); }
    boolean keyPressed(int key, int scan, int mods) { return widgets.keyPressed(key, scan, mods); }
    boolean keyReleased(int key, int scan, int mods) { return widgets.keyReleased(key, scan, mods); }
    boolean charTyped(char character, int mods) { return widgets.charTyped(character, mods); }

    private void sendAction(Consumer<FriendlyByteBuf> write) {
        if (address == null || kind == null) return;
        var payload = new FriendlyByteBuf(Unpooled.buffer());
        try {
            write.accept(payload);
            if (payload.readableBytes() > MEChamberConfigurator.MAX_ACTION_BYTES) return;
            byte[] data = new byte[payload.readableBytes()];
            payload.getBytes(payload.readerIndex(), data);
            PacketDistributor.sendToServer(new WirelessAePackets.MEChamberConfiguratorActionPacket(
                    menu.containerId, address, kind, MEChamberConfigurator.ROOT_ACTION_ID, data));
        } finally {
            payload.release();
        }
    }
}
