package org.gtlcore.gtlcore.api.machine;

import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetThemeEntry;
import brachy.modularui.widgets.TextWidget;
import net.minecraft.network.chat.Component;

/** Keep the complete timing visible while fitting translated machine names on one bounded row. */
public final class PerformanceMonitorRowWidget extends TextWidget<PerformanceMonitorRowWidget> {
    private final int micros;

    public PerformanceMonitorRowWidget(PerformanceMonitorMachine.Row row) {
        super(Component.translatable(row.nameKey())); micros = row.micros();
    }

    @Override public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
        ClientDraw.draw(context, getKey(), micros, getArea().getPadding().left(), getArea().getPadding().top(),
                getArea().paddedWidth(), getArea().paddedHeight());
    }

    // Dedicated servers construct menus without resolving Minecraft's client font class.
    private static final class ClientDraw {
        private static void draw(ModularGuiContext context, Component name, int micros, int x, int y, int width, int height) {
            var font = net.minecraft.client.Minecraft.getInstance().font;
            String time = micros + " μs";
            int timeWidth = font.width(time), nameWidth = Math.max(0, width - timeWidth - 4);
            String label = name.getString();
            if (font.width(label) > nameWidth) {
                label = nameWidth >= font.width("…") ? font.plainSubstrByWidth(label, nameWidth - font.width("…")) + "…" : "";
            }
            int top = y + Math.max(0, (height - font.lineHeight) / 2);
            context.getGraphics().drawString(font, label, x, top, 0xFFFFFFFF, false);
            context.getGraphics().drawString(font, time, x + width - timeWidth, top, 0xFFFFFFFF, false);
        }
    }
}
