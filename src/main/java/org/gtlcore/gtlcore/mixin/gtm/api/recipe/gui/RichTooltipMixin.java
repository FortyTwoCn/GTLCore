package org.gtlcore.gtlcore.mixin.gtm.api.recipe.gui;

import org.gtlcore.gtlcore.api.recipe.IRecipeTooltip;
import brachy.modularui.screen.RichTooltip;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import java.util.function.Consumer;

@Mixin(RichTooltip.class)
public abstract class RichTooltipMixin implements IRecipeTooltip {
    @Shadow private Consumer<RichTooltip> tooltipBuilder;
    @Unique private Consumer<RichTooltip> gtlcore$ingredientBuilder;
    @Unique private boolean gtlcore$capturedIngredientBuilder;
    @Unique private boolean gtlcore$hideClampedFluidAmount;

    @Override
    public void gtlcore$resetContentBuilders() {
        if (!gtlcore$capturedIngredientBuilder) {
            gtlcore$ingredientBuilder = tooltipBuilder;
            gtlcore$capturedIngredientBuilder = true;
        }
        tooltipBuilder = gtlcore$ingredientBuilder;
        gtlcore$hideClampedFluidAmount = false;
        ((RichTooltip) (Object) this).markDirty();
    }

    @Override public void gtlcore$hideClampedFluidAmount(boolean hide) { gtlcore$hideClampedFluidAmount = hide; }
    @Override public boolean gtlcore$hideClampedFluidAmount() { return gtlcore$hideClampedFluidAmount; }
}
