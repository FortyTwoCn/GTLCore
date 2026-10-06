package org.gtlcore.gtlcore.mixin.gtm.machine;

import com.gregtechceu.gtceu.common.machine.storage.QuantumChestMachine;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(QuantumChestMachine.class)
public interface QuantumChestMachineAccessor {

    @Accessor(value = "lockedItem", remap = false)
    CustomItemStackHandler gtlcore$getLockedInventory();
}
