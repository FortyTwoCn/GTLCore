package org.gtlcore.gtlcore.common.machine.trait;

import org.gtlcore.gtlcore.common.machine.multiblock.generator.GeneratorArrayMachine;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.ActionResult;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.hepdd.gtmthings.api.misc.WirelessEnergyContainer;
import net.minecraft.network.chat.Component;

/** Wireless output joins the native recipe tick, including waiting/resumption and saved pending work. */
public final class GeneratorArrayRecipeLogic extends RecipeLogic {
    @SaveField private long pendingWirelessEU;

    @Override public ActionResult handleTickRecipe(GTRecipe recipe) {
        if (!recipe.data.contains(GeneratorArrayMachine.WIRELESS_EUT)) return super.handleTickRecipe(recipe);
        var account = GeneratorArrayMachine.accountFor(recipe);
        long energy = recipe.data.getLong(GeneratorArrayMachine.WIRELESS_EUT);
        if (energy <= 0 || account == null) return unavailable();
        if (pendingWirelessEU == 0) {
            if (com.hepdd.gtmthings.config.ConfigHolder.INSTANCE.isWirelessRateEnable && account.getRate() < energy) return unavailable();
            var nativeResult = super.handleTickRecipe(recipe);
            if (!nativeResult.isSuccess()) return nativeResult;
            pendingWirelessEU = energy;
        }
        long accepted = account.addEnergy(pendingWirelessEU, getMachine());
        pendingWirelessEU -= accepted;
        return pendingWirelessEU == 0 ? ActionResult.SUCCESS : unavailable();
    }
    public void clearPendingOutput() { pendingWirelessEU = 0; }
    private static ActionResult unavailable() {
        return ActionResult.fail(Component.translatable("gui.gtlcore.generator_array.wireless_unavailable"), EURecipeCapability.CAP, IO.OUT);
    }
}
