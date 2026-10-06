package org.gtlcore.gtlcore.mixin.ae2.integration;

import org.gtlcore.gtlcore.client.gui.EncodePatternErrorModify;
import org.gtlcore.gtlcore.client.preview.PreviewMaterialHighlights;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "tamaized.ae2jeiintegration.integration.modules.jei.transfer.EncodePatternTransferHandler$ErrorRenderer", remap = false)
public class EncodePatternErrorRendererMixin implements EncodePatternErrorModify {

    @Unique
    private boolean gTLCore$virtualIngredientHint;

    @Redirect(method = "showError",
              at = @At(value = "INVOKE",
                       target = "Lmezz/jei/api/gui/ingredient/IRecipeSlotView;drawHighlight(Lnet/minecraft/client/gui/GuiGraphics;I)V"),
              require = 1)
    private void gTLCore$highlightMaterial(IRecipeSlotView slot, GuiGraphics graphics, int color) {
        PreviewMaterialHighlights.draw(slot, graphics, color);
    }

    @Override
    public void gTLCore$setVirtualIngredientHint(boolean hint) {
        this.gTLCore$virtualIngredientHint = hint;
    }

    @Inject(method = "getTooltip", at = @At("TAIL"), require = 1)
    private void gTLCore$appendVirtualIngredientHint(ITooltipBuilder tooltip, CallbackInfo ci) {
        if (gTLCore$virtualIngredientHint) {
            tooltip.add(Component.translatable("gtlcore.jei.virtual_ingredient_hint").withStyle(ChatFormatting.AQUA));
        }
    }
}
