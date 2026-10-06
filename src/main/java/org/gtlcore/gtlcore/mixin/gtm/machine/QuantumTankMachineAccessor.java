package org.gtlcore.gtlcore.mixin.gtm.machine;

import com.gregtechceu.gtceu.common.machine.storage.QuantumTankMachine;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = QuantumTankMachine.class, remap = false)
public interface QuantumTankMachineAccessor {
    @Invoker("setLocked") void gtlcore$setLocked(boolean locked);
    @Invoker("setLocked") void gtlcore$setLockedFluid(FluidStack fluid);
}
