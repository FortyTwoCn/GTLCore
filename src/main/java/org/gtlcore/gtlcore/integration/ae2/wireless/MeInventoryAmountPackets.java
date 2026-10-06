package org.gtlcore.gtlcore.integration.ae2.wireless;

import org.gtlcore.gtlcore.integration.ae2.MeInventoryAmountService;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Optional;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class MeInventoryAmountPackets {

    private static final int MAX_REQUESTS_PER_WINDOW = 20;
    private static final long REQUEST_WINDOW_TICKS = 20;
    private static final MeInventoryRequestLimiter<ServerPlayer> REQUEST_LIMITER = new MeInventoryRequestLimiter<>(MAX_REQUESTS_PER_WINDOW, REQUEST_WINDOW_TICKS);

    private MeInventoryAmountPackets() {}

    public static void register(PayloadRegistrar registrar) {
        org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAePackets.registerPacket(registrar, Request.TYPE, Request::encode, Request::decode, Request::handle, PacketFlow.SERVERBOUND);
        org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAePackets.registerPacket(registrar, Response.TYPE, Response::encode, Response::decode, Response::handle, PacketFlow.CLIENTBOUND);
    }

    public static void sendRequest(AEKey key) {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new Request(key));
    }

    public record Request(AEKey key) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Request> TYPE = new CustomPacketPayload.Type<>(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gtlcore", "me_inventory_amount/request"));

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
                if (player == null) {
                    return;
                }
                if (!REQUEST_LIMITER.tryAcquire(player, player.serverLevel().getGameTime())) {
                    return;
                }
                MeInventoryAmountService.Result result = MeInventoryAmountService.query(player, packet.key);
                PacketDistributor.sendToPlayer(player,
                        new Response(packet.key, result.available(), result.amount()));
            });

        }
    }

    public record Response(AEKey key, boolean available, BigInteger amount) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Response> TYPE = new CustomPacketPayload.Type<>(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gtlcore", "me_inventory_amount/response"));

        @Override
        public CustomPacketPayload.Type<Response> type() { return TYPE; }


        public Response {
            amount = available ? amount.max(BigInteger.ZERO) : BigInteger.ZERO;
        }

        private static void encode(Response packet, RegistryFriendlyByteBuf buffer) {
            WirelessAeKeyPacketCodec.write(buffer, packet.key);
            buffer.writeBoolean(packet.available);
            if (packet.available) {
                buffer.writeByteArray(packet.amount.toByteArray());
            }
        }

        private static Response decode(RegistryFriendlyByteBuf buffer) {
            AEKey key = WirelessAeKeyPacketCodec.read(buffer);
            boolean available = buffer.readBoolean();
            BigInteger amount = available ? new BigInteger(buffer.readByteArray(32)) : BigInteger.ZERO;
            return new Response(key, available, amount);
        }

        private static void handle(Response packet, IPayloadContext context) {
            context.enqueueWork(() -> org.gtlcore.gtlcore.client.ae2.wireless.WirelessAeClientPacketHandler
                            .handleMeInventoryAmount(packet));

        }
    }
}
