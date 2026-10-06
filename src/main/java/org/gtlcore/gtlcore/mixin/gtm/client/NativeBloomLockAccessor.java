package org.gtlcore.gtlcore.mixin.gtm.client;

import com.gregtechceu.gtceu.client.bloom.BloomRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.locks.ReadWriteLock;

@Mixin(value = BloomRenderer.class, remap = false)
public interface NativeBloomLockAccessor {
    @Accessor("BLOOM_RENDER_LOCK")
    static ReadWriteLock gtlcore$getBloomLock() {
        throw new AssertionError("Native bloom accessor was not transformed");
    }
}
