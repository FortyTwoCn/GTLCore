package org.gtlcore.gtlcore.common.machine.trait;

import org.gtlcore.gtlcore.common.machine.multiblock.generator.GTLLargeCombustionEngineMachine;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.ActionResult;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;

/** Auxiliary fluids join native tick matching, so blocked output cannot consume lubricant or boosters. */
public final class CombustionEngineRecipeLogic extends RecipeLogic {
    @SaveField private int acceptedTicks;
    @Override public ActionResult handleTickRecipe(GTRecipe recipe) {
        var engine = (GTLLargeCombustionEngineMachine) getMachine();
        if (engine.isIntakesObstructed()) return ActionResult.fail(Component.translatable("gtceu.multiblock.large_combustion_engine.obstructed"), null, IO.NONE);
        int next = acceptedTicks + 1;
        GTRecipe tickRecipe = recipe;
        GTRecipe displayed = lastUnrolledRecipe == null ? recipe : lastUnrolledRecipe;
        if (next == 1 || next % 72 == 0) {
            tickRecipe = recipe.copy();
            var inputs = new ArrayList<>(tickRecipe.getTickInputContents(FluidRecipeCapability.CAP));
            inputs.addAll(engine.getLubricantRecipe().getInputContents(FluidRecipeCapability.CAP));
            tickRecipe.tickInputs.put(FluidRecipeCapability.CAP, inputs);
            displayed = displayed.copy();
            var displayedInputs = new ArrayList<>(displayed.getTickInputContents(FluidRecipeCapability.CAP));
            displayedInputs.addAll(engine.getLubricantRecipe().getInputContents(FluidRecipeCapability.CAP));
            displayed.tickInputs.put(FluidRecipeCapability.CAP, displayedInputs);
        }
        var result = RecipeHelper.matchTickRecipe(getRLMachine(), tickRecipe);
        if (!result.isSuccess()) return result;
        // Native prerolls read the display recipe's original tick ingredients. Include this tick's lubricant
        // in that copy as well; adding it only to the working recipe would validate it without consuming it.
        var running = RecipeHelper.doTickPrerolls(tickRecipe, chanceCaches, displayed);
        result = handleTickRecipeIO(running, IO.IN);
        if (!result.isSuccess()) return result;
        result = handleTickRecipeIO(running, IO.OUT);
        if (result.isSuccess()) {
            acceptedTicks = next == 72000 ? 0 : next;
            if (next == 1 || next % 20 == 0) engine.updateBoost();
        }
        return result;
    }
    @Override public void resetRecipeLogic() { acceptedTicks = 0; super.resetRecipeLogic(); }
}
