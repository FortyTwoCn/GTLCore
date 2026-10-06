package org.gtlcore.gtlcore.mixin.gtm.client;

import com.gregtechceu.gtceu.client.bloom.BloomRenderer;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;

import java.util.function.Consumer;

@Mixin(value = BloomRenderer.class, remap = false)
public abstract class NativeBloomQuadMixin {
    @WrapMethod(method = "copyBloomQuad")
    private static void gtlcore$protectQuadWrite(BakedQuad quad, int[] lights, RenderType type,
                                                Consumer<VertexConsumer> draw, Operation<Void> original) {
        if (BloomRenderer.usesChunkPassBackend() || BloomRenderer.bloomChunkContext().getValue() == null) {
            original.call(quad, lights, type, draw);
            return;
        }
        var lock = NativeBloomLockAccessor.gtlcore$getBloomLock().writeLock();
        lock.lock();
        try {
            original.call(quad, lights, type, draw);
        } finally {
            lock.unlock();
        }
    }
}
