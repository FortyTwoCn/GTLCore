package org.gtlcore.gtlcore.mixin.gtm.machine;

import com.gregtechceu.gtceu.common.machine.multiblock.part.hpca.HPCAComponentPartMachine;
import com.gregtechceu.gtceu.common.machine.trait.hpca.HPCAComputationProviderTrait;
import com.gregtechceu.gtceu.common.machine.trait.hpca.HPCAComponentTrait;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import brachy.modularui.api.drawable.IDrawable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native traits own computation and damage state; configure the production factory once. */
@Mixin(HPCAComponentPartMachine.class)
public abstract class HPCAComputationPartMachineMixin {
    @Inject(method = "<init>", at = @At("TAIL"), remap = false, require = 1)
    private void gtlcore$persistentComponent(BlockEntityCreationInfo info, boolean advanced, IDrawable icon,
                                             IDrawable damagedIcon, HPCAComponentTrait trait, CallbackInfo ci) {
        ((HPCAComponentPartMachine) (Object) this).getTraitHolder().registerPersistentTrait("gtlcore_hpca_component", trait);
    }

    @Inject(method = "createHPCAComputationTrait", at = @At("RETURN"), remap = false, require = 1)
    private static void gtlcore$computationCapacity(boolean advanced, CallbackInfoReturnable<HPCAComputationProviderTrait> cir) {
        cir.getReturnValue().setCWUPerTick(advanced ? 32 : 8);
    }
}
