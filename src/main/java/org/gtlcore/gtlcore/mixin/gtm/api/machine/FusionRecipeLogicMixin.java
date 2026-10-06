package org.gtlcore.gtlcore.mixin.gtm.api.machine;

import org.gtlcore.gtlcore.api.machine.trait.IFusionHeatTransaction;
import org.gtlcore.gtlcore.api.recipe.NativeFusionStartup;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.ActionResult;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = RecipeLogic.class, remap = false)
public abstract class FusionRecipeLogicMixin {
    @Shadow public abstract IRecipeLogicMachine getRLMachine();

    @WrapOperation(method = "setupRecipe", at = @At(value = "INVOKE", target =
            "Lcom/gregtechceu/gtceu/api/machine/trait/recipe/RecipeLogic;handleRecipeIO(Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;Lcom/gregtechceu/gtceu/api/capability/recipe/IO;)Lcom/gregtechceu/gtceu/api/recipe/ActionResult;"), require = 1)
    private ActionResult gtlcore$payAcceptedFusionInputs(RecipeLogic logic, GTRecipe recipe, IO io,
                                                       Operation<ActionResult> original) {
        if (io == IO.IN && logic.getRLMachine().self() instanceof IFusionHeatTransaction fusion)
            return fusion.gtlcore$withFusionHeat(recipe, () -> original.call(logic, recipe, io));
        return original.call(logic, recipe, io);
    }

    @WrapMethod(method = "handleTickRecipe")
    private ActionResult gtlcore$recoverFusionHeatBeforeTickInputs(GTRecipe recipe, Operation<ActionResult> original) {
        if (!(getRLMachine().self() instanceof IFusionHeatTransaction fusion)) return original.call(recipe);
        long required = NativeFusionStartup.requiredHeat(recipe);
        if (required >= 0 && NativeFusionStartup.missingHeat(fusion.gtlcore$getFusionHeat(), required) > 0) {
            // Match before reserving; a blocked power/input/output tick does not make even a transient payment.
            var matched = RecipeHelper.matchTickRecipe(getRLMachine(), recipe);
            if (!matched.isSuccess()) return matched;
        }
        return fusion.gtlcore$withFusionHeat(recipe, () -> original.call(recipe));
    }
}
