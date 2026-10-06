package org.gtlcore.gtlcore.mixin.gtm.machine;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ParallelHatchPartMachine;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParallelHatchPartMachine.class)
public abstract class ParallelHatchPartMachineMixin {

    @Mutable
    @Shadow(remap = false)
    @Final
    private int maxParallel;

    @Shadow(remap = false)
    private int currentParallel;

    @Inject(method = "<init>(Lcom/gregtechceu/gtceu/api/blockentity/BlockEntityCreationInfo;I)V", at = @At("RETURN"), remap = false, require = 1)
    public void ParallelHatchPartMachine(BlockEntityCreationInfo holder, int tier, CallbackInfo ci) {
        this.maxParallel = Math.max(1, (int) Math.pow(4, tier - 2));
        this.currentParallel = maxParallel;
    }

    @Inject(method = "getCurrentParallel", at = @At("HEAD"), remap = false, cancellable = true, require = 1)
    private void getCurrentParallelInj(CallbackInfoReturnable<Integer> cir) {
        if (currentParallel == 0) cir.setReturnValue(maxParallel);
    }

    @Inject(method = "canShared(Lcom/gregtechceu/gtceu/api/machine/multiblock/MultiblockControllerMachine;Ljava/lang/String;)Z", at = @At("HEAD"), remap = false, cancellable = true, require = 1)
    public void canShared(MultiblockControllerMachine controller, String structure, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }
}
