package org.gtlcore.gtlcore.client.ae2.wireless;


import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import brachy.modularui.screen.EmbedHandler;
import brachy.modularui.screen.ModularScreen;
import org.jetbrains.annotations.Nullable;


/** A native ModularUI embed; the AE2 terminal remains the player's authoritative menu. */
final class MEChamberConfiguratorWidgetHost {
    private static final int CONTENT_PADDING = 6;
    private static final int HEADER_HEIGHT = 22;
    private static final int CLOSE_SIZE = 16;
    private static final int CLOSE_INSET = 3;
    private static final int DIM_COLOR = 0x88000000;
    private static final float OVERLAY_Z = 400.0F;
    private static final Component CLOSE_LABEL = Component.literal("x");

    private @Nullable MEChamberConfiguratorPanels.View view;
    private @Nullable ModularScreen widgetHost;

    void open(MEChamberConfiguratorPanels.View view, int screenWidth, int screenHeight) {
        close();
        this.view = view;
        this.widgetHost = ModularScreen.createEmbed("gtlcore", view.root(), screenWidth, screenHeight);
        var area = view.root().getArea();
        view.root().pos((screenWidth - area.width) / 2, (screenHeight - area.height + HEADER_HEIGHT) / 2);
        widgetHost.onResize(screenWidth, screenHeight);
    }

    void close() {
        if (widgetHost != null) {
            widgetHost.getPanelManager().closeAll();
            widgetHost.getPanelManager().dispose();
        }
        view = null;
        widgetHost = null;
    }

    boolean isOpen() {
        return view != null && widgetHost != null;
    }

    void render(GuiGraphics graphics, int screenWidth, int screenHeight, int mouseX, int mouseY,
                float partialTick) {
        if (view == null || widgetHost == null) return;
        var area = view.root().getArea();
        int panelLeft = area.x - CONTENT_PADDING;
        int panelTop = area.y - HEADER_HEIGHT;
        int panelWidth = area.width + CONTENT_PADDING * 2;
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, OVERLAY_Z);
        try {
            graphics.fill(0, 0, screenWidth, screenHeight, DIM_COLOR);
            WirelessAeStyle.drawInsetPanel(graphics, panelLeft, panelTop, panelWidth,
                    area.height + HEADER_HEIGHT + CONTENT_PADDING);
            WirelessAeStyle.drawTrimmedString(graphics, Minecraft.getInstance().font, view.title(),
                    panelLeft + CONTENT_PADDING, panelTop + CONTENT_PADDING,
                    panelWidth - CONTENT_PADDING * 2 - CLOSE_SIZE - CLOSE_INSET, WirelessAeStyle.TEXT);
            drawCloseButton(graphics, panelLeft, panelTop, panelWidth, mouseX, mouseY);
            graphics.flush();
            // Native embeds perform their own tick, hover, focus and tooltip updates.
            EmbedHandler.drawEmbed(widgetHost, graphics, mouseX, mouseY, partialTick);
        } finally {
            graphics.pose().popPose();
        }
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (view == null || widgetHost == null) return false;
        if (button == 0 && isInsideCloseButton(mouseX, mouseY)) {
            close();
            return true;
        }
        updateMouse(mouseX, mouseY);
        widgetHost.mousePressed(button);
        return true;
    }

    boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (widgetHost == null) return false;
        updateMouse(mouseX, mouseY);
        widgetHost.mouseDragged(button, dragX, dragY);
        return true;
    }

    boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (widgetHost == null) return false;
        updateMouse(mouseX, mouseY);
        widgetHost.mouseReleased(button);
        return true;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (widgetHost == null) return false;
        updateMouse(mouseX, mouseY);
        widgetHost.mouseScrolled(horizontal, vertical);
        return true;
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (widgetHost == null) return false;
        widgetHost.keyPressed(keyCode, scanCode, modifiers);
        return true;
    }

    boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (widgetHost == null) return false;
        widgetHost.keyReleased(keyCode, scanCode, modifiers);
        return true;
    }

    boolean charTyped(char codePoint, int modifiers) {
        if (widgetHost == null) return false;
        widgetHost.charTyped(codePoint, modifiers);
        return true;
    }

    private void updateMouse(double mouseX, double mouseY) {
        widgetHost.getContext().updateState((int) mouseX, (int) mouseY, 0);
        widgetHost.onFrameUpdate();
    }

    private void drawCloseButton(GuiGraphics graphics, int panelLeft, int panelTop, int panelWidth,
                                 int mouseX, int mouseY) {
        int closeX = panelLeft + panelWidth - CLOSE_INSET - CLOSE_SIZE;
        int closeY = panelTop + CLOSE_INSET;
        boolean hovered = isInside(mouseX, mouseY, closeX, closeY, CLOSE_SIZE, CLOSE_SIZE);
        WirelessAeStyle.drawButtonBackground(graphics, closeX, closeY, CLOSE_SIZE, CLOSE_SIZE,
                true, false, false, hovered);
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, CLOSE_LABEL, closeX + (CLOSE_SIZE - font.width(CLOSE_LABEL)) / 2,
                closeY + (CLOSE_SIZE - font.lineHeight) / 2, WirelessAeStyle.TEXT, false);
    }

    private boolean isInsideCloseButton(double mouseX, double mouseY) {
        var area = view.root().getArea();
        int closeX = area.x + area.width + CONTENT_PADDING - CLOSE_INSET - CLOSE_SIZE;
        int closeY = area.y - HEADER_HEIGHT + CLOSE_INSET;
        return isInside(mouseX, mouseY, closeX, closeY, CLOSE_SIZE, CLOSE_SIZE);
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    ModularScreen embeddedScreen() {
        return widgetHost;
    }
}
