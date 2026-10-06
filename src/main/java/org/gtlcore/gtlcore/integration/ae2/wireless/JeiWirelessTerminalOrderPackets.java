package org.gtlcore.gtlcore.integration.ae2.wireless;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import appeng.api.stacks.AEKey;

import java.util.Optional;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class JeiWirelessTerminalOrderPackets {

    private static final int MAX_REQUESTS_PER_WINDOW = 20;
    private static final long REQUEST_WINDOW_TICKS = 20;
    private static final MeInventoryRequestLimiter<ServerPlayer> REQUEST_LIMITER = new MeInventoryRequestLimiter<>(
            MAX_REQUESTS_PER_WINDOW,
            REQUEST_WINDOW_TICKS);

    private JeiWirelessTerminalOrderPackets() {}

    public static void register(PayloadRegistrar registrar) {
        org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAePackets.registerPacket(registrar, Request.TYPE, Request::encode, Request::decode, Request::handle, PacketFlow.SERVERBOUND);
        org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAePackets.registerPacket(registrar, ExtractionRequest.TYPE, ExtractionRequest::encode, ExtractionRequest::decode, ExtractionRequest::handle, PacketFlow.SERVERBOUND);
    }

    public static void sendRequest(AEKey key) {
        if (WirelessAeKeyPacketCodec.supports(key)) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new Request(key));
        }
    }

    public static void sendExtractionRequest(AEKey key) {
        if (WirelessAeKeyPacketCodec.supports(key)) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new ExtractionRequest(key));
        }
    }

    public record Request(AEKey key) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Request> TYPE = new CustomPacketPayload.Type<>(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gtlcore", "jei_wireless_terminal_order/_request"));

        @Override
        public CustomPacketPayload.Type<Request> type() { return TYPE; }


        private static void encode(Request packet, RegistryFriendlyByteBuf buffer) {
            WirelessAeKeyPacketCodec.write(buffer, packet.key);
        }

        private static Request decode(RegistryFriendlyByteBuf buffer) {
            return new Request(WirelessAeKeyPacketCodec.read(buffer));
        }

        private static void handle(Request packet, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = (context.player() instanceof ServerPlayer sender ? sender : null);
                if (player != null && REQUEST_LIMITER.tryAcquire(player, player.serverLevel().getGameTime())) {
                    WirelessTerminalCraftingMenuService.open(player, packet.key);
                }
            });

        }
    }

    public record ExtractionRequest(AEKey key) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ExtractionRequest> TYPE = new CustomPacketPayload.Type<>(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gtlcore", "jei_wireless_terminal_order/_extraction_request"));

        @Override
        public CustomPacketPayload.Type<ExtractionRequest> type() { return TYPE; }


        private static void encode(ExtractionRequest packet, RegistryFriendlyByteBuf buffer) {
            WirelessAeKeyPacketCodec.write(buffer, packet.key);
        }

        private static ExtractionRequest decode(RegistryFriendlyByteBuf buffer) {
            return new ExtractionRequest(WirelessAeKeyPacketCodec.read(buffer));
        }

        private static void handle(ExtractionRequest packet, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = (context.player() instanceof ServerPlayer sender ? sender : null);
                if (player != null && REQUEST_LIMITER.tryAcquire(player, player.serverLevel().getGameTime())) {
                    WirelessTerminalExtractionService.extractToCursor(player, packet.key);
                }
            });

        }
    }
}
