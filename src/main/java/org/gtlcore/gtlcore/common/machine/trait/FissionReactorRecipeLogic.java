package org.gtlcore.gtlcore.common.machine.trait;

import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import java.util.Map;

/** A paid reactor cycle waits intact until its whole output can be delivered. */
public final class FissionReactorRecipeLogic extends RecipeLogic {
    @Override public void onRecipeFinish() {
        if (lastRecipe != null) {
            var output = RecipeRunnerHelper.handleRecipe(IO.OUT, getRLMachine(), lastRecipe.outputs,
                    Map.of(), false, lastRecipe, true);
            if (!output.isSuccess()) {
                // Native setStatus consumes a finish pause when entering WAITING. Keep it for delivery.
                boolean pause = suspendAfterFinish; suspendAfterFinish = false;
                try { setWaiting(output.reason()); }
                finally { suspendAfterFinish = pause; }
                return;
            }
        }
        super.onRecipeFinish();
    }
}
