package org.gtlcore.gtlcore.mixin.gtm.machine;

import com.gregtechceu.gtceu.common.machine.multiblock.part.DualHatchPartMachine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(DualHatchPartMachine.class)
public abstract class DualHatchPartMachineMixin {
    /** @author GTLCore @reason Preserve original dual-hatch capacity in native fluid units. */
    @Overwrite(remap = false)
    public static int getTankCapacity(int initialCapacity, int tier) {
        return Math.toIntExact(Math.multiplyExact((long) initialCapacity, 1L << tier));
    }
    /** @author GTLCore @reason Preserve the original item and fluid slot counts. */
    @Overwrite(remap = false)
    public static int getDualHatchInventorySize(int tier) {
        return Math.multiplyExact(tier + 1, tier + 1);
    }
}
