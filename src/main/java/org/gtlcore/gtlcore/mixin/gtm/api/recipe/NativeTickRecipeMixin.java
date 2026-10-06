package org.gtlcore.gtlcore.mixin.gtm.api.recipe;

import org.gtlcore.gtlcore.api.machine.ISteamMachine;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.api.recipe.RecipeResult;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.ActionResult;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.utils.GTUtil;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Objects;

/** The native API moved tick matching from GTRecipe to RecipeHelper. */
@Mixin(RecipeHelper.class)
public abstract class NativeTickRecipeMixin {
    @Inject(method = "matchTickRecipe", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private static void gtlcore$voltageTier(IRecipeCapabilityHolder holder, GTRecipe recipe,
                                           CallbackInfoReturnable<ActionResult> cir) {
        if (!recipe.hasTick() || recipe.tickInputs.isEmpty() || recipe.getInputEUt().isEmpty() || !(holder instanceof WorkableElectricMultiblockMachine machine)) return;
        GTRecipe last = machine.getRecipeLogic().getLastOriginRecipe();
        if ((last == null || !Objects.equals(recipe.id, last.id)) &&
                IGTRecipe.of(recipe).getEuTier() > GTUtil.getFloorTierByVoltage(machine.getMaxVoltage())) {
            var failure = RecipeResult.FAIL_VOLTAGE_TIER;
            RecipeResult.of(machine, failure);
            RecipeLogic.putFailureReason(machine, recipe, failure.reason());
            cir.setReturnValue(ActionResult.fail(failure.reason(), EURecipeCapability.CAP, IO.IN));
        }
    }
    @Inject(method = "matchTickRecipe", at = @At("RETURN"), remap = false, require = 1)
    private static void gtlcore$failureStatus(IRecipeCapabilityHolder holder, GTRecipe recipe,
                                             CallbackInfoReturnable<ActionResult> cir) {
        if (!(holder instanceof IRecipeLogicMachine machine)) return;
        var result = cir.getReturnValue();
        if (result.isSuccess()) return;
        if (result.capability() == CWURecipeCapability.CAP && result.io() == IO.IN) {
            RecipeResult.of(machine, RecipeResult.FAIL_NO_ENOUGH_CWU_IN);
        } else if (result.capability() == EURecipeCapability.CAP) {
            if (result.io() == IO.IN) RecipeResult.of(machine, holder instanceof ISteamMachine ?
                    RecipeResult.fail(Component.translatable("gtceu.recipe.fail.steam.enough")) : RecipeResult.FAIL_NO_ENOUGH_EU_IN);
            else if (result.io() == IO.OUT) RecipeResult.of(machine, RecipeResult.FAIL_NO_ENOUGH_EU_OUT);
        }
    }
}
