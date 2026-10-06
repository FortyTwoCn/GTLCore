package org.gtlcore.gtlcore.mixin.gtm.gui;

import org.gtlcore.gtlcore.client.gui.widget.NativeAEWidgetIngredients;
import com.gregtechceu.gtceu.integration.ae2.gui.AEConfigWidget;
import appeng.api.stacks.GenericStack;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = AEConfigWidget.class, remap = false)
public abstract class AEFluidConfigSlotWidgetMixin {
    @WrapOperation(method = "draw", at = @At(value = "INVOKE", target = "Lcom/gregtechceu/gtceu/integration/ae2/gui/AEGuiHelper;drawAmountOverlay(Lnet/minecraft/client/gui/GuiGraphics;JII)V", ordinal = 0), require = 1)
    private void gtlcore$configAmount(GuiGraphics graphics, long amount, int x, int y, Operation<Void> original, @Local(ordinal = 0) GenericStack stack) {
        NativeAEWidgetIngredients.drawAmount(graphics, stack, x, y);
    }
    @WrapOperation(method = "draw", at = @At(value = "INVOKE", target = "Lcom/gregtechceu/gtceu/integration/ae2/gui/AEGuiHelper;drawAmountOverlay(Lnet/minecraft/client/gui/GuiGraphics;JII)V", ordinal = 1), require = 1)
    private void gtlcore$stockAmount(GuiGraphics graphics, long amount, int x, int y, Operation<Void> original, @Local(ordinal = 1) GenericStack stack) {
        NativeAEWidgetIngredients.drawAmount(graphics, stack, x, y);
    }
}
