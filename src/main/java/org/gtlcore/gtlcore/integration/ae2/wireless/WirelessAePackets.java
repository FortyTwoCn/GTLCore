package org.gtlcore.gtlcore.integration.ae2.wireless;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.client.gui.PatterEncodingTermMenuModify;
import org.gtlcore.gtlcore.integration.ae2.chamber.MEChamberConfigurator;
import org.gtlcore.gtlcore.integration.ae2.chamber.MEChamberManagerTerminalMenu;
import org.gtlcore.gtlcore.integration.ae2.emitter.EmitterManagerTerminalMenu;
import org.gtlcore.gtlcore.integration.ae2.pattern.PatternQuickUploadSelectionMenu;
import org.gtlcore.gtlcore.integration.ae2.throughput.ThroughputMonitorTerminalMenu;
import org.gtlcore.gtlcore.integration.ae2.throughput.ThroughputMonitorUpdateInterval;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.api.stacks.AEKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class WirelessAePackets {

    private static final String PROTOCOL_VERSION = "28";
    private static final MeInventoryRequestLimiter<ServerPlayer> TARGET_REQUEST_LIMITER = new MeInventoryRequestLimiter<>(8, 20);
    private WirelessAePackets() {}

    public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registerPacket(registrar, UnbindWithToolPacket.TYPE, (packet, buffer) -> buffer.writeBlockPos(packet.pos()), buffer -> new UnbindWithToolPacket(buffer.readBlockPos()), UnbindWithToolPacket::handle, PacketFlow.SERVERBOUND);
        registerPacket(registrar, ClearBindingToolPacket.TYPE, (packet, buffer) -> {}, buffer -> new ClearBindingToolPacket(), ClearBindingToolPacket::handle, PacketFlow.SERVERBOUND);
        registerPacket(registrar, RenameNetworkPacket.TYPE, RenameNetworkPacket::encode, RenameNetworkPacket::decode, RenameNetworkPacket::handle, PacketFlow.SERVERBOUND);
        registerPacket(registrar, SetFavoriteNetworkPacket.TYPE, SetFavoriteNetworkPacket::encode, SetFavoriteNetworkPacket::decode, SetFavoriteNetworkPacket::handle, PacketFlow.SERVERBOUND);
        registerPacket(registrar, ConnectTargetPacket.TYPE, ConnectTargetPacket::encode, ConnectTargetPacket::decode, ConnectTargetPacket::handle, PacketFlow.SERVERBOUND);
        registerPacket(registrar, OpenTargetMenuPacket.TYPE, OpenTargetMenuPacket::encode, OpenTargetMenuPacket::decode, OpenTargetMenuPacket::handle, PacketFlow.SERVERBOUND);
        registerPacket(registrar, OpenNormalTargetMenuPacket.TYPE, OpenNormalTargetMenuPacket::encode, OpenNormalTargetMenuPacket::decode, OpenNormalTargetMenuPacket::handle, PacketFlow.SERVERBOUND);
        registerPacket(registrar, RequestTargetNetworksPacket.TYPE, RequestTargetNetworksPacket::encode, RequestTargetNetworksPacket::decode, RequestTargetNetworksPacket::handle, PacketFlow.SERVERBOUND);
        registerPacket(registrar, SyncTargetNetworksPacket.TYPE, SyncTargetNetworksPacket::encode, SyncTargetNetworksPacket::decode, SyncTargetNetworksPacket::handle, PacketFlow.CLIENTBOUND);
        registerPacket(registrar, OpenPatternQuickUploadSelectionPacket.TYPE, OpenPatternQuickUploadSelectionPacket::encode, OpenPatternQuickUploadSelectionPacket::decode, OpenPatternQuickUploadSelectionPacket::handle, PacketFlow.CLIENTBOUND);
        registerPacket(registrar, SelectPatternQuickUploadTargetPacket.TYPE, SelectPatternQuickUploadTargetPacket::encode, SelectPatternQuickUploadTargetPacket::decode, SelectPatternQuickUploadTargetPacket::handle, PacketFlow.SERVERBOUND);
        registerPacket(registrar, PatternQuickUploadDuplicatePacket.TYPE, PatternQuickUploadDuplicatePacket::encode, PatternQuickUploadDuplicatePacket::decode, PatternQuickUploadDuplicatePacket::handle, PacketFlow.CLIENTBOUND);
        registerPacket(registrar, SyncThroughputMonitorTerminalPacket.TYPE, SyncThroughputMonitorTerminalPacket::encode, SyncThroughputMonitorTerminalPacket::decode, SyncThroughputMonitorTerminalPacket::handle, PacketFlow.CLIENTBOUND);
        registerPacket(registrar, SetThroughputMonitorSourceTrackingPacket.TYPE, SetThroughputMonitorSourceTrackingPacket::encode, SetThroughputMonitorSourceTrackingPacket::decode, SetThroughputMonitorSourceTrackingPacket::handle, PacketFlow.SERVERBOUND);
        registerPacket(registrar, SetThroughputMonitorUpdateIntervalPacket.TYPE, SetThroughputMonitorUpdateIntervalPacket::encode, SetThroughputMonitorUpdateIntervalPacket::decode, SetThroughputMonitorUpdateIntervalPacket::handle, PacketFlow.SERVERBOUND);
        registerPacket(registrar, SyncThroughputMonitorSourcesPacket.TYPE, SyncThroughputMonitorSourcesPacket::encode, SyncThroughputMonitorSourcesPacket::decode, SyncThroughputMonitorSourcesPacket::handle, PacketFlow.CLIENTBOUND);
        registerPacket(registrar, SyncEmitterManagerTerminalPacket.TYPE, SyncEmitterManagerTerminalPacket::encode, SyncEmitterManagerTerminalPacket::decode, SyncEmitterManagerTerminalPacket::handle, PacketFlow.CLIENTBOUND);
        registerPacket(registrar, SetEmitterSettingPacket.TYPE, SetEmitterSettingPacket::encode, SetEmitterSettingPacket::decode, SetEmitterSettingPacket::handle, PacketFlow.SERVERBOUND);
        registerPacket(registrar, SetEmitterValuePacket.TYPE, SetEmitterValuePacket::encode, SetEmitterValuePacket::decode, SetEmitterValuePacket::handle, PacketFlow.SERVERBOUND);
        registerPacket(registrar, SelectEmitterPacket.TYPE, SelectEmitterPacket::encode, SelectEmitterPacket::decode, SelectEmitterPacket::handle, PacketFlow.SERVERBOUND);
        registerPacket(registrar, SyncMEChamberManagerEntriesPacket.TYPE, SyncMEChamberManagerEntriesPacket::encode, SyncMEChamberManagerEntriesPacket::decode, SyncMEChamberManagerEntriesPacket::handle, PacketFlow.CLIENTBOUND);
        registerPacket(registrar, SelectMEChamberPacket.TYPE, SelectMEChamberPacket::encode, SelectMEChamberPacket::decode, SelectMEChamberPacket::handle, PacketFlow.SERVERBOUND);
        registerPacket(registrar, SetMEChamberSlotAmountPacket.TYPE, SetMEChamberSlotAmountPacket::encode, SetMEChamberSlotAmountPacket::decode, SetMEChamberSlotAmountPacket::handle, PacketFlow.SERVERBOUND);
        registerPacket(registrar, SetMEChamberSlotConfigPacket.TYPE, SetMEChamberSlotConfigPacket::encode, SetMEChamberSlotConfigPacket::decode, SetMEChamberSlotConfigPacket::handle, PacketFlow.SERVERBOUND);
        registerPacket(registrar, SetMEChamberControlPacket.TYPE, SetMEChamberControlPacket::encode, SetMEChamberControlPacket::decode, SetMEChamberControlPacket::handle, PacketFlow.SERVERBOUND);
        registerPacket(registrar, MEChamberConfiguratorActionPacket.TYPE, MEChamberConfiguratorActionPacket::encode, MEChamberConfiguratorActionPacket::decode, MEChamberConfiguratorActionPacket::handle, PacketFlow.SERVERBOUND);
        registerPacket(registrar, SyncMEChamberManagerContentsPacket.TYPE, SyncMEChamberManagerContentsPacket::encode, SyncMEChamberManagerContentsPacket::decode, SyncMEChamberManagerContentsPacket::handle, PacketFlow.CLIENTBOUND);
        registerPacket(registrar, SyncMEChamberSnapshotPacket.TYPE, SyncMEChamberSnapshotPacket::encode, SyncMEChamberSnapshotPacket::decode, SyncMEChamberSnapshotPacket::handle, PacketFlow.CLIENTBOUND);
        MeInventoryAmountPackets.register(registrar);
        FastCellDisplayPackets.register(registrar);
        org.gtlcore.gtlcore.integration.ae2.graph.GraphRingPackets.register(registrar);
        JeiWirelessTerminalOrderPackets.register(registrar);
        JeiPatternQuery.register(registrar);
    }

    public record ClearBindingToolPacket() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ClearBindingToolPacket> TYPE = new CustomPacketPayload.Type<>(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gtlcore", "wireless_ae/clear_binding_tool"));

        @Override
        public CustomPacketPayload.Type<ClearBindingToolPacket> type() { return TYPE; }


        private static void handle(ClearBindingToolPacket packet, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = (context.player() instanceof ServerPlayer sender ? sender : null);
                if (player != null && !player.isSpectator()) {
                    WirelessNetworkBindingToolItem.clearSavedNetwork(player);
                }
            });

        }
    }

    public record UnbindWithToolPacket(BlockPos pos) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<UnbindWithToolPacket> TYPE = new CustomPacketPayload.Type<>(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gtlcore", "wireless_ae/unbind_with_tool"));

        @Override
        public CustomPacketPayload.Type<UnbindWithToolPacket> type() { return TYPE; }


        private static void handle(UnbindWithToolPacket packet, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = (context.player() instanceof ServerPlayer sender ? sender : null);
                if (player != null && player.canInteractWithBlock(packet.pos(), 0)) {
                    WirelessAeNetworkRuntime.unbindWithTool(player, packet.pos());
                }
            });

        }
    }

    public static <P extends CustomPacketPayload> void registerPacket(PayloadRegistrar registrar,
            CustomPacketPayload.Type<P> type, BiConsumer<P, RegistryFriendlyByteBuf> encoder,
            Function<RegistryFriendlyByteBuf, P> decoder, BiConsumer<P, IPayloadContext> handler,
            PacketFlow direction) {
        StreamCodec<RegistryFriendlyByteBuf, P> codec = StreamCodec.of(
                (buffer, packet) -> encoder.accept(packet, buffer), decoder::apply);
        if (direction == PacketFlow.SERVERBOUND) {
            registrar.playToServer(type, codec, handler::accept);
        } else {
            registrar.playToClient(type, codec, handler::accept);
        }
    }

    private static void dispatchClient(IPayloadContext context, Runnable handler) {
        context.enqueueWork(handler);

    }

    public record RenameNetworkPacket(BlockPos corePos, String name) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<RenameNetworkPacket> TYPE = new CustomPacketPayload.Type<>(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gtlcore", "wireless_ae/rename_network"));

        @Override
        public CustomPacketPayload.Type<RenameNetworkPacket> type() { return TYPE; }


        private static void encode(RenameNetworkPacket packet, RegistryFriendlyByteBuf buffer) {
            buffer.writeBlockPos(packet.corePos);
            buffer.writeUtf(packet.name);
        }

        private static RenameNetworkPacket decode(RegistryFriendlyByteBuf buffer) {
            return new RenameNetworkPacket(buffer.readBlockPos(), buffer.readUtf(32));
        }

        private static void handle(RenameNetworkPacket packet, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = (context.player() instanceof ServerPlayer sender ? sender : null);
                if (player == null || isCloseEnough(player, packet.corePos)) {
                    return;
                }

                ServerLevel level = player.serverLevel();
                BlockEntity blockEntity = level.getBlockEntity(packet.corePos);
                if (!(blockEntity instanceof WirelessNetworkCoreBlockEntity core)) {
                    return;
                }

                WirelessAeSavedData data = WirelessAeSavedData.get(level.getServer());
                UUID frequency = core.getFrequency();
                if (!WirelessAeNetworkRuntime.canAccessNetwork(player, frequency)) {
                    return;
                }
                data.setCore(frequency, GlobalPos.of(level.dimension(), packet.corePos));
                data.setNetworkName(frequency, packet.name);
                WirelessAeNetworkRuntime.requestReconnect(frequency);
                player.displayClientMessage(
                        Component.translatable("message.gtlcore.wireless_core.name_saved", data.getNetworkName(frequency)),
                        true);
            });

        }
    }

    public record SetFavoriteNetworkPacket(BlockPos bookmarkPos, UUID frequency) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SetFavoriteNetworkPacket> TYPE = new CustomPacketPayload.Type<>(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gtlcore", "wireless_ae/set_favorite_network"));

        @Override
        public CustomPacketPayload.Type<SetFavoriteNetworkPacket> type() { return TYPE; }


        private static void encode(SetFavoriteNetworkPacket packet, RegistryFriendlyByteBuf buffer) {
            buffer.writeBlockPos(packet.bookmarkPos);
            buffer.writeUUID(packet.frequency);
        }

        private static SetFavoriteNetworkPacket decode(RegistryFriendlyByteBuf buffer) {
            return new SetFavoriteNetworkPacket(buffer.readBlockPos(), buffer.readUUID());
        }

        private static void handle(SetFavoriteNetworkPacket packet, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = (context.player() instanceof ServerPlayer sender ? sender : null);
                if (player == null || isCloseEnough(player, packet.bookmarkPos)) {
                    return;
                }

                ServerLevel level = player.serverLevel();
                if (!(level.getBlockEntity(packet.bookmarkPos) instanceof WirelessNetworkBookmarkBlockEntity)) {
                    return;
                }

                WirelessAeSavedData data = WirelessAeSavedData.get(level.getServer());
                if (!data.getFrequencies().contains(packet.frequency) || !WirelessAeNetworkRuntime.canAccessNetwork(player, packet.frequency)) {
                    return;
                }
                boolean removeFavorite = packet.frequency.equals(data.getFavoriteNetwork(player));
                String networkName = data.getNetworkName(packet.frequency);
                data.setFavoriteNetwork(player, removeFavorite ? null : packet.frequency);
                player.displayClientMessage(
                        Component.translatable(
                                removeFavorite ?
                                        "message.gtlcore.wireless_bookmark.favorite_removed" :
                                        "message.gtlcore.wireless_bookmark.saved",
                                networkName),
                        true);
            });

        }
    }

    public record ConnectTargetPacket(BlockPos targetPos, Direction targetSide, Vec3 hitLocation,
                                      UUID frequency, boolean disconnect) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ConnectTargetPacket> TYPE = new CustomPacketPayload.Type<>(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gtlcore", "wireless_ae/connect_target"));

        @Override
        public CustomPacketPayload.Type<ConnectTargetPacket> type() { return TYPE; }


        public ConnectTargetPacket(BlockPos targetPos, UUID frequency, boolean disconnect) {
            this(targetPos, null, null, frequency, disconnect);
        }

        private static void encode(ConnectTargetPacket packet, RegistryFriendlyByteBuf buffer) {
            buffer.writeBlockPos(packet.targetPos);
            writeDirection(buffer, packet.targetSide);
            writeVec3(buffer, packet.hitLocation);
            buffer.writeUUID(packet.frequency);
            buffer.writeBoolean(packet.disconnect);
        }

        private static ConnectTargetPacket decode(RegistryFriendlyByteBuf buffer) {
            return new ConnectTargetPacket(
                    buffer.readBlockPos(),
                    readDirection(buffer),
                    readVec3(buffer),
                    buffer.readUUID(),
                    buffer.readBoolean());
        }

        private static void handle(ConnectTargetPacket packet, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = (context.player() instanceof ServerPlayer sender ? sender : null);
                if (player == null || isCloseEnough(player, packet.targetPos)) {
                    return;
                }
                if (!TARGET_REQUEST_LIMITER.tryAcquire(player, player.serverLevel().getGameTime())) return;

                ServerLevel level = player.serverLevel();
                WirelessAeSavedData.MemberKey target = WirelessAeNetworkRuntime.resolveWirelessTarget(
                        level,
                        packet.targetPos,
                        packet.targetSide,
                        packet.hitLocation);
                BlockPos targetPos = target.blockPos();
                GlobalPos targetGlobalPos = target.pos();
                WirelessAeSavedData data = WirelessAeSavedData.get(level.getServer());
                if (!WirelessAeNetworkRuntime.canAccessNetwork(player, packet.frequency)) {
                    return;
                }
                UUID currentNetwork = data.getMemberNetwork(target);
                UUID wiredNetwork = WirelessAeNetworkRuntime.findWiredNetworkFrequency(level.getServer(), target);
                UUID connectedNetwork = wiredNetwork == null ? WirelessAeNetworkRuntime.findConnectedNetworkFrequency(level.getServer(), target) : wiredNetwork;
                boolean canModifyCurrentConnection = wiredNetwork == null && connectedNetwork != null && connectedNetwork.equals(currentNetwork) && WirelessAeNetworkRuntime.hasWirelessConnection(connectedNetwork, target);

                if (packet.disconnect) {
                    if (!canModifyCurrentConnection || !packet.frequency.equals(currentNetwork)) {
                        return;
                    }
                    for (UUID removedNetwork : data.removeMembersAt(targetGlobalPos)) {
                        WirelessAeNetworkRuntime.disconnectMembersAt(removedNetwork, targetGlobalPos);
                    }
                    player.displayClientMessage(
                            Component.translatable("message.gtlcore.wireless_target.disconnected"),
                            true);
                    return;
                }

                if (connectedNetwork != null && !canModifyCurrentConnection) {
                    return;
                }

                if (!WirelessAeNetworkRuntime.canBindAsWirelessTarget(level, targetPos, target.side())) {
                    player.displayClientMessage(
                            Component.translatable("message.gtlcore.wireless_target.invalid_target"),
                            true);
                    return;
                }

                WirelessNetworkCoreBlockEntity core = WirelessAeNetworkRuntime.getLoadedCore(
                        level.getServer(),
                        packet.frequency);
                if (core == null) {
                    player.displayClientMessage(
                            Component.translatable("message.gtlcore.wireless_target.missing_core"),
                            true);
                    return;
                }
                if (!core.isLinkedToAeNetwork()) {
                    player.displayClientMessage(
                            Component.translatable("message.gtlcore.wireless_target.core_not_connected"),
                            true);
                    return;
                }

                for (UUID removedNetwork : data.removeMembersAt(targetGlobalPos)) {
                    WirelessAeNetworkRuntime.disconnectMembersAt(removedNetwork, targetGlobalPos);
                }

                WirelessAeNetworkRuntime.ConnectionResult result = WirelessAeNetworkRuntime.connectMemberNow(
                        level.getServer(),
                        packet.frequency,
                        target);
                data.addMember(packet.frequency, target);
                WirelessAeNetworkRuntime.requestReconnect(packet.frequency);

                boolean pending = result == WirelessAeNetworkRuntime.ConnectionResult.TARGET_MISSING || result == WirelessAeNetworkRuntime.ConnectionResult.FAILED;
                player.displayClientMessage(
                        Component.translatable(
                                pending ? "message.gtlcore.wireless_target.linked_target_pending" : "message.gtlcore.wireless_target.linked_target",
                                data.getNetworkName(packet.frequency)),
                        true);
            });

        }
    }

    public record OpenTargetMenuPacket(BlockPos targetPos, Direction targetSide, Vec3 hitLocation) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<OpenTargetMenuPacket> TYPE = new CustomPacketPayload.Type<>(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gtlcore", "wireless_ae/open_target_menu"));

        @Override
        public CustomPacketPayload.Type<OpenTargetMenuPacket> type() { return TYPE; }


        public OpenTargetMenuPacket(BlockPos targetPos) {
            this(targetPos, null, null);
        }

        private static void encode(OpenTargetMenuPacket packet, RegistryFriendlyByteBuf buffer) {
            buffer.writeBlockPos(packet.targetPos);
            writeDirection(buffer, packet.targetSide);
            writeVec3(buffer, packet.hitLocation);
        }

        private static OpenTargetMenuPacket decode(RegistryFriendlyByteBuf buffer) {
            return new OpenTargetMenuPacket(buffer.readBlockPos(), readDirection(buffer), readVec3(buffer));
        }

        private static void handle(OpenTargetMenuPacket packet, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = (context.player() instanceof ServerPlayer sender ? sender : null);
                if (player == null || isCloseEnough(player, packet.targetPos)) {
                    return;
                }
                if (!TARGET_REQUEST_LIMITER.tryAcquire(player, player.serverLevel().getGameTime())) return;

                ServerLevel level = player.serverLevel();
                WirelessAeSavedData.MemberKey target = WirelessAeNetworkRuntime.resolveWirelessTarget(
                        level,
                        packet.targetPos,
                        packet.targetSide,
                        packet.hitLocation);
                if (WirelessAeNetworkRuntime.canBindAsWirelessTarget(level, target.blockPos(), target.side())) {
                    WirelessAeTargetMenu.open(player, level, packet.targetPos, packet.targetSide, packet.hitLocation);
                }
            });

        }
    }

    public record OpenNormalTargetMenuPacket(BlockPos targetPos) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<OpenNormalTargetMenuPacket> TYPE = new CustomPacketPayload.Type<>(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gtlcore", "wireless_ae/open_normal_target_menu"));

        @Override
        public CustomPacketPayload.Type<OpenNormalTargetMenuPacket> type() { return TYPE; }


        private static void encode(OpenNormalTargetMenuPacket packet, RegistryFriendlyByteBuf buffer) {
            buffer.writeBlockPos(packet.targetPos);
        }

        private static OpenNormalTargetMenuPacket decode(RegistryFriendlyByteBuf buffer) {
            return new OpenNormalTargetMenuPacket(buffer.readBlockPos());
        }

        private static void handle(OpenNormalTargetMenuPacket packet, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = (context.player() instanceof ServerPlayer sender ? sender : null);
                if (player == null || isCloseEnough(player, packet.targetPos)) {
                    return;
                }
                ServerLevel level = player.serverLevel();
                if (!level.hasChunkAt(packet.targetPos)) {
                    return;
                }

                BlockState state = level.getBlockState(packet.targetPos);
                BlockHitResult hit = new BlockHitResult(
                        Vec3.atCenterOf(packet.targetPos),
                        Direction.UP,
                        packet.targetPos,
                        false);
                ItemInteractionResult result = state.useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
                if (result.consumesAction() || result == ItemInteractionResult.FAIL) return;
                if (result == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION &&
                        state.useWithoutItem(level, player, hit).consumesAction()) return;

                MenuProvider provider = state.getMenuProvider(level, packet.targetPos);
                if (provider != null) {
                    player.openMenu(provider);
                }
            });

        }
    }

    public record RequestTargetNetworksPacket(BlockPos targetPos, Direction targetSide, Vec3 hitLocation) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<RequestTargetNetworksPacket> TYPE = new CustomPacketPayload.Type<>(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gtlcore", "wireless_ae/request_target_networks"));

        @Override
        public CustomPacketPayload.Type<RequestTargetNetworksPacket> type() { return TYPE; }


        public RequestTargetNetworksPacket(BlockPos targetPos) {
            this(targetPos, null, null);
        }

        private static void encode(RequestTargetNetworksPacket packet, RegistryFriendlyByteBuf buffer) {
            buffer.writeBlockPos(packet.targetPos);
            writeDirection(buffer, packet.targetSide);
            writeVec3(buffer, packet.hitLocation);
        }

        private static RequestTargetNetworksPacket decode(RegistryFriendlyByteBuf buffer) {
            return new RequestTargetNetworksPacket(buffer.readBlockPos(), readDirection(buffer), readVec3(buffer));
        }

        private static void handle(RequestTargetNetworksPacket packet, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = (context.player() instanceof ServerPlayer sender ? sender : null);
                if (player == null || isCloseEnough(player, packet.targetPos)) {
                    return;
                }
                if (!TARGET_REQUEST_LIMITER.tryAcquire(player, player.serverLevel().getGameTime())) return;

                ServerLevel level = player.serverLevel();
                WirelessAeSavedData.MemberKey target = WirelessAeNetworkRuntime.resolveWirelessTarget(
                        level,
                        packet.targetPos,
                        packet.targetSide,
                        packet.hitLocation);
                if (!WirelessAeNetworkRuntime.canBindAsWirelessTarget(level, target.blockPos(), target.side())) {
                    return;
                }

                PacketDistributor.sendToPlayer(player,
                        new SyncTargetNetworksPacket(packet.targetPos, buildTargetEntries(player, target)));
            });

        }
    }

    public record SyncTargetNetworksPacket(BlockPos targetPos, List<TargetNetworkEntry> entries) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SyncTargetNetworksPacket> TYPE = new CustomPacketPayload.Type<>(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gtlcore", "wireless_ae/sync_target_networks"));

        @Override
        public CustomPacketPayload.Type<SyncTargetNetworksPacket> type() { return TYPE; }


        private static void encode(SyncTargetNetworksPacket packet, RegistryFriendlyByteBuf buffer) {
            buffer.writeBlockPos(packet.targetPos);
            buffer.writeVarInt(packet.entries.size());
            for (TargetNetworkEntry entry : packet.entries) {
                buffer.writeUUID(entry.frequency());
                buffer.writeUtf(entry.name());
                buffer.writeBoolean(entry.connected());
                buffer.writeBoolean(entry.disconnectable());
            }
        }

        private static SyncTargetNetworksPacket decode(RegistryFriendlyByteBuf buffer) {
            BlockPos targetPos = buffer.readBlockPos();
            int size = buffer.readVarInt();
            List<TargetNetworkEntry> entries = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                entries.add(new TargetNetworkEntry(
                        buffer.readUUID(),
                        buffer.readUtf(32),
                        buffer.readBoolean(),
                        buffer.readBoolean()));
            }
            return new SyncTargetNetworksPacket(targetPos, entries);
        }

        private static void handle(SyncTargetNetworksPacket packet, IPayloadContext context) {
            dispatchClient(context, () -> org.gtlcore.gtlcore.client.ae2.wireless.WirelessAeClientPacketHandler
                    .handleTargetNetworks(packet));
        }
    }

    public record TargetNetworkEntry(UUID frequency, String name, boolean connected, boolean disconnectable) {}

    public record OpenPatternQuickUploadSelectionPacket(ItemStack patternStack,
                                                        List<PatternQuickUploadSelectionMenu.Entry> entries) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<OpenPatternQuickUploadSelectionPacket> TYPE = new CustomPacketPayload.Type<>(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gtlcore", "wireless_ae/open_pattern_quick_upload_selection"));

        @Override
        public CustomPacketPayload.Type<OpenPatternQuickUploadSelectionPacket> type() { return TYPE; }


        private static void encode(OpenPatternQuickUploadSelectionPacket packet, RegistryFriendlyByteBuf buffer) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, packet.patternStack);
            writePatternQuickUploadEntries(buffer, packet.entries);
        }

        private static OpenPatternQuickUploadSelectionPacket decode(RegistryFriendlyByteBuf buffer) {
            return new OpenPatternQuickUploadSelectionPacket(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer), readPatternQuickUploadEntries(buffer));
        }

        private static void handle(OpenPatternQuickUploadSelectionPacket packet,
                                   IPayloadContext context) {
            dispatchClient(context, () -> org.gtlcore.gtlcore.client.ae2.wireless.WirelessAeClientPacketHandler
                    .handlePatternQuickUploadSelection(packet));
        }
    }

    public record SelectPatternQuickUploadTargetPacket(int index) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SelectPatternQuickUploadTargetPacket> TYPE = new CustomPacketPayload.Type<>(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gtlcore", "wireless_ae/select_pattern_quick_upload_target"));

        @Override
        public CustomPacketPayload.Type<SelectPatternQuickUploadTargetPacket> type() { return TYPE; }


        private static void encode(SelectPatternQuickUploadTargetPacket packet, RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.index);
        }

        private static SelectPatternQuickUploadTargetPacket decode(RegistryFriendlyByteBuf buffer) {
            return new SelectPatternQuickUploadTargetPacket(buffer.readVarInt());
        }

        private static void handle(SelectPatternQuickUploadTargetPacket packet,
                                   IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = (context.player() instanceof ServerPlayer sender ? sender : null);
                if (player != null && player.containerMenu instanceof PatternQuickUploadSelectionMenu menu) {
                    menu.select(player, packet.index);
                } else if (player != null && player.containerMenu instanceof PatterEncodingTermMenuModify menuModify) {
                    menuModify.gTLCore$selectQuickUploadTarget(packet.index);
                }
            });

        }
    }

    public static void sendPatternQuickUploadDuplicate(ServerPlayer player, Component targetName) {
        PacketDistributor.sendToPlayer(player,
                new PatternQuickUploadDuplicatePacket(targetName));
    }

    public record PatternQuickUploadDuplicatePacket(Component targetName) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<PatternQuickUploadDuplicatePacket> TYPE = new CustomPacketPayload.Type<>(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gtlcore", "wireless_ae/pattern_quick_upload_duplicate"));

        @Override
        public CustomPacketPayload.Type<PatternQuickUploadDuplicatePacket> type() { return TYPE; }


        private static void encode(PatternQuickUploadDuplicatePacket packet, RegistryFriendlyByteBuf buffer) {
            ComponentSerialization.STREAM_CODEC.encode(buffer, packet.targetName);
        }

        private static PatternQuickUploadDuplicatePacket decode(RegistryFriendlyByteBuf buffer) {
            return new PatternQuickUploadDuplicatePacket(ComponentSerialization.STREAM_CODEC.decode(buffer));
        }

        private static void handle(PatternQuickUploadDuplicatePacket packet,
                                   IPayloadContext context) {
            dispatchClient(context, () -> org.gtlcore.gtlcore.client.ae2.wireless.WirelessAeClientPacketHandler
                    .handlePatternQuickUploadDuplicate(packet));
        }
    }

    public record SyncThroughputMonitorTerminalPacket(
                                                      int containerId,
                                                      List<ThroughputMonitorTerminalMenu.Entry> entries) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SyncThroughputMonitorTerminalPacket> TYPE = new CustomPacketPayload.Type<>(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gtlcore", "wireless_ae/sync_throughput_monitor_terminal"));

        @Override
        public CustomPacketPayload.Type<SyncThroughputMonitorTerminalPacket> type() { return TYPE; }


        private static void encode(SyncThroughputMonitorTerminalPacket packet, RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.containerId);
            ThroughputMonitorTerminalMenu.writeEntries(buffer, packet.entries);
        }

        private static SyncThroughputMonitorTerminalPacket decode(RegistryFriendlyByteBuf buffer) {
            return new SyncThroughputMonitorTerminalPacket(
                    buffer.readVarInt(),
                    ThroughputMonitorTerminalMenu.readEntries(buffer));
        }

        private static void handle(SyncThroughputMonitorTerminalPacket packet,
                                   IPayloadContext context) {
            dispatchClient(context, () -> org.gtlcore.gtlcore.client.ae2.wireless.WirelessAeClientPacketHandler
                    .handleThroughputMonitorTerminal(packet));
        }
    }

    public record SetThroughputMonitorSourceTrackingPacket(int containerId, AEKey key, boolean track) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SetThroughputMonitorSourceTrackingPacket> TYPE = new CustomPacketPayload.Type<>(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gtlcore", "wireless_ae/set_throughput_monitor_source_tracking"));

        @Override
        public CustomPacketPayload.Type<SetThroughputMonitorSourceTrackingPacket> type() { return TYPE; }


        private static void encode(SetThroughputMonitorSourceTrackingPacket packet, RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.containerId);
            AEKey.writeKey(buffer, packet.key);
            buffer.writeBoolean(packet.track);
        }

        private static SetThroughputMonitorSourceTrackingPacket decode(RegistryFriendlyByteBuf buffer) {
            return new SetThroughputMonitorSourceTrackingPacket(
                    buffer.readVarInt(),
                    AEKey.readKey(buffer),
                    buffer.readBoolean());
        }

        private static void handle(SetThroughputMonitorSourceTrackingPacket packet,
                                   IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = (context.player() instanceof ServerPlayer sender ? sender : null);
                if (player != null && player.containerMenu instanceof ThroughputMonitorTerminalMenu menu &&
                        menu.containerId == packet.containerId) {
                    menu.trackSources(player, packet.key, packet.track);
                }
            });

        }
    }

    public record SetThroughputMonitorUpdateIntervalPacket(
                                                           int containerId,
                                                           ThroughputMonitorUpdateInterval updateInterval) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SetThroughputMonitorUpdateIntervalPacket> TYPE = new CustomPacketPayload.Type<>(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gtlcore", "wireless_ae/set_throughput_monitor_update_interval"));

        @Override
        public CustomPacketPayload.Type<SetThroughputMonitorUpdateIntervalPacket> type() { return TYPE; }


        private static void encode(SetThroughputMonitorUpdateIntervalPacket packet, RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.containerId);
            buffer.writeEnum(packet.updateInterval);
        }

        private static SetThroughputMonitorUpdateIntervalPacket decode(RegistryFriendlyByteBuf buffer) {
            return new SetThroughputMonitorUpdateIntervalPacket(
                    buffer.readVarInt(),
                    buffer.readEnum(ThroughputMonitorUpdateInterval.class));
        }

        private static void handle(SetThroughputMonitorUpdateIntervalPacket packet,
                                   IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = (context.player() instanceof ServerPlayer sender ? sender : null);
                if (player != null && player.containerMenu instanceof ThroughputMonitorTerminalMenu menu &&
                        menu.containerId == packet.containerId) {
                    menu.setUpdateInterval(player, packet.updateInterval);
                }
            });

        }
    }

    public record SyncThroughputMonitorSourcesPacket(
                                                     int containerId,
                                                     AEKey key,
                                                     List<ThroughputMonitorTerminalMenu.SourceEntry> sources) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SyncThroughputMonitorSourcesPacket> TYPE = new CustomPacketPayload.Type<>(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gtlcore", "wireless_ae/sync_throughput_monitor_sources"));

        @Override
        public CustomPacketPayload.Type<SyncThroughputMonitorSourcesPacket> type() { return TYPE; }


        private static void encode(SyncThroughputMonitorSourcesPacket packet, RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.containerId);
            AEKey.writeKey(buffer, packet.key);
            ThroughputMonitorTerminalMenu.writeSourceEntries(buffer, packet.sources);
        }

        private static SyncThroughputMonitorSourcesPacket decode(RegistryFriendlyByteBuf buffer) {
            return new SyncThroughputMonitorSourcesPacket(
                    buffer.readVarInt(),
                    AEKey.readKey(buffer),
                    ThroughputMonitorTerminalMenu.readSourceEntries(buffer));
        }

        private static void handle(SyncThroughputMonitorSourcesPacket packet,
                                   IPayloadContext context) {
            dispatchClient(context, () -> org.gtlcore.gtlcore.client.ae2.wireless.WirelessAeClientPacketHandler
                    .handleThroughputMonitorSources(packet));
        }
    }

    public record SyncEmitterManagerTerminalPacket(
                                                   int containerId,
                                                   List<EmitterManagerTerminalMenu.Entry> entries) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SyncEmitterManagerTerminalPacket> TYPE = new CustomPacketPayload.Type<>(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gtlcore", "wireless_ae/sync_emitter_manager_terminal"));

        @Override
        public CustomPacketPayload.Type<SyncEmitterManagerTerminalPacket> type() { return TYPE; }


        private static void encode(SyncEmitterManagerTerminalPacket packet, RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.containerId);
            EmitterManagerTerminalMenu.writeEntries(buffer, packet.entries);
        }

        private static SyncEmitterManagerTerminalPacket decode(RegistryFriendlyByteBuf buffer) {
            return new SyncEmitterManagerTerminalPacket(
                    buffer.readVarInt(),
                    EmitterManagerTerminalMenu.readEntries(buffer));
        }

        private static void handle(SyncEmitterManagerTerminalPacket packet,
                                   IPayloadContext context) {
            dispatchClient(context, () -> org.gtlcore.gtlcore.client.ae2.wireless.WirelessAeClientPacketHandler
                    .handleEmitterManagerTerminal(packet));
        }
    }

    public record SetEmitterSettingPacket(int containerId, EmitterManagerTerminalMenu.Address address,
                                          String settingName, String valueName) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SetEmitterSettingPacket> TYPE = new CustomPacketPayload.Type<>(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gtlcore", "wireless_ae/set_emitter_setting"));

        @Override
        public CustomPacketPayload.Type<SetEmitterSettingPacket> type() { return TYPE; }


        private static final int MAX_SETTING_NAME_LENGTH = 64;

        private static void encode(SetEmitterSettingPacket packet, RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.containerId);
            packet.address.write(buffer);
            buffer.writeUtf(packet.settingName, MAX_SETTING_NAME_LENGTH);
            buffer.writeUtf(packet.valueName, MAX_SETTING_NAME_LENGTH);
        }

        private static SetEmitterSettingPacket decode(RegistryFriendlyByteBuf buffer) {
            return new SetEmitterSettingPacket(
                    buffer.readVarInt(),
                    EmitterManagerTerminalMenu.Address.read(buffer),
                    buffer.readUtf(MAX_SETTING_NAME_LENGTH),
                    buffer.readUtf(MAX_SETTING_NAME_LENGTH));
        }

        private static void handle(SetEmitterSettingPacket packet,
                                   IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = (context.player() instanceof ServerPlayer sender ? sender : null);
                if (player != null && player.containerMenu instanceof EmitterManagerTerminalMenu menu &&
                        menu.containerId == packet.containerId) {
                    menu.setEmitterSetting(player, packet.address, packet.settingName, packet.valueName);
                }
            });

        }
    }

    /** Tells the server which emitter the terminal has selected, so its upgrade slots back the card slots. */
    public record SelectEmitterPacket(int containerId,
                                      @org.jetbrains.annotations.Nullable EmitterManagerTerminalMenu.Address address) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SelectEmitterPacket> TYPE = new CustomPacketPayload.Type<>(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gtlcore", "wireless_ae/select_emitter"));

        @Override
        public CustomPacketPayload.Type<SelectEmitterPacket> type() { return TYPE; }


        private static void encode(SelectEmitterPacket packet, RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.containerId);
            buffer.writeBoolean(packet.address != null);
            if (packet.address != null) {
                packet.address.write(buffer);
            }
        }

        private static SelectEmitterPacket decode(RegistryFriendlyByteBuf buffer) {
            int containerId = buffer.readVarInt();
            return new SelectEmitterPacket(
                    containerId,
                    buffer.readBoolean() ? EmitterManagerTerminalMenu.Address.read(buffer) : null);
        }

        private static void handle(SelectEmitterPacket packet, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = (context.player() instanceof ServerPlayer sender ? sender : null);
                if (player != null && player.containerMenu instanceof EmitterManagerTerminalMenu menu &&
                        menu.containerId == packet.containerId) {
                    menu.setSelectedAddress(packet.address);
                }
            });

        }
    }

    public record SetEmitterValuePacket(int containerId, EmitterManagerTerminalMenu.Address address,
                                        EmitterManagerTerminalMenu.ValueKind kind, long value) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SetEmitterValuePacket> TYPE = new CustomPacketPayload.Type<>(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gtlcore", "wireless_ae/set_emitter_value"));

        @Override
        public CustomPacketPayload.Type<SetEmitterValuePacket> type() { return TYPE; }


        private static void encode(SetEmitterValuePacket packet, RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.containerId);
            packet.address.write(buffer);
            buffer.writeEnum(packet.kind);
            buffer.writeVarLong(packet.value);
        }

        private static SetEmitterValuePacket decode(RegistryFriendlyByteBuf buffer) {
            return new SetEmitterValuePacket(
                    buffer.readVarInt(),
                    EmitterManagerTerminalMenu.Address.read(buffer),
                    buffer.readEnum(EmitterManagerTerminalMenu.ValueKind.class),
                    buffer.readVarLong());
        }

        private static void handle(SetEmitterValuePacket packet,
                                   IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = (context.player() instanceof ServerPlayer sender ? sender : null);
                if (player != null && player.containerMenu instanceof EmitterManagerTerminalMenu menu &&
                        menu.containerId == packet.containerId) {
                    menu.setEmitterValue(player, packet.address, packet.kind, packet.value);
                }
            });

        }
    }

    public record SyncMEChamberManagerEntriesPacket(
                                                    int containerId,
                                                    List<MEChamberManagerTerminalMenu.Entry> entries) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SyncMEChamberManagerEntriesPacket> TYPE = new CustomPacketPayload.Type<>(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gtlcore", "wireless_ae/sync_m_e_chamber_manager_entries"));

        @Override
        public CustomPacketPayload.Type<SyncMEChamberManagerEntriesPacket> type() { return TYPE; }


        private static void encode(SyncMEChamberManagerEntriesPacket packet, RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.containerId);
            MEChamberManagerTerminalMenu.writeEntries(buffer, packet.entries);
        }

        private static SyncMEChamberManagerEntriesPacket decode(RegistryFriendlyByteBuf buffer) {
            return new SyncMEChamberManagerEntriesPacket(
                    buffer.readVarInt(),
                    MEChamberManagerTerminalMenu.readEntries(buffer));
        }

        private static void handle(SyncMEChamberManagerEntriesPacket packet,
                                   IPayloadContext context) {
            dispatchClient(context, () -> org.gtlcore.gtlcore.client.ae2.wireless.WirelessAeClientPacketHandler
                    .handleMEChamberManagerEntries(packet));
        }
    }

    public record SelectMEChamberPacket(int containerId, MEChamberManagerTerminalMenu.Address address) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SelectMEChamberPacket> TYPE = new CustomPacketPayload.Type<>(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gtlcore", "wireless_ae/select_m_e_chamber"));

        @Override
        public CustomPacketPayload.Type<SelectMEChamberPacket> type() { return TYPE; }


        private static void encode(SelectMEChamberPacket packet, RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.containerId);
            packet.address.write(buffer);
        }

        private static SelectMEChamberPacket decode(RegistryFriendlyByteBuf buffer) {
            return new SelectMEChamberPacket(
                    buffer.readVarInt(),
                    MEChamberManagerTerminalMenu.Address.read(buffer));
        }

        private static void handle(SelectMEChamberPacket packet, IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = (context.player() instanceof ServerPlayer sender ? sender : null);
                if (player != null && player.containerMenu instanceof MEChamberManagerTerminalMenu menu &&
                        menu.containerId == packet.containerId) {
                    menu.selectChamber(player, packet.address);
                }
            });

        }
    }

    public record SetMEChamberSlotAmountPacket(int containerId, MEChamberManagerTerminalMenu.Address address,
                                               MEChamberManagerTerminalMenu.StorageKind storage, int slot,
                                               long amount) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SetMEChamberSlotAmountPacket> TYPE = new CustomPacketPayload.Type<>(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gtlcore", "wireless_ae/set_m_e_chamber_slot_amount"));

        @Override
        public CustomPacketPayload.Type<SetMEChamberSlotAmountPacket> type() { return TYPE; }


        private static void encode(SetMEChamberSlotAmountPacket packet, RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.containerId);
            packet.address.write(buffer);
            buffer.writeEnum(packet.storage);
            buffer.writeVarInt(packet.slot);
            buffer.writeVarLong(packet.amount);
        }

        private static SetMEChamberSlotAmountPacket decode(RegistryFriendlyByteBuf buffer) {
            return new SetMEChamberSlotAmountPacket(
                    buffer.readVarInt(),
                    MEChamberManagerTerminalMenu.Address.read(buffer),
                    buffer.readEnum(MEChamberManagerTerminalMenu.StorageKind.class),
                    buffer.readVarInt(),
                    buffer.readVarLong());
        }

        private static void handle(SetMEChamberSlotAmountPacket packet,
                                   IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = (context.player() instanceof ServerPlayer sender ? sender : null);
                if (player != null && player.containerMenu instanceof MEChamberManagerTerminalMenu menu &&
                        menu.containerId == packet.containerId) {
                    menu.setSlotAmount(player, packet.address, packet.storage, packet.slot, packet.amount);
                }
            });

        }
    }

    public record SetMEChamberSlotConfigPacket(int containerId, MEChamberManagerTerminalMenu.Address address,
                                               MEChamberManagerTerminalMenu.StorageKind storage, int slot,
                                               @org.jetbrains.annotations.Nullable AEKey key) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SetMEChamberSlotConfigPacket> TYPE = new CustomPacketPayload.Type<>(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gtlcore", "wireless_ae/set_m_e_chamber_slot_config"));

        @Override
        public CustomPacketPayload.Type<SetMEChamberSlotConfigPacket> type() { return TYPE; }


        private static void encode(SetMEChamberSlotConfigPacket packet, RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.containerId);
            packet.address.write(buffer);
            buffer.writeEnum(packet.storage);
            buffer.writeVarInt(packet.slot);
            AEKey.writeOptionalKey(buffer, packet.key);
        }

        private static SetMEChamberSlotConfigPacket decode(RegistryFriendlyByteBuf buffer) {
            return new SetMEChamberSlotConfigPacket(
                    buffer.readVarInt(),
                    MEChamberManagerTerminalMenu.Address.read(buffer),
                    buffer.readEnum(MEChamberManagerTerminalMenu.StorageKind.class),
                    buffer.readVarInt(),
                    AEKey.readOptionalKey(buffer));
        }

        private static void handle(SetMEChamberSlotConfigPacket packet,
                                   IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = (context.player() instanceof ServerPlayer sender ? sender : null);
                if (player != null && player.containerMenu instanceof MEChamberManagerTerminalMenu menu &&
                        menu.containerId == packet.containerId) {
                    menu.setSlotConfig(player, packet.address, packet.storage, packet.slot, packet.key);
                }
            });

        }
    }

    public record SetMEChamberControlPacket(int containerId, MEChamberManagerTerminalMenu.Address address,
                                            MEChamberManagerTerminalMenu.ControlKind control, int value) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SetMEChamberControlPacket> TYPE = new CustomPacketPayload.Type<>(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gtlcore", "wireless_ae/set_m_e_chamber_control"));

        @Override
        public CustomPacketPayload.Type<SetMEChamberControlPacket> type() { return TYPE; }


        private static void encode(SetMEChamberControlPacket packet, RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.containerId);
            packet.address.write(buffer);
            buffer.writeEnum(packet.control);
            buffer.writeVarInt(packet.value);
        }

        private static SetMEChamberControlPacket decode(RegistryFriendlyByteBuf buffer) {
            return new SetMEChamberControlPacket(
                    buffer.readVarInt(),
                    MEChamberManagerTerminalMenu.Address.read(buffer),
                    buffer.readEnum(MEChamberManagerTerminalMenu.ControlKind.class),
                    buffer.readVarInt());
        }

        private static void handle(SetMEChamberControlPacket packet,
                                   IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = (context.player() instanceof ServerPlayer sender ? sender : null);
                if (player != null && player.containerMenu instanceof MEChamberManagerTerminalMenu menu &&
                        menu.containerId == packet.containerId) {
                    menu.setControl(player, packet.address, packet.control, packet.value);
                }
            });

        }
    }

    public record MEChamberConfiguratorActionPacket(int containerId, MEChamberManagerTerminalMenu.Address address,
                                                    MEChamberConfigurator.Kind kind, int actionId,
                                                    byte[] actionData) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<MEChamberConfiguratorActionPacket> TYPE = new CustomPacketPayload.Type<>(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gtlcore", "wireless_ae/m_e_chamber_configurator_action"));

        @Override
        public CustomPacketPayload.Type<MEChamberConfiguratorActionPacket> type() { return TYPE; }


        private static void encode(MEChamberConfiguratorActionPacket packet, RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.containerId);
            packet.address.write(buffer);
            buffer.writeEnum(packet.kind);
            buffer.writeVarInt(packet.actionId);
            buffer.writeByteArray(packet.actionData);
        }

        private static MEChamberConfiguratorActionPacket decode(RegistryFriendlyByteBuf buffer) {
            return new MEChamberConfiguratorActionPacket(
                    buffer.readVarInt(),
                    MEChamberManagerTerminalMenu.Address.read(buffer),
                    buffer.readEnum(MEChamberConfigurator.Kind.class),
                    buffer.readVarInt(),
                    buffer.readByteArray(MEChamberConfigurator.MAX_ACTION_BYTES));
        }

        private static void handle(MEChamberConfiguratorActionPacket packet,
                                   IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer player = (context.player() instanceof ServerPlayer sender ? sender : null);
                if (player != null && player.containerMenu instanceof MEChamberManagerTerminalMenu menu &&
                        menu.containerId == packet.containerId) {
                    menu.handleConfiguratorAction(
                            player, packet.address, packet.kind, packet.actionId, packet.actionData);
                }
            });

        }
    }

    public record SyncMEChamberSnapshotPacket(int containerId, boolean contents, long revision,
                                              int totalBytes, int offset, byte[] data) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SyncMEChamberSnapshotPacket> TYPE = new CustomPacketPayload.Type<>(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gtlcore", "wireless_ae/sync_m_e_chamber_snapshot"));

        @Override
        public CustomPacketPayload.Type<SyncMEChamberSnapshotPacket> type() { return TYPE; }


        private static void encode(SyncMEChamberSnapshotPacket packet, RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.containerId);
            buffer.writeBoolean(packet.contents);
            buffer.writeVarLong(packet.revision);
            buffer.writeVarInt(packet.totalBytes);
            buffer.writeVarInt(packet.offset);
            buffer.writeByteArray(packet.data);
        }

        private static SyncMEChamberSnapshotPacket decode(RegistryFriendlyByteBuf buffer) {
            return new SyncMEChamberSnapshotPacket(buffer.readVarInt(), buffer.readBoolean(), buffer.readVarLong(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readByteArray(
                            org.gtlcore.gtlcore.integration.ae2.chamber.ChamberSnapshotTransfer.MAX_CHUNK_BYTES));
        }

        private static void handle(SyncMEChamberSnapshotPacket packet, IPayloadContext context) {
            dispatchClient(context, () -> org.gtlcore.gtlcore.client.ae2.wireless.WirelessAeClientPacketHandler
                    .handleMEChamberSnapshot(packet));
        }
    }

    public record SyncMEChamberManagerContentsPacket(
                                                     int containerId,
                                                     @org.jetbrains.annotations.Nullable MEChamberManagerTerminalMenu.Address address,
                                                     List<MEChamberManagerTerminalMenu.SlotContent> contents,
                                                     MEChamberManagerTerminalMenu.ChamberDetails details) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SyncMEChamberManagerContentsPacket> TYPE = new CustomPacketPayload.Type<>(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gtlcore", "wireless_ae/sync_m_e_chamber_manager_contents"));

        @Override
        public CustomPacketPayload.Type<SyncMEChamberManagerContentsPacket> type() { return TYPE; }


        private static void encode(SyncMEChamberManagerContentsPacket packet, RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.containerId);
            buffer.writeBoolean(packet.address != null);
            if (packet.address != null) {
                packet.address.write(buffer);
            }
            MEChamberManagerTerminalMenu.writeContents(buffer, packet.contents);
            MEChamberManagerTerminalMenu.writeDetails(buffer, packet.details);
        }

        private static SyncMEChamberManagerContentsPacket decode(RegistryFriendlyByteBuf buffer) {
            int containerId = buffer.readVarInt();
            MEChamberManagerTerminalMenu.Address address = buffer.readBoolean() ?
                    MEChamberManagerTerminalMenu.Address.read(buffer) :
                    null;
            return new SyncMEChamberManagerContentsPacket(
                    containerId,
                    address,
                    MEChamberManagerTerminalMenu.readContents(buffer),
                    MEChamberManagerTerminalMenu.readDetails(buffer));
        }

        private static void handle(SyncMEChamberManagerContentsPacket packet,
                                   IPayloadContext context) {
            dispatchClient(context, () -> org.gtlcore.gtlcore.client.ae2.wireless.WirelessAeClientPacketHandler
                    .handleMEChamberManagerContents(packet));
        }
    }

    private static void writePatternQuickUploadEntries(RegistryFriendlyByteBuf buffer,
                                                       List<PatternQuickUploadSelectionMenu.Entry> entries) {
        buffer.writeVarInt(entries.size());
        for (PatternQuickUploadSelectionMenu.Entry entry : entries) {
            buffer.writeResourceLocation(entry.levelKey().location());
            buffer.writeBlockPos(entry.bufferPos());
            ComponentSerialization.STREAM_CODEC.encode(buffer, entry.targetName());
            buffer.writeResourceLocation(entry.recipeTypeId());
            ComponentSerialization.STREAM_CODEC.encode(buffer, entry.recipeTypeName());
            buffer.writeBoolean(entry.showPosition());
        }
    }

    private static List<PatternQuickUploadSelectionMenu.Entry> readPatternQuickUploadEntries(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<PatternQuickUploadSelectionMenu.Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new PatternQuickUploadSelectionMenu.Entry(
                    ResourceKey.create(Registries.DIMENSION, buffer.readResourceLocation()),
                    buffer.readBlockPos(),
                    ComponentSerialization.STREAM_CODEC.decode(buffer),
                    buffer.readResourceLocation(),
                    ComponentSerialization.STREAM_CODEC.decode(buffer),
                    buffer.readBoolean()));
        }
        return entries;
    }

    private static List<TargetNetworkEntry> buildTargetEntries(ServerPlayer player,
                                                               WirelessAeSavedData.MemberKey target) {
        ServerLevel level = player.serverLevel();
        WirelessAeSavedData data = WirelessAeSavedData.get(level.getServer());
        UUID currentNetwork = data.getMemberNetwork(target);
        UUID wiredNetwork = WirelessAeNetworkRuntime.findWiredNetworkFrequency(level.getServer(), target);
        UUID connectedNetwork = wiredNetwork == null ? WirelessAeNetworkRuntime.findConnectedNetworkFrequency(level.getServer(), target) : wiredNetwork;
        List<TargetNetworkEntry> entries = new ArrayList<>();
        for (WirelessAeSavedData.NetworkInfo network : WirelessAeNetworkRuntime.getAccessibleNetworkInfo(player)) {
            boolean connected = network.frequency().equals(connectedNetwork);
            boolean disconnectable = wiredNetwork == null && connected && network.frequency().equals(currentNetwork) && WirelessAeNetworkRuntime.hasWirelessConnection(network.frequency(), target);
            entries.add(new TargetNetworkEntry(
                    network.frequency(),
                    network.name(),
                    connected,
                    disconnectable));
        }
        return entries;
    }

    private static void writeDirection(RegistryFriendlyByteBuf buffer, Direction direction) {
        buffer.writeBoolean(direction != null);
        if (direction != null) {
            buffer.writeEnum(direction);
        }
    }

    private static Direction readDirection(RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean() ? buffer.readEnum(Direction.class) : null;
    }

    private static void writeVec3(RegistryFriendlyByteBuf buffer, Vec3 vec) {
        buffer.writeBoolean(vec != null);
        if (vec != null) {
            buffer.writeDouble(vec.x);
            buffer.writeDouble(vec.y);
            buffer.writeDouble(vec.z);
        }
    }

    private static Vec3 readVec3(RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean() ? new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()) : null;
    }

    private static boolean isCloseEnough(ServerPlayer player, BlockPos pos) {
        return !(player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D);
    }
}
