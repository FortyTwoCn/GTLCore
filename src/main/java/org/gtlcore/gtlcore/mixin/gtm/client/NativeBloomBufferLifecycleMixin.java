package org.gtlcore.gtlcore.mixin.gtm.client;

import com.gregtechceu.gtceu.client.bloom.BloomRenderer;
import com.gregtechceu.gtceu.core.mixins.client.bloom.BufferBuilderAccessor;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Native empty builds finish the builder too; it must be removed before the next rebuild. */
@Mixin(value = BloomRenderer.SafeMode.class, remap = false)
public abstract class NativeBloomBufferLifecycleMixin {
    @WrapMethod(method = "getOrStartBloomBuffer")
    private static BufferBuilder gtlcore$discardFinishedBuffer(SectionPos section, Operation<BufferBuilder> original) {
        var lock = NativeBloomLockAccessor.gtlcore$getBloomLock().writeLock();
        lock.lock();
        try {
            gtlcore$clearFinished(section);
            return original.call(section);
        } finally {
            lock.unlock();
        }
    }

    @WrapMethod(method = "bakeBloomChunkBuffers")
    private static void gtlcore$protectNativeBake(SectionPos section, VertexSorting sorting, Operation<Void> original) {
        var lock = NativeBloomLockAccessor.gtlcore$getBloomLock().writeLock();
        lock.lock();
        try {
            original.call(section, sorting);
            gtlcore$clearFinished(section);
        } finally {
            lock.unlock();
        }
    }

    @Unique
    private static void gtlcore$clearFinished(SectionPos section) {
        var builder = BloomRenderer.SafeMode.BLOOM_BUFFER_BUILDERS.get(section);
        if (builder != null && !((BufferBuilderAccessor) builder).isBuilding()
                && BloomRenderer.SafeMode.BLOOM_BUFFER_BUILDERS.remove(section, builder)) {
            var storage = BloomRenderer.SafeMode.BLOOM_BYTE_BUFFERS.remove(section);
            if (storage != null) storage.close();
        }
    }
}
