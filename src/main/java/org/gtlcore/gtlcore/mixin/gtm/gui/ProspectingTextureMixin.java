package org.gtlcore.gtlcore.mixin.gtm.gui;

import org.gtlcore.gtlcore.client.gui.IProspectingTextureMixin;
import org.gtlcore.gtlcore.integration.ftb.NativeProspectorCoordinates;
import com.gregtechceu.gtceu.common.mui.widgets.prospector.ProspectorMapHandler;
import com.gregtechceu.gtceu.common.mui.widgets.prospector.ProspectorMapTexture;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;
import dev.architectury.networking.NetworkManager;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.net.TeleportFromMapPacket;
import org.spongepowered.asm.mixin.*;

@Mixin(ProspectorMapTexture.class)
public abstract class ProspectingTextureMixin extends AbstractTexture implements IProspectingTextureMixin {
    @Shadow(remap = false) @Final private ProspectorMapHandler<?> mapHandler;

    @Override @Unique
    public void gTLCore$addWayPoint(Player player, double mouseX, double mouseY) {
        if (!ModList.get().isLoaded("ftbchunks")) return;
        var manager = FTBChunksAPI.clientApi().getWaypointManager(player.level().dimension()).orElse(null);
        if (manager == null) return;
        NativeProspectorCoordinates.point(mapHandler.getPlayerChunkPos(), mapHandler.getChunkRadius(), mouseX, mouseY)
                .ifPresent(pos -> manager.addWaypointAt(pos, NativeProspectorCoordinates.nextWaypointName(
                        manager.getAllWaypoints().stream().map(waypoint -> waypoint.getName()).toList())).setColor(200));
    }

    @Override @Unique
    public void gTLCore$teleportWayPoint(Player player, double mouseX, double mouseY) {
        if (!ModList.get().isLoaded("ftbchunks")) return;
        NativeProspectorCoordinates.point(mapHandler.getPlayerChunkPos(), mapHandler.getChunkRadius(), mouseX, mouseY)
                .ifPresent(pos -> NetworkManager.sendToServer(new TeleportFromMapPacket(pos, true, player.level().dimension())));
    }
}
