package org.gtlcore.gtlcore.mixin.gtm.client;
import org.gtlcore.gtlcore.client.preview.WorldPreview;
import com.gregtechceu.gtceu.client.mui.schema.MutableSchema;
import com.gregtechceu.gtceu.client.renderer.PatternPreviewRenderer;
import brachy.modularui.drawable.schema.RenderFilter;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PatternPreviewRenderer.class, remap = false)
public abstract class WorldPreviewPerformanceMixin {
    @Shadow private BlockPos controllerPos;
    @Shadow private MutableSchema schema;
    @Shadow @Final private java.util.concurrent.atomic.AtomicInteger timeout;
    @Inject(method = "showPreview", at = @At("HEAD"), cancellable = true)
    private void gtlcore$show(BlockPos origin, MutableSchema schema, RenderFilter filter, int duration, CallbackInfo ci) {
        if (WorldPreview.show(origin, schema, filter, duration)) {
            // The native layer-cycling method compares this anchor on the next activation.
            // dispose() above cleared it while canceling the previous native compilation.
            this.controllerPos = origin;
            this.schema = schema;
            this.timeout.set(WorldPreview.active() ? -1 : duration);
            ci.cancel();
        } else WorldPreview.clear();
    }
    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    private void gtlcore$render(PoseStack pose, MultiBufferSource.BufferSource buffers, Camera camera,
                               RenderLevelStageEvent.Stage stage, float partialTicks, Matrix4f modelView, CallbackInfo ci) {
        if (WorldPreview.active()) {
            if (stage == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) WorldPreview.render(pose, camera, partialTicks, modelView);
            ci.cancel();
        }
    }
    @Inject(method = "dispose", at = @At("HEAD"))
    private void gtlcore$cancel(CallbackInfo ci) { WorldPreview.clear(); }
    @Inject(method = "clientTick", at = @At("HEAD"))
    private void gtlcore$tick(CallbackInfo ci) { WorldPreview.tick(); }
}
