package org.gtlcore.gtlcore.mixin.gtmt;

import org.gtlcore.gtlcore.integration.gtmt.WirelessEnergyMonitorSnapshot;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.hepdd.gtmthings.api.misc.WirelessEnergyContainer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.UUID;

/** Record the actual accepted native transfer, including rate-limited partial transfers. */
@Mixin(WirelessEnergyContainer.class)
public abstract class WirelessEnergyManagerMixin {
    @Shadow(remap = false) @Final private UUID uuid;

    @Inject(method = "addEnergy", at = @At("RETURN"), remap = false, require = 1)
    private void gtlcore$recordInput(long requested, MetaMachine machine, CallbackInfoReturnable<Long> cir) {
        if (cir.getReturnValueJ() > 0) WirelessEnergyMonitorSnapshot.record(uuid, cir.getReturnValueJ(), machine);
    }

    @Inject(method = "removeEnergy", at = @At("RETURN"), remap = false, require = 1)
    private void gtlcore$recordOutput(long requested, MetaMachine machine, CallbackInfoReturnable<Long> cir) {
        if (cir.getReturnValueJ() > 0) WirelessEnergyMonitorSnapshot.record(uuid, -cir.getReturnValueJ(), machine);
    }
}
