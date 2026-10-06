package org.gtlcore.gtlcore.mixin.gtm.machine;

import org.gtlcore.gtlcore.common.machine.multiblock.generator.GeneratorArrayMachine;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.WorkableTieredMachine;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorkableTieredMachine.class)
public abstract class WorkableTieredMachineMixin {
    @Inject(method = {
            "<init>(Lcom/gregtechceu/gtceu/api/blockentity/BlockEntityCreationInfo;ILcom/gregtechceu/gtceu/api/machine/trait/recipe/RecipeLogic;IIIIZLit/unimi/dsi/fastutil/ints/Int2IntFunction;)V",
            "<init>(Lcom/gregtechceu/gtceu/api/blockentity/BlockEntityCreationInfo;IZLcom/gregtechceu/gtceu/api/machine/trait/recipe/RecipeLogic;Lit/unimi/dsi/fastutil/ints/Int2IntFunction;)V"
    }, at = @At("TAIL"), remap = false, require = 2)
    private void gtlcore$nativeInventories(CallbackInfo ci) {
        var machine = (WorkableTieredMachine) (Object) this;
        machine.importItems.setFilter(stack -> !IntCircuitBehaviour.isIntegratedCircuit(stack));
        if (machine.energyContainer.getOutputVoltage() == 0) return;
        long voltage = GTValues.V[machine.getTier()]; int amperage = GeneratorArrayMachine.getAmperage(machine.getTier());
        if (amperage > 0) machine.energyContainer.resetBasicInfo(Math.multiplyExact(Math.multiplyExact(voltage, amperage), 64), 0, 0, voltage, amperage);
    }
}
