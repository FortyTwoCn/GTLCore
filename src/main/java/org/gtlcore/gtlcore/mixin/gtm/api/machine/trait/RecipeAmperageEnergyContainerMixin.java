package org.gtlcore.gtlcore.mixin.gtm.api.machine.trait;

import com.gregtechceu.gtceu.api.machine.SimpleGeneratorMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.RecipeAmperageEnergyContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Core generators output their configured amperage instead of the recipe stack's default single amp. */
@Mixin(RecipeAmperageEnergyContainer.class)
public abstract class RecipeAmperageEnergyContainerMixin extends NotifiableEnergyContainer {
    public RecipeAmperageEnergyContainerMixin(long capacity, long inputVoltage, long inputAmps, long outputVoltage, long outputAmps) {
        super(capacity, inputVoltage, inputAmps, outputVoltage, outputAmps);
    }
    @Inject(method = "getOutputAmperage", at = @At("HEAD"), remap = false, cancellable = true, require = 1)
    private void gtlcore$generatorOutput(CallbackInfoReturnable<Long> cir) {
        if (getMachine() instanceof SimpleGeneratorMachine) cir.setReturnValue(super.getOutputAmperage());
    }
}
