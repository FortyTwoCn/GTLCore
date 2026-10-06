package org.gtlcore.gtlcore.mixin.gtm.cover;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.common.cover.PumpCover;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PumpCover.class)
public abstract class PumpCoverMixin extends CoverBehavior {
    @Shadow(remap = false) protected int transferRate;
    @Shadow(remap = false) protected int mBLeftToTransferLastSecond;
    @Shadow(remap = false) @Final public int maxFluidTransferRate;
    @Unique private long gtlcore$remainingFluidBudget;

    protected PumpCoverMixin(CoverDefinition definition, ICoverable holder, Direction side) {
        super(definition, holder, side);
    }

    // Includes fluid regulators, whose constructors call this native overload.
    @ModifyVariable(method = "<init>(Lcom/gregtechceu/gtceu/api/cover/CoverDefinition;Lcom/gregtechceu/gtceu/api/capability/ICoverable;Lnet/minecraft/core/Direction;II)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 1, remap = false, require = 1)
    private static int gtlcore$fluidRate(int nativeRate, CoverDefinition definition, ICoverable holder,
                                       Direction side, int tier, int requestedRate) {
        return tier == GTValues.ULV ? Integer.MAX_VALUE : 64 * (1 << (2 * Math.min(tier - 1, GTValues.IV)));
    }

    @Inject(method = "<init>(Lcom/gregtechceu/gtceu/api/cover/CoverDefinition;Lcom/gregtechceu/gtceu/api/capability/ICoverable;Lnet/minecraft/core/Direction;II)V",
            at = @At("RETURN"), remap = false, require = 1)
    private void gtlcore$initialBudget(CoverDefinition definition, ICoverable holder, Direction side,
                                     int tier, int requestedRate, CallbackInfo ci) {
        gtlcore$remainingFluidBudget = (long) transferRate * 20;
        mBLeftToTransferLastSecond = (int) Math.min(Integer.MAX_VALUE, gtlcore$remainingFluidBudget);
    }

    @WrapOperation(method = "update", at = @At(value = "INVOKE", target =
            "Lcom/gregtechceu/gtceu/common/cover/PumpCover;doTransferFluids(I)I"), remap = false, require = 1)
    private int gtlcore$transferUnlimitedBudget(PumpCover cover, int nativeLimit, Operation<Integer> transfer) {
        if (maxFluidTransferRate != Integer.MAX_VALUE) return transfer.call(cover, nativeLimit);
        // Preserve the original long budget through multiple native int-sized
        // transfers. Each call retains native filters, IO and regulator modes.
        long movedTotal = 0;
        while (gtlcore$remainingFluidBudget > 0) {
            int limit = (int) Math.min(Integer.MAX_VALUE, gtlcore$remainingFluidBudget);
            int moved = transfer.call(cover, limit);
            if (moved <= 0) break;
            gtlcore$remainingFluidBudget -= moved;
            movedTotal += moved;
            if (moved < limit) break;
        }
        return (int) Math.min(Integer.MAX_VALUE, movedTotal);
    }

    @Inject(method = "update", at = @At("RETURN"), remap = false, require = 1)
    private void gtlcore$refillBudget(CallbackInfo ci) {
        if (coverHolder.getOffsetTimer() % 20 == 0) {
            gtlcore$remainingFluidBudget = (long) transferRate * 20;
            mBLeftToTransferLastSecond = (int) Math.min(Integer.MAX_VALUE, gtlcore$remainingFluidBudget);
        } else if (maxFluidTransferRate == Integer.MAX_VALUE) {
            mBLeftToTransferLastSecond = (int) Math.min(Integer.MAX_VALUE, gtlcore$remainingFluidBudget);
        }
    }
}
