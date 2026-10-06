package org.gtlcore.gtlcore.common.machine.trait;

import org.gtlcore.gtlcore.common.machine.multiblock.noenergy.NeutronActivatorMachine;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.ActionResult;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import net.minecraft.network.chat.Component;

/** Native waiting preserves in-flight work when neutron energy runs out. */
public final class NeutronActivatorRecipeLogic extends RecipeLogic {
    @Override
    public ActionResult handleTickRecipe(GTRecipe recipe) {
        var activator = (NeutronActivatorMachine) getMachine();
        if (activator.getEV() < activator.requiredEV(recipe)) {
            return ActionResult.fail(Component.translatable("gtceu.recipe.fail.no.enough.eV.consumed"), null, IO.IN);
        }
        var result = super.handleTickRecipe(recipe);
        if (!result.isSuccess()) return result;
        if (!activator.prepareWorkingTick()) {
            return ActionResult.fail(Component.translatable("gui.gtlcore.neutron_activator.working_blocked"), null, IO.NONE);
        }
        return ActionResult.SUCCESS;
    }
}
