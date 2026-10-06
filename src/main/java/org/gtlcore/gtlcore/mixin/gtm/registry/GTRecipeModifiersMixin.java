package org.gtlcore.gtlcore.mixin.gtm.registry;

import org.gtlcore.gtlcore.api.recipe.*;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.CoilWorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.*;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GTRecipeModifiers.class)
public abstract class GTRecipeModifiersMixin {
    /** @author GTLCore @reason Preserve temperature speed-up and apply coil energy discounts after native overclock selection. */
    @Overwrite(remap = false)
    public static ModifierFunction ebfOverclock(MetaMachine machine, GTRecipe recipe) {
        if (!(machine instanceof CoilWorkableElectricMultiblockMachine coil)) return ModifierFunction.NULL;
        int temperature = coil.getCoilType().getCoilTemperature() + 100 * Math.max(0, coil.getTier() - GTValues.MV);
        int required = recipe.data.getInt("ebf_temp");
        if (!recipe.data.contains("ebf_temp") || required > temperature)
            return ModifierFunction.cancel(RecipeResult.FAIL_NO_ENOUGH_TEMPERATURE.reason());
        if (RecipeHelper.getRecipeEUtTier(recipe) > coil.getTier())
            return ModifierFunction.cancel(Component.translatable("gtceu.recipe_modifier.insufficient_voltage"));
        double speed = Math.max(.5, (double) required / temperature);
        double discount = Math.pow(.95, Math.max(0, (temperature - required) / OverclockingLogic.COIL_EUT_DISCOUNT_TEMPERATURE));
        return input -> {
            RecipeMultiplierTracker.captureReduction(machine, input, discount, speed);
            var reduced = ModifierFunction.builder().durationMultiplier(speed).build().apply(input);
            OverclockingLogic logic = (params, voltage) -> OverclockingLogic.heatingCoilOC(params, voltage, required, temperature);
            var result = logic.getModifier(machine, reduced, coil.getOverclockVoltage()).apply(reduced);
            return result == null ? null : ModifierFunction.builder().eutMultiplier(discount).build().apply(result);
        };
    }
    /** @author GTLCore @reason Route parallel hatch limits through the same native Core input selection as execution. */
    @Overwrite(remap = false)
    public static ModifierFunction hatchParallel(MetaMachine machine, GTRecipe recipe) { return CoreRecipeModifiers.hatchParallel(machine, recipe); }

    @Inject(method = "pyrolyseOvenOverclock", at = @At("HEAD"), remap = false, require = 1)
    private static void gtlcore$capturePyrolyseReduction(MetaMachine machine, GTRecipe recipe, CallbackInfoReturnable<ModifierFunction> cir) {
        if (machine instanceof CoilWorkableElectricMultiblockMachine coil) {
            int tier = coil.getCoilTier();
            RecipeMultiplierTracker.captureReduction(machine, recipe, 1, tier == 0 ? 4.0 / 3 : 2.0 / (tier + 1));
        }
    }
}
