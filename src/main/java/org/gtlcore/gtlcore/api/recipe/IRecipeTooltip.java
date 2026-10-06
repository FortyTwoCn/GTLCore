package org.gtlcore.gtlcore.api.recipe;

/** Retains the recipe viewer's ingredient tooltip while replacing content-derived builders on refresh. */
public interface IRecipeTooltip {
    void gtlcore$resetContentBuilders();
    void gtlcore$hideClampedFluidAmount(boolean hide);
    boolean gtlcore$hideClampedFluidAmount();
}
