package org.gtlcore.gtlcore.client.renderer.machine;

import org.gtlcore.gtlcore.api.GTLConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.resources.model.ModelResourceLocation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Shared model entry point for harmony animations, including addons using ClientUtil.
 * Collects only ordinary world block-entity draws; previews, shader packs and custom consumers
 * retain the original renderer. No dependency on addon classes or their animation implementations.
 */
public final class HarmonyAnimationRenderer {

    private static final Map<BakedModel, HarmonyModelCache> MODELS = new IdentityHashMap<>();
    private static final HarmonyModelCache[] ORDER = new HarmonyModelCache[HarmonyModelCache.NAMES.length];
    private static boolean collecting;
    private static volatile int resourceVersion;
    private static int builtVersion = -1;
    private static Renderer renderer;
    private static final Matrix4f projection = new Matrix4f();
    private static boolean shaderApiChecked, shaderApiFailed;
    private static Object shaderApi;
    private static Method shaderPackInUse;
    private static int shaderResourceVersion = -1;
    private static boolean standardShaders;
    private static ShaderInstance animationShader;
    private static Uniform animationTransform;

    private HarmonyAnimationRenderer() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(HarmonyAnimationRenderer::reload);
        modBus.addListener(HarmonyAnimationRenderer::modelsBaked);
        modBus.addListener(HarmonyAnimationRenderer::loadShader);
        NeoForge.EVENT_BUS.addListener(HarmonyAnimationRenderer::stage);
        NeoForge.EVENT_BUS.addListener(HarmonyAnimationRenderer::frame);
        NeoForge.EVENT_BUS.addListener(HarmonyAnimationRenderer::unload);
    }

    /** Called by ClientUtil without changing its public return type or existing addon linkage. */
    public static ModelBlockRenderer modelRenderer(ModelBlockRenderer original) {
        if (!collecting || !RenderSystem.isOnRenderThread()) return original;
        if (renderer == null || renderer.original != original) renderer = new Renderer(original);
        return renderer;
    }

    private static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resources -> resourceVersion++);
    }

    private static void modelsBaked(ModelEvent.BakingCompleted event) {
        resourceVersion++;
    }

    private static void loadShader(RegisterShadersEvent event) {
        animationShader = null;
        animationTransform = null;
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), GTLConstants.id("harmony_animation"), DefaultVertexFormat.BLOCK), shader -> {
                animationShader = shader;
                animationTransform = shader.getUniform("AnimationTransform");
            });
        } catch (IOException exception) {
            GTLConstants.LOGGER.warn("Could not load the harmony animation shader; using the regular model renderer", exception);
        }
    }

    private static void unload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) resourceVersion++;
    }

    private static void frame(RenderFrameEvent.Pre event) {
        // Interrupted frames and world changes can never leave instances in the next frame.
        reset();
        if (builtVersion != resourceVersion) dispose();

    }

    private static void stage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            reset();
            if (Minecraft.getInstance().level != null && animationShader != null && animationTransform != null &&
                    !Minecraft.useShaderTransparency() && !shaders() && standardShaders()) {
                projection.set(RenderSystem.getProjectionMatrix());
                collecting = true;
            }
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            collecting = false;
            try {
                draw();
            } finally {
                reset();
            }
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            reset();
        }
    }

    private static boolean standardShaders() {
        if (shaderResourceVersion != resourceVersion) {
            var resources = Minecraft.getInstance().getResourceManager();
            standardShaders = true;
            for (String name : new String[] { "solid", "translucent" }) {
                for (String extension : new String[] { "json", "vsh", "fsh" }) {
                    var location = ResourceLocation.fromNamespaceAndPath("minecraft", "shaders/core/rendertype_" + name + "." + extension);
                    standardShaders &= resources.getResource(location)
                            .map(resource -> resource.sourcePackId().equals("vanilla")).orElse(false);
                }
            }
            shaderResourceVersion = resourceVersion;
        }
        return standardShaders;
    }

    private static boolean shaders() {
        if (!shaderApiChecked) {
            shaderApiChecked = true;
            if (ModList.get().isLoaded("oculus") || ModList.get().isLoaded("iris")) {
                try {
                    Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                    shaderApi = api.getMethod("getInstance").invoke(null);
                    shaderPackInUse = api.getMethod("isShaderPackInUse");
                } catch (ReflectiveOperationException | LinkageError exception) {
                    shaderApiFailed = true;
                }
            }
        }
        if (shaderApiFailed) return true;
        if (shaderApi == null) return false;
        try {
            return (boolean) shaderPackInUse.invoke(shaderApi);
        } catch (ReflectiveOperationException exception) {
            shaderApiFailed = true;
            return true;
        }
    }

    private static void prepareModels() {
        if (builtVersion == resourceVersion) return;
        dispose();
        var manager = Minecraft.getInstance().getModelManager();
        for (int i = 0; i < ORDER.length; i++) {
            String name = HarmonyModelCache.NAMES[i];
            BakedModel model = manager.getModel(ModelResourceLocation.standalone(GTLConstants.id("obj/" + name)));
            if (model == manager.getMissingModel()) continue;
            ORDER[i] = new HarmonyModelCache(name, model);
            MODELS.put(model, ORDER[i]);
        }
        builtVersion = resourceVersion;
    }

    private static void reset() {
        collecting = false;
        for (var mesh : ORDER) {
            if (mesh != null) mesh.instances = 0;
        }
    }

    private static void dispose() {
        for (int i = 0; i < ORDER.length; i++) {
            if (ORDER[i] != null) ORDER[i].close();
            ORDER[i] = null;
        }
        MODELS.clear();
        if (renderer != null) renderer.buffers.clear();
        builtVersion = -1;
    }

    private static void draw() {
        for (var mesh : ORDER) {
            if (mesh == null || mesh.instances == 0) continue;
            mesh.type.setupRenderState();
            ShaderInstance shader = animationShader;
            try {
                mesh.bind();
                animationTransform.set(mesh.transforms.get(0));
                setupShader(shader, mesh.views.get(0));
                for (int i = 0; i < mesh.instances; i++) {
                    if (i != 0) {
                        animationTransform.set(mesh.transforms.get(i));
                        animationTransform.upload();
                        if (!mesh.views.get(i).equals(mesh.views.get(i - 1))) {
                            shader.MODEL_VIEW_MATRIX.set(mesh.views.get(i));
                            shader.MODEL_VIEW_MATRIX.upload();
                        }
                    }
                    mesh.draw();
                }
            } finally {
                if (shader != null) shader.clear();
                VertexBuffer.unbind();
                mesh.type.clearRenderState();
            }
        }
    }

    /** The same uniforms as VertexBuffer.drawWithShader, applied once per model batch. */
    private static void setupShader(ShaderInstance shader, Matrix4f pose) {
        shader.setDefaultUniforms(VertexFormat.Mode.TRIANGLES, pose, projection, Minecraft.getInstance().getWindow());
        shader.apply();
    }

    private static final class Renderer extends ModelBlockRenderer {

        private final ModelBlockRenderer original;
        private final HarmonyWorldBuffers buffers = new HarmonyWorldBuffers();

        Renderer(ModelBlockRenderer original) {
            super(Minecraft.getInstance().getBlockColors());
            this.original = original;
        }

        @Override
        public void renderModel(PoseStack.Pose pose, VertexConsumer consumer, @Nullable BlockState state,
                                BakedModel model, float red, float green, float blue, int light, int overlay,
                                ModelData data, RenderType type) {
            if (collecting && animationShader != null && animationTransform != null &&
                    state == null && data == ModelData.EMPTY && red == 1 && green == 1 && blue == 1 &&
                    light == LightTexture.FULL_BRIGHT && overlay == OverlayTexture.NO_OVERLAY &&
                    (type == RenderType.solid() || type == RenderType.translucent()) &&
                    RenderSystem.getShaderColor()[3] == 1 &&
                    buffers.owns(consumer, type)) {
                prepareModels();
                var mesh = MODELS.get(model);
                if (mesh != null && mesh.type == type && mesh.prepare(original) &&
                        mesh.enqueue(pose.pose(), RenderSystem.getModelViewMatrix(), projection))
                    return;
            }
            original.renderModel(pose, consumer, state, model, red, green, blue, light, overlay, data, type);
        }
    }
}
