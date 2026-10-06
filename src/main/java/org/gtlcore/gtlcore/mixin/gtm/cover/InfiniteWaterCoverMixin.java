package org.gtlcore.gtlcore.mixin.gtm.cover;

import com.gregtechceu.gtceu.common.cover.InfiniteWaterCover;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Keep native capability discovery and cover subscriptions; preserve Core's supply amount. */
@Mixin(InfiniteWaterCover.class)
public abstract class InfiniteWaterCoverMixin {
    @ModifyArg(method = "lambda$update$0", at = @At(value = "INVOKE", target =
            "Lnet/neoforged/neoforge/fluids/FluidStack;<init>(Lnet/minecraft/world/level/material/Fluid;I)V"),
            index = 1, remap = false, require = 1)
    private static int gtlcore$unlimitedWater(int nativeAmount) {
        return Integer.MAX_VALUE;
    }
}
