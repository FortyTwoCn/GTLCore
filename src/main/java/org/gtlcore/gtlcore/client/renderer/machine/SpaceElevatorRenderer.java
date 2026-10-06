package org.gtlcore.gtlcore.client.renderer.machine;

import org.gtlcore.gtlcore.api.GTLConstants;
import org.gtlcore.gtlcore.client.ClientUtil;
import org.gtlcore.gtlcore.client.renderer.RenderBufferHelper;
import org.gtlcore.gtlcore.utils.RenderUtil;

import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.client.renderer.GTRenderTypes;

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


public class SpaceElevatorRenderer extends DynamicRender<WorkableMultiblockMachine, SpaceElevatorRenderer> {

    public static final ResourceLocation CLIMBER_MODEL = GTLConstants.id("obj/climber");

    public static final MapCodec<SpaceElevatorRenderer> CODEC = MapCodec.unit(SpaceElevatorRenderer::new);
    public static final DynamicRenderType<WorkableMultiblockMachine, SpaceElevatorRenderer> TYPE = new DynamicRenderType<>(CODEC);

    @Override
    public DynamicRenderType<WorkableMultiblockMachine, SpaceElevatorRenderer> getType() {
        return TYPE;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void render(WorkableMultiblockMachine machine, float partialTicks, PoseStack poseStack, MultiBufferSource buffer,
                       int combinedLight, int combinedOverlay) {
        if (machine.isFormed()) {
            float tick = RenderUtil.getSmoothTick(machine, partialTicks);
            double x = 0.5, y = 1, z = 0.5;
            switch (machine.getFrontFacing()) {
                case NORTH -> z = 3.5;
                case SOUTH -> z = -2.5;
                case WEST -> x = 3.5;
                case EAST -> x = -2.5;
            }
            poseStack.pushPose();
            RenderBufferHelper.renderCylinder(poseStack, buffer.getBuffer(GTRenderTypes.lightRing()), (float) x, (float) (y - 2), (float) z, 0.3F, 360, 10, 0, 0, 0, 1);
            poseStack.translate(x, y + 180 + (140 * Math.sin(tick / 160)), z);
            renderClimber(poseStack, buffer);
            poseStack.popPose();
        }
    }

    private void renderClimber(PoseStack poseStack, MultiBufferSource buffer) {
        float scale = 4F;
        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        ClientUtil.modelRenderer().renderModel(poseStack.last(), buffer.getBuffer(RenderType.solid()), null, ClientUtil.getBakedModel(CLIMBER_MODEL), 1.0F, 1.0F, 1.0F, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderType.solid());
        poseStack.popPose();
    }

    @Override
    public boolean shouldRender(WorkableMultiblockMachine machine, Vec3 cameraPos) {
        return machine.isFormed() && super.shouldRender(machine, cameraPos);
    }

    @Override
    public boolean shouldRenderOffScreen(WorkableMultiblockMachine machine) {
        return machine.isFormed();
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
