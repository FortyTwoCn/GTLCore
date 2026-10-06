package org.gtlcore.gtlcore.integration.ae2.chamber;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAePackets;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

import java.util.function.Consumer;

/** Bounded transport; clients publish a snapshot only after every chunk has arrived. */
public final class ChamberSnapshotTransfer {

    public static final int MAX_CHUNK_BYTES = ChamberSnapshotCodec.MAX_CHUNK_BYTES;
    public static final int MAX_SNAPSHOT_BYTES = ChamberSnapshotCodec.MAX_SNAPSHOT_BYTES;

    private ChamberSnapshotTransfer() {}

    public static boolean send(ServerPlayer player, int containerId, boolean contents, long revision,
                               Consumer<RegistryFriendlyByteBuf> encoder) {
        SyncEvent event = new SyncEvent();
        event.begin();
        try {
            ChamberSnapshotCodec.encode(player.registryAccess(), encoder, chunk -> {
                PacketDistributor.sendToPlayer(player,
                        new WirelessAePackets.SyncMEChamberSnapshotPacket(
                                containerId, contents, revision, chunk.totalBytes(), chunk.offset(), chunk.data()));
                event.bytes = chunk.totalBytes();
                event.chunks++;
            });
            event.contents = contents;
            event.containerId = containerId;
            event.commit();
            return true;
        } catch (IndexOutOfBoundsException | IllegalArgumentException failure) {
            GTLCore.LOGGER.warn("Cannot encode chamber snapshot for menu {}", containerId, failure);
            player.displayClientMessage(Component.translatable("message.gtlcore.chamber_snapshot_too_large"), false);
            player.closeContainer();
            return false;
        }
    }

    @Name("gtlcore.ChamberSnapshot")
    @Label("Chamber snapshot transfer")
    @Category("GTLCore")
    @StackTrace(false)
    public static final class SyncEvent extends Event {

        public int containerId;
        public boolean contents;
        public int bytes;
        public int chunks;
    }
}
