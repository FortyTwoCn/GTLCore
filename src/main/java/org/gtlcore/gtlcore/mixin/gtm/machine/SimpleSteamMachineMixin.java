package org.gtlcore.gtlcore.mixin.gtm.machine;

import org.gtlcore.gtlcore.api.machine.ISteamMachine;
import org.gtlcore.gtlcore.api.recipe.RecipeResult;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.steam.SimpleSteamMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.common.recipe.condition.VentCondition;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(SimpleSteamMachine.class)
public abstract class SimpleSteamMachineMixin implements ISteamMachine {
    /** @author GTLCore @reason Preserve pressure, venting and failure reporting using native modifier functions. */
    @Overwrite(remap = false)
    public static ModifierFunction recipeModifier(MetaMachine machine, GTRecipe recipe) {
        if (!(machine instanceof SimpleSteamMachine steam)) return ModifierFunction.NULL;
        if (RecipeHelper.getRecipeEUtTier(recipe) > 1) return ModifierFunction.cancel(RecipeResult.FAIL_VOLTAGE_TIER.reason());
        if (!steam.getExhaustVentTrait().checkVenting()) return ModifierFunction.cancel(Component.translatable("gtceu.recipe.fail.no.venting"));
        var builder = ModifierFunction.builder().conditions(VentCondition.INSTANCE);
        // Native high pressure already consumes 2 mB/EU. Doubling EU as well would double the old steam cost.
        if (!steam.isHighPressure()) builder.durationMultiplier(2);
        return builder.build();
    }
    @Override public double getSteamPerEU() { return ((SimpleSteamMachine) (Object) this).getConversionRate(); }
}
