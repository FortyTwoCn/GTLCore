package org.gtlcore.gtlcore.mixin.gtm.client;

import org.gtlcore.gtlcore.api.recipe.IRecipeTooltip;
import brachy.modularui.integration.jei.JeiRecipeViewerSlot;
import brachy.modularui.integration.recipeviewer.RecipeViewerSlotWidget;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.ArrayList;

/** The native JEI FluidStack carries an int; the Core content tooltip already supplies its exact long amount. */
@Mixin(JeiRecipeViewerSlot.class)
public abstract class JeiRecipeViewerSlotMixin {
    @Redirect(method = "lambda$new$3", at = @At(value = "INVOKE", target =
            "Lmezz/jei/api/gui/ingredient/IRecipeSlotDrawable;getTooltip(Lmezz/jei/api/gui/builder/ITooltipBuilder;)V"),
            remap = false, require = 1, expect = 1, allow = 1)
    private void gtlcore$exactFluidAmount(IRecipeSlotDrawable slot, ITooltipBuilder tooltip) {
        slot.getTooltip(tooltip);
        var widget = (RecipeViewerSlotWidget<?, ?>) (Object) this;
        if (!((IRecipeTooltip) (Object) widget.tooltip()).gtlcore$hideClampedFluidAmount()) return;
        var clamped = new ArrayList<Component>();
        for (var line : tooltip.getLines()) line.left().ifPresent(text -> {
            if (text instanceof Component component && component.getContents() instanceof TranslatableContents translated &&
                    translated.getKey().equals("jei.tooltip.liquid.amount")) clamped.add(component);
        });
        tooltip.removeAll(clamped);
    }
}
