package org.gtlcore.gtlcore.mixin.gtm.gui;

import org.gtlcore.gtlcore.client.gui.IProspectingTextureMixin;
import org.gtlcore.gtlcore.integration.ftb.NativeProspectorCoordinates;
import com.gregtechceu.gtceu.common.mui.widgets.prospector.ProspectorMapHandler;
import com.gregtechceu.gtceu.common.mui.widgets.prospector.ProspectorMapTexture;
import brachy.modularui.api.widget.Interactable;
import brachy.modularui.widget.Widget;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ProspectorMapHandler.class)
public abstract class ProspectingMapWidgetMixin extends Widget<ProspectingMapWidgetMixin> {
    @Shadow(remap = false) private ProspectorMapTexture<?> texture;

    @Inject(method = "onMousePressed", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void gtlcore$waypointButtons(int button, CallbackInfoReturnable<Interactable.Result> cir) {
        if (texture == null || !ModList.get().isLoaded("ftbchunks") || (button != 0 && button != 1)) return;
        var map = (ProspectorMapHandler<?>) (Object) this;
        int x = getContext().getMouseX(), z = getContext().getMouseY();
        if (NativeProspectorCoordinates.point(map.getPlayerChunkPos(), map.getChunkRadius(), x, z).isEmpty()) return;
        var waypoints = (IProspectingTextureMixin) texture;
        if (button == 0) waypoints.gTLCore$teleportWayPoint(map.getPlayer(), x, z);
        else waypoints.gTLCore$addWayPoint(map.getPlayer(), x, z);
        cir.setReturnValue(Interactable.Result.SUCCESS);
    }
}
