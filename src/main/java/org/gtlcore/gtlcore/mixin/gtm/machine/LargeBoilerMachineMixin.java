package org.gtlcore.gtlcore.mixin.gtm.machine;

import org.gtlcore.gtlcore.api.recipe.NativeLargeBoilerFuel;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.common.machine.multiblock.steam.LargeBoilerMachine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LargeBoilerMachine.class, remap = false)
public abstract class LargeBoilerMachineMixin extends WorkableMultiblockMachine {
    @Shadow private int throttle;
    @Shadow protected abstract void setThrottle(int value);
    @Shadow protected abstract void updateSteamSubscription();
    protected LargeBoilerMachineMixin(BlockEntityCreationInfo info) { super(info); }

    @Inject(method = "recipeModifier", at = @At("HEAD"), cancellable = true, require = 1)
    private static void gtlcore$thermalFuel(MetaMachine machine, GTRecipe recipe, CallbackInfoReturnable<ModifierFunction> cir) {
        if (machine instanceof LargeBoilerMachine boiler) cir.setReturnValue(NativeLargeBoilerFuel.thermalModifier(boiler.getMaxTemperature()));
    }

    @ModifyVariable(method = "setThrottle", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 1)
    private int gtlcore$validThrottle(int value) { return NativeLargeBoilerFuel.throttle(value); }

    @Override public void onLoad() {
        super.onLoad();
        if (!isRemote()) {
            setThrottle(NativeLargeBoilerFuel.throttle(throttle));
            updateSteamSubscription();
        }
    }
}
