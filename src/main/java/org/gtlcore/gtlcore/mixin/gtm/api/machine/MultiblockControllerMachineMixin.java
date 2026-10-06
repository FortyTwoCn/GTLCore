package org.gtlcore.gtlcore.mixin.gtm.api.machine;

import org.gtlcore.gtlcore.api.pattern.WorldPatternTiming;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.multiblock.pattern.PatternState;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;

/** Instruments the native controller lifecycle without replacing its formation and part management. */
@Mixin(value = MultiblockControllerMachine.class, remap = false)
public abstract class MultiblockControllerMachineMixin {
    @WrapMethod(method = "checkStructurePattern")
    private PatternState gtlcore$timedCheck(String structure, Operation<PatternState> original) {
        var controller = (MultiblockControllerMachine) (Object) this;
        try (var timing = WorldPatternTiming.beginCheck(controller, "native_structure:" + structure)) {
            var state = original.call(structure);
            if (timing != null) timing.result(state.getState().isValid());
            return state;
        }
    }
}
