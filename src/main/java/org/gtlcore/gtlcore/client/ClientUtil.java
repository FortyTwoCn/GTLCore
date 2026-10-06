package org.gtlcore.gtlcore.client;

import org.gtlcore.gtlcore.client.renderer.machine.HarmonyAnimationRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ClientUtil {

    public static Minecraft mc() {
        return Minecraft.getInstance();
    }

    public static BakedModel getBakedModel(ResourceLocation resourceLocation) {
        return mc().getModelManager().getModel(ModelResourceLocation.standalone(resourceLocation));
    }

    public static BlockRenderDispatcher blockRenderer() {
        return mc().getBlockRenderer();
    }

    public static ModelBlockRenderer modelRenderer() {
        return HarmonyAnimationRenderer.modelRenderer(blockRenderer().getModelRenderer());
    }
}
