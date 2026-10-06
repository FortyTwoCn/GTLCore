package org.gtlcore.gtlcore.mixin.gtm.api.machine;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.sync_system.managed.ManagedSyncBlockEntity;
import com.lowdragmc.lowdraglib.async.AsyncThreadData;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep asynchronous machine changes on the server thread using the actual native dirty entrypoint. */
@Mixin(value = ManagedSyncBlockEntity.class, remap = false)
public abstract class NativeMachineDirtyMixin {
    @Inject(method = "markAsChanged", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtlcore$queueAsyncMachineChange(CallbackInfo ci) {
        if (!((Object) this instanceof MetaMachine machine) || !(machine.getLevel() instanceof ServerLevel level)
                || level.getServer().isSameThread() || !AsyncThreadData.isThreadService()) return;
        ci.cancel();
        level.getServer().execute(() -> {
            if (!machine.isRemoved() && level.getBlockEntity(machine.getBlockPos()) == machine) machine.markAsChanged();
        });
    }
}
