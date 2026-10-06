package org.gtlcore.gtlcore.common.machine.multiblock.noenergy;

import org.gtlcore.gtlcore.api.machine.multiblock.NoEnergyMultiblockMachine;
import org.gtlcore.gtlcore.api.recipe.CoreRecipeModifiers;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import net.minecraft.network.chat.Component;
import java.util.List;

/** Heat exchange keeps coolant in the recipe so search and output previews cannot consume it. */
public class HeatExchangerMachine extends NoEnergyMultiblockMachine {
    public HeatExchangerMachine(BlockEntityCreationInfo info, Object... args) { super(info); }
    public static ModifierFunction recipeModifier(MetaMachine machine, GTRecipe recipe) {
        if (!(machine instanceof HeatExchangerMachine)) return ModifierFunction.NULL;
        var fluids = recipe.getInputContents(FluidRecipeCapability.CAP);
        if (fluids.size() != 2) return ModifierFunction.NULL;
        boolean regularWater = FluidRecipeCapability.CAP.of(fluids.get(1).content()).test(GTMaterials.Water.getFluid(1));
        if (!regularWater) {
            var coolant = recipe.copy(); coolant.inputs.clear();
            coolant.inputs.put(FluidRecipeCapability.CAP, List.of(fluids.get(1)));
            if (!RecipeRunnerHelper.matchRecipeInput((HeatExchangerMachine) machine, coolant))
                return ModifierFunction.cancel(Component.translatable("gtceu.recipe.fail.no.enough.distilledwater"));
        }
        return source -> {
            var candidate = source.copy();
            if (!regularWater) candidate.duration = 200;
            return CoreRecipeModifiers.accurateParallel(machine, candidate, Integer.MAX_VALUE, false).getFirst();
        };
    }
}
