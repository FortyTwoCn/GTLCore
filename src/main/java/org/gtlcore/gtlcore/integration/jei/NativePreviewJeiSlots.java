package org.gtlcore.gtlcore.integration.jei;

import org.gtlcore.gtlcore.api.gui.NativePatternPreviewWidget;
import org.gtlcore.gtlcore.client.preview.PreviewMaterialHighlights;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import net.minecraft.client.gui.navigation.ScreenPosition;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.gui.widgets.ISlottedRecipeWidget;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeIngredientRole;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** JEI queries live LDLib slots, including replacements on page changes and newly selected predicates. */
public final class NativePreviewJeiSlots implements ISlottedRecipeWidget {
    private final NativePatternPreviewWidget widget;
    private final IGuiHelper guiHelper;
    private final Map<SlotWidget, SlotRecipeWidget> slots = new IdentityHashMap<>();

    public NativePreviewJeiSlots(NativePatternPreviewWidget widget, IGuiHelper guiHelper) {
        this.widget = widget;
        this.guiHelper = guiHelper;
    }

    public NativePatternPreviewWidget getWidget() { return widget; }

    @Override public ScreenPosition getPosition() {
        return new ScreenPosition(widget.getArea().x, widget.getArea().y);
    }

    @Override public Optional<RecipeSlotUnderMouse> getSlotUnderMouse(double mouseX, double mouseY) {
        var live = widget.getPreview().getIngredientSlots();
        slots.keySet().removeIf(slot -> !live.contains(slot));
        for (var slot : live) {
            var bounds = PreviewMaterialHighlights.bounds(slot);
            if (bounds == null || !bounds.contains((int) mouseX, (int) mouseY)) continue;
            var bridge = slots.computeIfAbsent(slot, source -> {
                var drawable = guiHelper.createRecipeSlotDrawable(RecipeIngredientRole.INPUT,
                        acceptor -> acceptor.addItemStack(source.getItem().copy()), Set.of(), 0);
                var result = new SlotRecipeWidget(source, drawable);
                PreviewMaterialHighlights.bind(source, drawable, widget);
                return result;
            });
            var origin = getPosition();
            return Optional.of(new RecipeSlotUnderMouse(bridge.new RecipeSlotDrawable(),
                    origin.x() + slot.getPositionX(), origin.y() + slot.getPositionY()));
        }
        return Optional.empty();
    }
}
