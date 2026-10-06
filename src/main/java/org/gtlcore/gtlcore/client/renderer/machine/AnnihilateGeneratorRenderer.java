package org.gtlcore.gtlcore.client.renderer.machine;

import org.gtlcore.gtlcore.client.ClientUtil;
import org.gtlcore.gtlcore.utils.RenderUtil;

import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.model.data.ModelData;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRender;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;


import static org.gtlcore.gtlcore.client.renderer.machine.EyeOfHarmonyRenderer.STAR_MODEL;

public class AnnihilateGeneratorRenderer extends DynamicRender<WorkableMultiblockMachine, AnnihilateGeneratorRenderer> {

    public static final MapCodec<AnnihilateGeneratorRenderer> CODEC = MapCodec.unit(AnnihilateGeneratorRenderer::new);
    public static final DynamicRenderType<WorkableMultiblockMachine, AnnihilateGeneratorRenderer> TYPE = new DynamicRenderType<>(CODEC);

    @Override
    public DynamicRenderType<WorkableMultiblockMachine, AnnihilateGeneratorRenderer> getType() {
        return TYPE;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void render(WorkableMultiblockMachine machine, float partialTicks, PoseStack poseStack, MultiBufferSource buffer,
                       int combinedLight, int combinedOverlay) {
        if (machine.isActive()) {
            float tick = RenderUtil.getSmoothTick(machine, partialTicks);
            double x = 0.5, y = 36.5, z = 0.5;
            switch (machine.getFrontFacing()) {
                case NORTH -> z = 39.5;
                case SOUTH -> z = -38.5;
                case WEST -> x = 39.5;
                case EAST -> x = -38.5;
            }
            poseStack.pushPose();
            poseStack.translate(x, y, z);
            renderStar(tick, poseStack, buffer);
            poseStack.popPose();
        }
    }

    private static void renderStar(float tick, PoseStack poseStack, MultiBufferSource buffer) {
        poseStack.pushPose();
        poseStack.scale(0.45F, 0.45F, 0.45F);
        poseStack.mulPose(new Quaternionf().fromAxisAngleDeg(0F, 1F, 1F, tick % 360F));
        ClientUtil.modelRenderer().renderModel(poseStack.last(), buffer.getBuffer(RenderType.translucent()), null, ClientUtil.getBakedModel(STAR_MODEL), 1.0F, 1.0F, 1.0F, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderType.translucent());
        poseStack.popPose();
    }

    @Override
    public boolean shouldRender(WorkableMultiblockMachine machine, Vec3 cameraPos) {
        return machine.isActive() && super.shouldRender(machine, cameraPos);
    }

    @Override
    public boolean shouldRenderOffScreen(WorkableMultiblockMachine machine) {
        return machine.isActive();
    }

    @Override
    public AABB getRenderBoundingBox(WorkableMultiblockMachine machine) {
        return new AABB(machine.getBlockPos()).inflate(getViewDistance());
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public int getViewDistance() {
        return 512;
    }
}
