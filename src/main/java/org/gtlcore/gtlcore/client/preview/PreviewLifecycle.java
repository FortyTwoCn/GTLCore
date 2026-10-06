package org.gtlcore.gtlcore.client.preview;

import org.gtlcore.gtlcore.api.gui.PatternPreviewWidget;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = "gtlcore", value = Dist.CLIENT)
public final class PreviewLifecycle {

    private static int reloads;

    private PreviewLifecycle() {}

    @SubscribeEvent
    public static void frame(RenderFrameEvent.Pre event) {
        PreviewFrameBudget.beginFrame();
        PreviewSceneState.releaseInvisible();

    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
        PreviewShapeCache.clear();
        PatternPreviewWidget.clearSharedPreviewState();
    }

    public static void clear() {
        PreviewScenes.clear();
        com.gregtechceu.gtceu.client.renderer.PatternPreviewRenderer.INSTANCE.dispose();
        PreviewSceneState.clearAll();
        PreviewMesh.clearAll();
    }

    public static void beginReload() {
        reloads++;
        clear();
    }

    public static void endReload() {
        clear();
        reloads = Math.max(0, reloads - 1);
    }

    public static boolean reloading() {
        return reloads != 0;
    }
}
