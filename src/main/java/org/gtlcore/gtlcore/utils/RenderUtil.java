package org.gtlcore.gtlcore.utils;

import org.gtlcore.gtlcore.client.GlobalRenderClock;
import org.gtlcore.gtlcore.config.ConfigHolder;
import org.gtlcore.gtlcore.mixin.mc.BufferBuilderAccessor;

import com.gregtechceu.gtceu.api.machine.MetaMachine;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.mojang.blaze3d.vertex.BufferBuilder;

@OnlyIn(Dist.CLIENT)
public class RenderUtil {

    private static final int MAX_BATCH_BYTES = 1536 * 1024 * 1024; // 1.5GB
    private static final int MAX_VERTICES_PER_BATCH = 2_097_152;

    public static int getBufferByteSize(BufferBuilder builder) {
        var access = (BufferBuilderAccessor) builder;
        return (int) Math.min(Integer.MAX_VALUE, (long) access.gtlcore$getVertexCount() * access.gtlcore$getVertexSize());
    }

    public static boolean shouldStartNewBatch(BufferBuilder builder, int currentVertexCount, int estimatedAdditionalVertices) {
        int byteSize = getBufferByteSize(builder);

        if (byteSize >= 0) {
            int estimatedBytes = estimatedAdditionalVertices * 40; // padding
            return byteSize + estimatedBytes > MAX_BATCH_BYTES;
        }

        // Fallback check: vertex count estimation
        return currentVertexCount + estimatedAdditionalVertices > MAX_VERTICES_PER_BATCH;
    }

    public static float getSmoothTick(MetaMachine machine, float partialTick) {
        return ConfigHolder.INSTANCE.enableSmoothAnimations ? GlobalRenderClock.getSmoothTick() : machine.getOffsetTimer() + partialTick;
    }
}
