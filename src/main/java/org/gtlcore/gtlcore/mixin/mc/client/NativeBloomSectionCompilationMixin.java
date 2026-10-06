package org.gtlcore.gtlcore.mixin.mc.client;

import com.gregtechceu.gtceu.client.bloom.BloomRenderer;
import com.gregtechceu.gtceu.client.bloom.BloomShaderManager;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;

/** Keeps complete rebuilds of the same native bloom section from mixing their shared mesh. */
@Mixin(SectionCompiler.class)
public abstract class NativeBloomSectionCompilationMixin {
    @Unique private static final ReentrantLock[] gtlcore$sectionLocks =
            IntStream.range(0, 64).mapToObj(index -> new ReentrantLock()).toArray(ReentrantLock[]::new);

    @WrapMethod(method = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;")
    private SectionCompiler.Results gtlcore$serializeSectionRebuild(SectionPos section, RenderChunkRegion region,
            VertexSorting sorting, SectionBufferBuilderPack builders, List<?> additional,
            Operation<SectionCompiler.Results> original) {
        if (!BloomShaderManager.isBloomActive() || BloomRenderer.usesChunkPassBackend())
            return original.call(section, region, sorting, builders, additional);
        var lock = gtlcore$sectionLocks[Math.floorMod(section.hashCode(), gtlcore$sectionLocks.length)];
        lock.lock();
        try {
            return original.call(section, region, sorting, builders, additional);
        } finally {
            BloomRenderer.bloomChunkContext().close();
            lock.unlock();
        }
    }
}
