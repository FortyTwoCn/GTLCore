package org.gtlcore.gtlcore.api.machine.trait.AECraft;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;

public interface IMolecularAssemblerHandler {

    void handleRecipeOutput(GTRecipe recipe);

    /** Keep the pending recipe when the output buffer cannot represent its full contents. */
    default boolean tryHandleRecipeOutput(GTRecipe recipe) {
        handleRecipeOutput(recipe);
        return true;
    }

    /**
     * extract from MolecularAssemblerSupportedPatterns, limited by parallelAmount
     */
    GTRecipe extractGTRecipe(long parallelAmount, int tickDuration);
}
