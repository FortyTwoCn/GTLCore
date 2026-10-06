package org.gtlcore.gtlcore.mixin.mc;

import com.mojang.blaze3d.vertex.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BufferBuilder.class)
public interface BufferBuilderAccessor {

    @Accessor("vertices")
    int gtlcore$getVertexCount();

    @Accessor("vertexSize")
    int gtlcore$getVertexSize();

}
