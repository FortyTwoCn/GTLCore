package org.gtlcore.gtlcore.mixin.gtm.ae.slot;

import org.gtlcore.gtlcore.integration.ae2.gui.NativeAEConfigSnapshot;
import com.gregtechceu.gtceu.integration.ae2.gui.AEConfigSyncHandler;
import com.gregtechceu.gtceu.integration.ae2.slot.IConfigurableSlotList;
import appeng.api.stacks.GenericStack;
import brachy.modularui.value.sync.SyncHandler;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AEConfigSyncHandler.class, remap = false)
public abstract class AEConfigSyncHandlerMixin extends SyncHandler<AEConfigSyncHandler> {
    @Shadow @Final private IConfigurableSlotList slotList;
    @Shadow @Final private GenericStack[] cachedConfig;
    @Shadow @Final private GenericStack[] cachedStock;
    @Inject(method = "detectAndSendChanges", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtlcore$immutableDelta(boolean init, CallbackInfo ci) {
        var snapshot = NativeAEConfigSnapshot.capture(slotList, cachedConfig, cachedStock, init);
        if (snapshot.hasChanges()) {
            // Advance on the server thread. Deferred/repeated encoding only reads the captured values.
            snapshot.updateCache(cachedConfig, cachedStock);
            syncToClient(1, snapshot::write);
        }
        ci.cancel();
    }
}
