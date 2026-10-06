package org.gtlcore.gtlcore.mixin.gtm.api.machine;

import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.multiblock.pattern.PatternState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** An invalidated structure must rediscover current block entities before binding its parts again. */
@Mixin(MultiblockControllerMachine.class)
public abstract class NativeMultiblockLifecycleMixin {
    @Inject(method = "invalidateStructure(Ljava/lang/String;)V", at = @At("TAIL"), remap = false, require = 1)
    private void gtlcore$discardInvalidatedPartCache(String name, CallbackInfo ci) {
        var state = ((MultiblockControllerMachine) (Object) this).getPatternState(name);
        if (state != null) {
            state.getCache().clear();
            state.setState(PatternState.CheckState.UNINITIALIZED);
        }
    }
}
