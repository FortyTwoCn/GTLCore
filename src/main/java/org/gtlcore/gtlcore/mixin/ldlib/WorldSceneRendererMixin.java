package org.gtlcore.gtlcore.mixin.ldlib;

import org.gtlcore.gtlcore.client.preview.PreviewRendererAccess;
import org.gtlcore.gtlcore.client.preview.PreviewSceneState;
import org.gtlcore.gtlcore.client.preview.PreviewScenes;

import com.lowdragmc.lowdraglib.client.scene.CameraEntity;
import com.lowdragmc.lowdraglib.client.scene.WorldSceneRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Iterator;

/** Bounded native MeshData compilation for previews; other LDLib scenes keep their own renderer. */
@Mixin(value = WorldSceneRenderer.class, remap = false)
public abstract class WorldSceneRendererMixin implements PreviewRendererAccess {
    @Unique private PreviewScenes.Key gtlcore$previewKey;
    @Unique private PreviewSceneState gtlcore$previewScene;
    @Shadow protected boolean ortho;
    @Shadow protected boolean useCache;
    @Shadow protected CameraEntity cameraEntity;
    @Shadow private float fov;
    @Shadow @Final public Level world;

    @Override public float gtlcore$fov() { return fov; }
    @Override public void gtlcore$previewKey(PreviewScenes.Key key) { gtlcore$previewKey = key; }
    @Override public PreviewScenes.Key gtlcore$previewKey() { return gtlcore$previewKey; }
    @Override public Iterator<BlockPos> gtlcore$rayCandidates(Vec3 start, Vec3 end) {
        return gtlcore$previewScene == null ? null : gtlcore$previewScene.rayCandidates(start, end);
    }

    @Inject(method = "renderCacheBuffer", at = @At("HEAD"), cancellable = true)
    private void gtlcore$render(Minecraft minecraft, MultiBufferSource.BufferSource buffers,
                               float partialTicks, CallbackInfo ci) {
        if (gtlcore$previewKey == null) return;
        if (gtlcore$previewScene == null) gtlcore$previewScene = new PreviewSceneState();
        if (gtlcore$previewScene.render(gtlcore$previewKey, (WorldSceneRenderer) (Object) this, partialTicks)) ci.cancel();
    }

    @Inject(method = "deleteCacheBuffer", at = @At("HEAD"))
    private void gtlcore$release(CallbackInfoReturnable<WorldSceneRenderer> cir) {
        if (gtlcore$previewScene != null) gtlcore$previewScene.release();
    }

    @Inject(method = "needCompileCache", at = @At("HEAD"))
    private void gtlcore$invalidate(CallbackInfoReturnable<WorldSceneRenderer> cir) {
        if (gtlcore$previewScene != null) gtlcore$previewScene.release();
        PreviewScenes.invalidate(gtlcore$previewKey);
    }

    @Inject(method = "isCompiling", at = @At("HEAD"), cancellable = true)
    private void gtlcore$compiling(CallbackInfoReturnable<Boolean> cir) {
        if (useCache && gtlcore$previewScene != null && !gtlcore$previewScene.fallback())
            cir.setReturnValue(gtlcore$previewScene.compiling());
    }

    @Inject(method = "getCompileProgress", at = @At("HEAD"), cancellable = true)
    private void gtlcore$progress(CallbackInfoReturnable<Double> cir) {
        if (useCache && gtlcore$previewScene != null && !gtlcore$previewScene.fallback())
            cir.setReturnValue(gtlcore$previewScene.progress());
    }

    @Inject(method = "rayTrace", at = @At("HEAD"), cancellable = true)
    private void gtlcore$trace(Vector3f point, CallbackInfoReturnable<BlockHitResult> cir) {
        if (gtlcore$previewKey == null || gtlcore$previewScene == null || gtlcore$previewScene.fallback()) return;
        if (!Float.isFinite(point.x) || !Float.isFinite(point.y) || !Float.isFinite(point.z)) {
            cir.setReturnValue(null);
            return;
        }
        var renderer = (WorldSceneRenderer) (Object) this;
        var start = new Vec3(renderer.getEyePos());
        if (ortho) start = start.add(start.subtract(new Vec3(renderer.getLookAt())).scale(500));
        var twice = point.mul(2.0f, new Vector3f());
        var end = new Vec3(twice.x - start.x, twice.y - start.y, twice.z - start.z);
        cir.setReturnValue(gtlcore$previewScene.trace(world, start, end, cameraEntity));
    }
}
