package org.gtlcore.gtlcore.client.preview;

import org.gtlcore.gtlcore.api.gui.PatternPreviewWidget;

import com.lowdragmc.lowdraglib.gui.ingredient.IRecipeIngredientSlot;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderType;

import mezz.jei.api.gui.ingredient.IRecipeSlotView;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/** JEI widget slots have local (0,0) bounds; AE asks for recipe-relative highlights. */
public final class PreviewMaterialHighlights {

    private static final Map<IRecipeSlotView, Binding> SLOTS = new WeakHashMap<>();
    private record Binding(WeakReference<Widget> slot, WeakReference<brachy.modularui.api.widget.IWidget> origin) {}

    private PreviewMaterialHighlights() {}

    public static void bind(IRecipeIngredientSlot ingredient, IRecipeSlotView jeiSlot) {
        bind(ingredient, jeiSlot, null);
    }

    public static void bind(IRecipeIngredientSlot ingredient, IRecipeSlotView jeiSlot,
                            brachy.modularui.api.widget.IWidget origin) {
        if (ingredient instanceof SlotWidget slot && previewParent(slot) != null) {
            SLOTS.put(jeiSlot, new Binding(new WeakReference<>(slot), new WeakReference<>(origin)));
        }
    }

    private static PatternPreviewWidget previewParent(Widget slot) {
        for (Widget current = slot; current != null; current = current.getParent()) {
            if (current instanceof PatternPreviewWidget preview) return preview;
        }
        return null;
    }

    /** Live coordinates include scrolling. Detached/hidden slots must not leave ghost highlights. */
    public static Rect2i bounds(Widget slot) {
        if (slot == null) return null;
        int left = slot.getPositionX() + 1, top = slot.getPositionY() + 1;
        int right = left + Math.max(0, slot.getSizeWidth() - 2);
        int bottom = top + Math.max(0, slot.getSizeHeight() - 2);
        for (Widget current = slot; current != null; current = current.getParent()) {
            if (!current.isVisible()) return null;
            if (current instanceof DraggableScrollableWidgetGroup scroll && scroll.isUseScissor()) {
                left = Math.max(left, scroll.getPositionX());
                top = Math.max(top, scroll.getPositionY());
                right = Math.min(right, scroll.getPositionX() + scroll.getSizeWidth());
                bottom = Math.min(bottom, scroll.getPositionY() + scroll.getSizeHeight());
            }
            if (current instanceof PatternPreviewWidget) {
                return right > left && bottom > top ? new Rect2i(left, top, right - left, bottom - top) : null;
            }
            var parent = current.getParent();
            if (parent == null || !parent.widgets.contains(current)) return null;
        }
        return null;
    }

    public static void draw(IRecipeSlotView jeiSlot, GuiGraphics graphics, int color) {
        var reference = SLOTS.get(jeiSlot);
        if (reference == null) {
            jeiSlot.drawHighlight(graphics, color);
            return;
        }
        var bounds = bounds(reference.slot().get());
        if (bounds != null) {
            var origin = reference.origin().get();
            int x = bounds.getX() + (origin == null ? 0 : origin.getArea().x);
            int y = bounds.getY() + (origin == null ? 0 : origin.getArea().y);
            graphics.fillGradient(RenderType.guiOverlay(), x, y,
                    x + bounds.getWidth(), y + bounds.getHeight(), color, color, 0);
        }
    }
}
