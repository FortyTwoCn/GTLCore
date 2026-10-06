package org.gtlcore.gtlcore.api.machine.trait;

import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.api.recipe.ActionResult;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import java.util.function.Supplier;

/** Heat payment belongs to accepted native recipe IO, rather than candidate modification. */
public interface IFusionHeatTransaction {
    long gtlcore$getFusionHeat();
    NotifiableEnergyContainer gtlcore$getFusionEnergy();
    ActionResult gtlcore$withFusionHeat(GTRecipe recipe, Supplier<ActionResult> operation);
}
