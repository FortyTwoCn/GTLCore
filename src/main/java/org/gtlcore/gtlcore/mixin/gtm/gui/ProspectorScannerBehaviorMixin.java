package org.gtlcore.gtlcore.mixin.gtm.gui;

import org.gtlcore.gtlcore.integration.ftb.NativeProspectorCoordinates;
import com.gregtechceu.gtceu.common.item.behavior.ProspectorScannerBehavior;
import brachy.modularui.drawable.GuiTextures;
import brachy.modularui.factory.PlayerInventoryGuiData;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widgets.ButtonWidget;
import net.neoforged.fml.ModList;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ProspectorScannerBehavior.class)
public abstract class ProspectorScannerBehaviorMixin {
    @Inject(method = "buildUI", at = @At("RETURN"), remap = false, require = 1)
    private void gtlcore$clearProspectingWaypoints(PlayerInventoryGuiData<?> data, PanelSyncManager manager,
                                                  UISettings settings, CallbackInfoReturnable<ModularPanel<?>> cir) {
        if (!ModList.get().isLoaded("ftbchunks")) return;
        cir.getReturnValue().child(new ButtonWidget<>().name("gtlcore_clear_prospecting_waypoints")
                .size(18).top(26).leftRelAnchor(0f, 1f).decoration().excludeAreaInRecipeViewer()
                .background(GuiTextures.BUTTON_CLEAN).overlay(GuiTextures.CLOSE)
                .tooltip(tooltip -> tooltip.addLine(net.minecraft.network.chat.Component.translatable("gtlcore.gui.prospector.clear_waypoints")))
                .onMousePressed((context, button) -> {
                    if (button != 0) return false;
                    var waypoints = FTBChunksAPI.clientApi().getWaypointManager(data.getPlayer().level().dimension()).orElse(null);
                    if (waypoints != null) waypoints.getAllWaypoints().stream()
                            .filter(waypoint -> NativeProspectorCoordinates.isGeneratedWaypoint(waypoint.getName()))
                            .toList().forEach(waypoints::removeWaypoint);
                    return true;
                }));
    }
}
