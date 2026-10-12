package org.gtlcore.gtlcore.api.gui;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.lowdragmc.lowdraglib.integration.jei.ModularWrapper;
import brachy.modularui.api.widget.Interactable;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetThemeEntry;
import brachy.modularui.widget.Widget;
import brachy.modularui.integration.recipeviewer.handlers.IngredientProvider;
import brachy.modularui.integration.recipeviewer.entry.EntryList;
import brachy.modularui.integration.recipeviewer.entry.item.ItemStackList;
import brachy.modularui.integration.recipeviewer.RecipeSlotRole;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.Minecraft;
import java.util.Optional;
import org.gtlcore.gtlcore.client.preview.PreviewMaterialHighlights;

/** Embeds the production LDLib 1.21 scene and controls in GT's native ModularUI recipe-viewer API. */
public final class NativePatternPreviewWidget extends Widget<NativePatternPreviewWidget> implements Interactable, IngredientProvider<ItemStack> {
    private final PatternPreviewWidget preview;
    private final ModularWrapper<PatternPreviewWidget> wrapper;

    public NativePatternPreviewWidget(MultiblockMachineDefinition definition) {
        name("gtlcore_native_pattern_preview");
        preview = PatternPreviewWidget.getPatternWidget(definition);
        wrapper = new ModularWrapper<>(preview);
        wrapper.setRecipeWidget(0, 0); size(preview.getSizeWidth(), preview.getSizeHeight());
    }

    public PatternPreviewWidget getPreview() { return preview; }
    @Override public Class<ItemStack> ingredientClass() { return ItemStack.class; }
    @Override public RecipeSlotRole getRecipeRole() { return RecipeSlotRole.INPUT; }
    @Override public EntryList<ItemStack> getIngredients() {
        int x = getContext().getAbsMouseX() - getArea().x;
        int y = getContext().getAbsMouseY() - getArea().y;
        for (var slot : preview.getIngredientSlots()) {
            var bounds = PreviewMaterialHighlights.bounds(slot);
            if (bounds != null && bounds.contains(x, y)) return ItemStackList.of(slot.getItem().copy());
        }
        return ItemStackList.of(java.util.List.<ItemStack>of());
    }
    @Override public void onUpdate() { wrapper.updateScreen(); }
    @Override public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
        wrapper.draw(context.getGraphics(), context.getMouseX(), context.getMouseY(), context.getRenderPartialTicks());
    }
    @Override public void drawForeground(ModularGuiContext context) {
        // LDLib queues hover text while drawing the embedded scene. Its JEI wrapper
        // leaves tooltip rendering to the host, so draw that queue in MUI's foreground.
        if (wrapper.tooltipTexts == null || wrapper.tooltipTexts.isEmpty()) return;
        var graphics = context.getGraphics();
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(context.getMouseX() - context.getAbsMouseX(),
                    context.getMouseY() - context.getAbsMouseY(), 0);
            graphics.renderTooltip(wrapper.tooltipFont == null ? Minecraft.getInstance().font : wrapper.tooltipFont,
                    wrapper.tooltipTexts, Optional.ofNullable(wrapper.tooltipComponent),
                    context.getAbsMouseX(), context.getAbsMouseY());
        } finally {
            graphics.pose().popPose();
        }
    }
    @Override public Result onMousePressed(int button) {
        return preview.mouseClicked(getContext().getMouseX(), getContext().getMouseY(), button) ? Result.SUCCESS : Result.IGNORE;
    }
    @Override public boolean onMouseReleased(int button) {
        return preview.mouseReleased(getContext().getMouseX(), getContext().getMouseY(), button);
    }
    @Override public void onMouseDrag(int button, double dx, double dy) {
        preview.mouseDragged(getContext().getMouseX(), getContext().getMouseY(), button, dx, dy);
    }
    @Override public boolean onMouseScrolled(double dx, double dy) {
        return preview.mouseWheelMove(getContext().getMouseX(), getContext().getMouseY(), dx, dy);
    }
    @Override public Result onKeyPressed(int key, int scanCode, int modifiers) {
        return preview.keyPressed(key, scanCode, modifiers) ? Result.SUCCESS : Result.IGNORE;
    }
    @Override public boolean onKeyReleased(int key, int scanCode, int modifiers) { return preview.keyReleased(key, scanCode, modifiers); }
    @Override public Result onCharTyped(char character, int modifiers) {
        return preview.charTyped(character, modifiers) ? Result.SUCCESS : Result.IGNORE;
    }
    @Override public void dispose() {
        preview.getPreviewControls().scene().getRenderer().deleteCacheBuffer(); super.dispose();
    }
}
