package org.gtlcore.gtlcore.integration.ae2.graph;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.config.ConfigHolder;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAePackets;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.core.network.clientbound.CraftConfirmPlanPacket;
import appeng.hooks.ticking.TickHandler;
import appeng.menu.me.crafting.CraftConfirmMenu;

import java.util.*;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class GraphRingPackets {

    private static final Map<ServerPlayer, long[]> RATES = new WeakHashMap<>();

    private GraphRingPackets() {}

    public static void register(PayloadRegistrar registrar) {
        org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAePackets.registerPacket(registrar, Request.TYPE, Request::write, Request::read, Request::handle, PacketFlow.SERVERBOUND);
        org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAePackets.registerPacket(registrar, Response.TYPE, Response::write, Response::read, Response::handle, PacketFlow.CLIENTBOUND);
        org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAePackets.registerPacket(registrar, Failure.TYPE, Failure::write, Failure::read, Failure::handle, PacketFlow.CLIENTBOUND);
    }

    public record Request(int container, UUID plan, int offset) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Request> TYPE = new CustomPacketPayload.Type<>(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gtlcore", "graph_ring/request"));

        @Override
        public CustomPacketPayload.Type<Request> type() { return TYPE; }


        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(container);
            buffer.writeUUID(plan);
            buffer.writeVarInt(offset);
        }

        private static Request read(RegistryFriendlyByteBuf buffer) {
            return new Request(buffer.readVarInt(), buffer.readUUID(), buffer.readVarInt());
        }

        private void handle(IPayloadContext context) {
            context.enqueueWork(() -> {
                var player = (context.player() instanceof ServerPlayer sender ? sender : null);
                if (player == null || !(player.containerMenu instanceof CraftConfirmMenu menu) || menu.containerId != container || !menu.isValidMenu()) return;
                if (offset < 0 || offset > GraphRingView.MAX_ROWS) return;
                long tick = TickHandler.instance().getCurrentTick();
                long[] rate = RATES.computeIfAbsent(player, ignored -> new long[2]);
                if (rate[0] != tick) {
                    rate[0] = tick;
                    rate[1] = 0;
                }
                if (++rate[1] > 4) {
                    failure(player, "RATE_LIMIT");
                    return;
                }
                var selected = ((GraphPlanMenu) menu).gtlcore$graphPlan();
                if (selected == null || !selected.id().equals(plan)) {
                    planChanged(player, menu);
                    return;
                }
                long start = System.nanoTime();
                var server = player.getServer();
                if (server == null) return;
                selected.displayAsync().whenComplete((view, error) -> server.execute(() -> {
                    if (player.containerMenu != menu || !menu.isValidMenu()) return;
                    if (((GraphPlanMenu) menu).gtlcore$graphPlan() != selected) {
                        planChanged(player, menu);
                        return;
                    }
                    if (error != null) {
                        failure(player, "VIEW_LIMIT");
                        GTLCore.LOGGER.warn("Crafting Ring view {} could not be built", plan, error);
                        return;
                    }
                    GraphRingView.Page page;
                    try {
                        page = view.page(offset);
                    } catch (IllegalArgumentException e) {
                        failure(player, "INVALID_PAGE");
                        return;
                    }
                    if (offset == 0 && ConfigHolder.INSTANCE.ae2GraphDiagnosticLogging)
                        GTLCore.LOGGER.info("[Graph Crafting] view={} rows={} first_page_ms={}", plan, page.total(), (System.nanoTime() - start) / 1_000_000.0);
                    PacketDistributor.sendToPlayer(player, new Response(container, page));
                }));
            });

        }

        private void planChanged(ServerPlayer player, CraftConfirmMenu menu) {
            var current = ((GraphPlanMenu) menu).gtlcore$graphPlan();
            var summary = menu.getPlan();
            if (current != null && summary instanceof GraphPlanSummaryView view && current.id().equals(view.gtlcore$graphPlanId()))
                PacketDistributor.sendToPlayer(player, new CraftConfirmPlanPacket(summary));
            failure(player, "PLAN_CHANGED");
        }

        private void failure(ServerPlayer player, String reason) {
            PacketDistributor.sendToPlayer(player, new Failure(container, plan, reason));
        }
    }

    public record Failure(int container, UUID plan, String reason) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Failure> TYPE = new CustomPacketPayload.Type<>(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gtlcore", "graph_ring/failure"));

        @Override
        public CustomPacketPayload.Type<Failure> type() { return TYPE; }


        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(container);
            buffer.writeUUID(plan);
            buffer.writeUtf(reason, 64);
        }

        private static Failure read(RegistryFriendlyByteBuf buffer) {
            return new Failure(buffer.readVarInt(), buffer.readUUID(), buffer.readUtf(64));
        }

        private void handle(IPayloadContext context) {
            context.enqueueWork(() -> org.gtlcore.gtlcore.client.ae2.graph.CraftingRingScreen.fail(this));

        }
    }

    public record Response(int container, GraphRingView.Page page) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Response> TYPE = new CustomPacketPayload.Type<>(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gtlcore", "graph_ring/response"));

        @Override
        public CustomPacketPayload.Type<Response> type() { return TYPE; }


        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(container);
            GraphRingView.write(page, buffer);
        }

        private static Response read(RegistryFriendlyByteBuf buffer) {
            return new Response(buffer.readVarInt(), GraphRingView.read(buffer));
        }

        private void handle(IPayloadContext context) {
            context.enqueueWork(() -> org.gtlcore.gtlcore.client.ae2.graph.CraftingRingScreen.receive(this));

        }
    }
}
