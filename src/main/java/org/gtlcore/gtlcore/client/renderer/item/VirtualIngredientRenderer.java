package org.gtlcore.gtlcore.client.renderer.item;

import org.gtlcore.gtlcore.common.item.VirtualIngredientBehavior;

import com.lowdragmc.lowdraglib.client.model.ModelFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import com.mojang.blaze3d.vertex.*;
import org.joml.Matrix4f;

/**
 * Draws a virtual ingredient's payload, with the wrapper's own sprite laid over it as a frame.
 * <p>
 * Fluid payloads get a flat sprite rather than their bucket, because a bucket is itself a legitimate item payload.
 */
public class VirtualIngredientRenderer extends WrappedItemRenderer {

    public static final VirtualIngredientRenderer INSTANCE = new VirtualIngredientRenderer();

    @Override
    @OnlyIn(Dist.CLIENT)
    public void renderItem(ItemStack stack, ItemDisplayContext transformType,
                           boolean leftHand, PoseStack poseStack,
                           MultiBufferSource buffer, int combinedLight,
                           int combinedOverlay, BakedModel model) {
        Minecraft mc = Minecraft.getInstance();
        AEItemKey payloadItem = VirtualIngredientBehavior.payloadItemKey(stack);
        AEFluidKey payloadFluid = VirtualIngredientBehavior.payloadFluidKey(stack);

        poseStack.pushPose();
        try {
            if (payloadItem != null) {
                ItemStack payload = payloadItem.toStack(1);
                BakedModel payloadModel = mc.getItemRenderer().getModel(payload, mc.level, mc.player, 0);
                mc.getItemRenderer().render(payload, transformType, leftHand, poseStack, buffer, combinedLight,
                        combinedOverlay, payloadModel);
            } else if (payloadFluid == null) {
                // Rendering our own stack dispatches straight back here, so mute the provider for the duration.
                BakedModel own = getVanillaModel(stack, mc.level, mc.player);
                vanillaRender(stack, transformType, leftHand, poseStack, buffer, combinedLight, combinedOverlay, own);
            }

            if (transformType == ItemDisplayContext.GUI) {
                poseStack.translate(-0.5F, -0.5F, -0.5F);
                if (payloadFluid != null) {
                    var extensions = IClientFluidTypeExtensions.of(payloadFluid.getFluid());
                    var fluid = payloadFluid.toStack(1000);
                    blit(poseStack, buffer, combinedLight,
                            ModelFactory.getBlockSprite(extensions.getStillTexture(fluid)), extensions.getTintColor(fluid));
                }
                blit(poseStack, buffer, combinedLight,
                        ModelFactory.getBlockSprite(ResourceLocation.fromNamespaceAndPath("gtlcore", "item/virtual_ingredient")), -1);
            }

        } finally {
            poseStack.popPose();
        }
    }

    /**
     * Emits into the same buffer source the item was drawn with. An immediate-mode Tesselator lands outside that
     * batch, leaving draw order up to whoever flushes it, which differs per screen.
     */
    @OnlyIn(Dist.CLIENT)
    private static void blit(PoseStack poseStack, MultiBufferSource buffer, int light,
                             TextureAtlasSprite sprite, int tint) {
        int a = tint >> 24 & 0xFF;
        int r = tint >> 16 & 0xFF;
        int g = tint >> 8 & 0xFF;
        int b = tint & 0xFF;
        // No alpha means the sprite carries its own colour; multiplying by zero would erase it.
        if (a == 0) {
            a = 0xFF;
            r = 0xFF;
            g = 0xFF;
            b = 0xFF;
        }

        VertexConsumer consumer = buffer.getBuffer(RenderType.text(InventoryMenu.BLOCK_ATLAS));
        float minU = sprite.getU0();
        float maxU = sprite.getU1();
        float minV = sprite.getV0();
        float maxV = sprite.getV1();
        Matrix4f pos = poseStack.last().pose();
        consumer.addVertex(pos, 1, 1, 0).setColor(r, g, b, a).setUv(maxU, minV).setLight(light);
        consumer.addVertex(pos, 0, 1, 0).setColor(r, g, b, a).setUv(minU, minV).setLight(light);
        consumer.addVertex(pos, 0, 0, 0).setColor(r, g, b, a).setUv(minU, maxV).setLight(light);
        consumer.addVertex(pos, 1, 0, 0).setColor(r, g, b, a).setUv(maxU, maxV).setLight(light);
    }
}
