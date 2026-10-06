package org.gtlcore.gtlcore.mixin.gtm.machine;

import org.gtlcore.gtlcore.api.machine.trait.ILongItemStorage;
import com.gregtechceu.gtceu.common.machine.storage.QuantumChestMachine;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Keeps native voiding insertion from overflowing a full MAX-tier chest. */
@Mixin(targets = "com.gregtechceu.gtceu.common.machine.storage.QuantumChestMachine$ItemCache", remap = false)
public abstract class QuantumChestItemCacheMixin implements ILongItemStorage {
    @Shadow @Final QuantumChestMachine this$0;

    @WrapOperation(method = "insertItem", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(JJ)J", ordinal = 1))
    private long gtlcore$saturateVoiding(long capacity, long amount, Operation<Long> original) {
        return amount < 0 ? capacity : original.call(capacity, amount);
    }

    public long gtlcore$getStoredAmount(int slot) { return slot == 0 ? this$0.getStoredAmount() : 0; }
}
