package org.gtlcore.gtlcore.mixin.gtm.machine;

import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(FluidHatchPartMachine.class)
public abstract class FluidHatchPartMachineMixin {
    /** @author GTLCore @reason Preserve original capacity without silent integer overflow. */
    @Overwrite(remap = false)
    public static int getTankCapacity(int initialCapacity, int tier) {
        return Math.toIntExact(Math.multiplyExact((long) initialCapacity, 1L << tier));
    }
}
