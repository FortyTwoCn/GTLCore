package org.gtlcore.gtlcore.mixin.gtm.api.machine.trait;

import org.gtlcore.gtlcore.api.machine.trait.SteamEnergyConversion;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.steam.SteamEnergyRecipeHandler;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.ingredient.EnergyStack;
import org.spongepowered.asm.mixin.*;
import java.util.List;

@Mixin(SteamEnergyRecipeHandler.class)
public abstract class SteamEnergyRecipeHandlerMixin {
    @Shadow(remap = false) @Final private NotifiableFluidTank steamTank;
    @Shadow(remap = false) @Final private double conversionRate;

    /** @author GTLCore @reason Preserve exact steam requirements and native mB/EU units. */
    @Overwrite(remap = false)
    public List<EnergyStack> handleRecipeInner(IO io, GTRecipe recipe, List<EnergyStack> left, boolean simulate) {
        return SteamEnergyConversion.handle(steamTank, SteamEnergyConversion.Rate.of(conversionRate), io, recipe, left, simulate);
    }

    /** @author GTLCore @reason Parallel budgets must count usable EU, not multiply steam by mB/EU. */
    @Overwrite(remap = false)
    public List<Object> getContents() { return List.of(SteamEnergyConversion.availableEU(steamTank, SteamEnergyConversion.Rate.of(conversionRate))); }

    /** @author GTLCore @reason Use the same exact conversion as recipe consumption. */
    @Overwrite(remap = false)
    public double getTotalContentAmount() { return SteamEnergyConversion.availableEU(steamTank, SteamEnergyConversion.Rate.of(conversionRate)); }
}
