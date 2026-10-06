package org.gtlcore.gtlcore.mixin.ae2.storage;

import org.gtlcore.gtlcore.integration.ae2.throughput.ThroughputMonitorStorageTracker;
import org.gtlcore.gtlcore.integration.ae2.throughput.ThroughputStorageView;

import appeng.api.storage.MEStorage;
import appeng.api.storage.SupplierStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.List;

/** Tracks the native dynamic storage wrapper used by AE2WTLib's wireless menu hosts. */
@Mixin(SupplierStorage.class)
public abstract class SupplierStorageMixin implements ThroughputStorageView {
    @Unique private MEStorage gtlcore$lastDelegate;
    @Unique private long gtlcore$topologyVersion;

    @Invoker(value = "getDelegate", remap = false)
    public abstract MEStorage gtlcore$getNativeDelegate();

    @Inject(method = "getDelegate", at = @At("RETURN"), remap = false, require = 1)
    private void gtlcore$refreshDelegate(CallbackInfoReturnable<MEStorage> callback) {
        MEStorage delegate = callback.getReturnValue();
        if (delegate != gtlcore$lastDelegate) {
            gtlcore$lastDelegate = delegate;
            gtlcore$topologyVersion++;
            ThroughputMonitorStorageTracker.onTopologyChanged((MEStorage) (Object) this);
        }
    }

    @Override
    public Collection<MEStorage> gtlcore$getChildStorages() {
        return List.of(gtlcore$getNativeDelegate());
    }

    @Override
    public long gtlcore$getTopologyVersion() {
        gtlcore$getNativeDelegate();
        return gtlcore$topologyVersion;
    }
}
