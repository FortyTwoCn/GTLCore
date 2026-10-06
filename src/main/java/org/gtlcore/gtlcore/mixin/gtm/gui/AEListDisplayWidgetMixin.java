package org.gtlcore.gtlcore.mixin.gtm.gui;

import org.gtlcore.gtlcore.client.gui.widget.NativeAEWidgetIngredients;
import com.gregtechceu.gtceu.integration.ae2.gui.AEStackDisplayWidget;
import appeng.api.stacks.*;
import brachy.modularui.integration.recipeviewer.entry.EntryList;
import brachy.modularui.integration.recipeviewer.handlers.IngredientProvider;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = AEStackDisplayWidget.class, remap = false)
public abstract class AEListDisplayWidgetMixin implements IngredientProvider<Object> {
    @Shadow protected abstract GenericStack getStack();
    @Override public EntryList<Object> getIngredients() { return NativeAEWidgetIngredients.entries(getStack()); }
    @Override @SuppressWarnings({"unchecked", "rawtypes"}) public Class<Object> ingredientClass() {
        var stack = getStack(); return (Class) (stack != null && stack.what() instanceof AEFluidKey ? FluidStack.class : ItemStack.class);
    }
    @Override public Object ingredientOverride() { return NativeAEWidgetIngredients.clickable(getStack(), new Rect2i(getArea().x, getArea().y, 18, 18)); }
    @WrapOperation(method = "draw", at = @At(value = "INVOKE", target = "Lcom/gregtechceu/gtceu/integration/ae2/gui/AEGuiHelper;drawAmountOverlay(Lnet/minecraft/client/gui/GuiGraphics;JII)V"), require = 1)
    private void gtlcore$amount(GuiGraphics graphics, long amount, int x, int y, Operation<Void> original, @Local GenericStack stack) {
        NativeAEWidgetIngredients.drawAmount(graphics, stack, x, y);
    }
}
