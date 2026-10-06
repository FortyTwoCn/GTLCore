package org.gtlcore.gtlcore.client.renderer;

import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public class RenderBufferHelper {

    /**
     * 绘制圆柱体
     */
    public static void renderCylinder(PoseStack poseStack, VertexConsumer buffer, float x, float y, float z,
                                      float radius, float height, int sides, float red, float green, float blue, float alpha) {
        Matrix4f mat = poseStack.last().pose();
        float angleStep = (float) (2.0 * Math.PI / sides);

        if (sides < 3) throw new IllegalArgumentException("Cylinder requires at least three sides");
        // GT 1.21 lightRing uses TRIANGLE_STRIP. Duplicate endpoints isolate successive cylinders
        // in the same native batch without adding visible triangles between them.
        buffer.addVertex(mat, x + radius, y, z).setColor(red, green, blue, alpha);
        for (int i = 0; i <= sides; i++) {
            float angle = i * angleStep;
            float px = x + Mth.cos(angle) * radius, pz = z + Mth.sin(angle) * radius;
            buffer.addVertex(mat, px, y, pz).setColor(red, green, blue, alpha);
            buffer.addVertex(mat, px, y + height, pz).setColor(red, green, blue, alpha);
        }
        float finalX = x + Mth.cos(sides * angleStep) * radius;
        float finalZ = z + Mth.sin(sides * angleStep) * radius;
        buffer.addVertex(mat, finalX, y + height, finalZ).setColor(red, green, blue, alpha);
    }
}
