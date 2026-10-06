package org.gtlcore.gtlcore.client.renderer.machine;

import org.gtlcore.gtlcore.api.GTLConstants;
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

import java.util.List;

public class EyeOfHarmonyRenderer extends DynamicRender<WorkableMultiblockMachine, EyeOfHarmonyRenderer> {

    public static final ResourceLocation SPACE_MODEL = GTLConstants.id("obj/space");
    public static final ResourceLocation STAR_MODEL = GTLConstants.id("obj/star");
    public static final List<ResourceLocation> ORBIT_OBJECTS = List.of(
            GTLConstants.id("obj/the_nether"),
            GTLConstants.id("obj/overworld"),
            GTLConstants.id("obj/the_end"));

    public static final MapCodec<EyeOfHarmonyRenderer> CODEC = MapCodec.unit(EyeOfHarmonyRenderer::new);
    public static final DynamicRenderType<WorkableMultiblockMachine, EyeOfHarmonyRenderer> TYPE = new DynamicRenderType<>(CODEC);

    @Override
    public DynamicRenderType<WorkableMultiblockMachine, EyeOfHarmonyRenderer> getType() {
        return TYPE;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void render(WorkableMultiblockMachine machine, float partialTicks, PoseStack poseStack, MultiBufferSource buffer,
                       int combinedLight, int combinedOverlay) {
        if (machine.isActive()) {
            float tick = RenderUtil.getSmoothTick(machine, partialTicks);
            double x = 0.5, y = 0.5, z = 0.5;
            switch (machine.getFrontFacing()) {
                case NORTH -> z = 16.5;
                case SOUTH -> z = -15.5;
                case WEST -> x = 16.5;
                case EAST -> x = -15.5;
            }
            poseStack.pushPose();
            poseStack.translate(x, y, z);
            renderStar(tick, poseStack, buffer);
            renderOrbitObjects(tick, poseStack, buffer, x, y, z);
            renderOuterSpaceShell(poseStack, buffer);
            poseStack.popPose();
        }
    }

    private static void renderStar(float tick, PoseStack poseStack, MultiBufferSource buffer) {
        poseStack.pushPose();
        poseStack.scale(0.02F, 0.02F, 0.02F);
        poseStack.mulPose(new Quaternionf().fromAxisAngleDeg(0F, 1F, 1F, (tick / 2) % 360F));
        ClientUtil.modelRenderer().renderModel(poseStack.last(), buffer.getBuffer(RenderType.translucent()), null, ClientUtil.getBakedModel(STAR_MODEL), 1.0F, 1.0F, 1.0F, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderType.translucent());
        poseStack.popPose();
    }

    private void renderOrbitObjects(float tick, PoseStack poseStack, MultiBufferSource buffer, double x, double y, double z) {
        for (int a = 1; a < 4; a++) {
            float scale = 0.007F + 0.003F * a;
            poseStack.pushPose();
            poseStack.scale(scale, scale, scale);
            poseStack.mulPose(new Quaternionf().fromAxisAngleDeg(1F, 0F, 1F, (tick * 1.5F / a) % 360F));
            poseStack.translate(x + (a * 100 + 160) * Math.sin(tick * a / 80 + 0.4), y, z + (a * 100 + 160) * Math.cos(tick * a / 80 + 0.4));
            ClientUtil.modelRenderer().renderModel(poseStack.last(), buffer.getBuffer(RenderType.solid()), null, ClientUtil.getBakedModel(ORBIT_OBJECTS.get(a - 1)), 1.0F, 1.0F, 1.0F, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderType.solid());
            poseStack.popPose();
        }
    }

    private void renderOuterSpaceShell(PoseStack poseStack, MultiBufferSource buffer) {
        float scale = 0.01F * 17.5F;
        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        ClientUtil.modelRenderer().renderModel(poseStack.last(), buffer.getBuffer(RenderType.solid()), null, ClientUtil.getBakedModel(SPACE_MODEL), 1.0F, 1.0F, 1.0F, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderType.solid());
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
        return 128;
    }
}
