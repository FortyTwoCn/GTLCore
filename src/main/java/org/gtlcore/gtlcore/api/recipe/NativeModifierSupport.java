package org.gtlcore.gtlcore.api.recipe;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;

/** Keeps native ModifierFunction copies consistent with Core's exact long execution counts. */
public final class NativeModifierSupport {
    private NativeModifierSupport() {}
    public static GTRecipe apply(ModifierFunction function, GTRecipe recipe, int parallels, int subticks, int batches) {
        long total;
        try {
            total = Math.multiplyExact(Math.multiplyExact(Math.multiplyExact(IGTRecipe.of(recipe).getRealParallels(), parallels), subticks), batches);
        } catch (ArithmeticException overflow) { return null; }
        var result = function.apply(recipe);
        if (result != null) {
            // Native fields retain their individual factors; overflow only affects their bounded display values.
            result.parallels = boundedProduct(recipe.parallels, parallels);
            result.subtickParallels = boundedProduct(recipe.subtickParallels, subticks);
            result.batchParallels = boundedProduct(recipe.batchParallels, batches);
            NativeRecipeState.nativeParallels(result, total);
        }
        return result;
    }
    private static int boundedProduct(int a, int b) { return (int) Math.min(Integer.MAX_VALUE, (long) Math.max(1, a) * Math.max(1, b)); }
}
