package org.gtlcore.gtlcore.mixin.gtm.client;
import com.gregtechceu.gtceu.client.mui.schema.MutableSchema;
import com.gregtechceu.gtceu.client.renderer.PatternPreviewRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = PatternPreviewRenderer.class, remap = false)
public interface WorldPreviewAccessor {
    @Accessor("schema") MutableSchema gtlcore$getNativeSchema();
}
