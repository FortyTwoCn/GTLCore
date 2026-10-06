package org.gtlcore.gtlcore.integration.ae2.wireless;

import org.gtlcore.gtlcore.GTLCore;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.integration.ae2.utils.SerializableManagedGridNode;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.security.IActionHost;
import appeng.api.util.AECableType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;

public class WirelessNetworkCoreBlockEntity extends MetaMachine
                                            implements IActionHost, IInWorldGridNodeHost {

    private static final String TAG_FREQUENCY = "frequency";
    private static final String NODE_TAG = "wireless_core";

    private static final IGridNodeListener<WirelessNetworkCoreBlockEntity> NODE_LISTENER = new IGridNodeListener<>() {

        @Override
        public void onSaveChanges(WirelessNetworkCoreBlockEntity host, IGridNode node) {
            host.setChanged();
        }
    };

    @SaveField
    private final SerializableManagedGridNode mainNode = (SerializableManagedGridNode) new SerializableManagedGridNode(this, NODE_LISTENER)
            .setFlags(GridFlags.DENSE_CAPACITY)
            .setIdlePowerUsage(0.0D)
            .setInWorldNode(true)
            .setExposedOnSides(getExposedSides())
            .setTagName(NODE_TAG);

    @SaveField(nbtKey = TAG_FREQUENCY)
    @SyncToClient
    private UUID frequency;

    public WirelessNetworkCoreBlockEntity(BlockPos pos, BlockState state) {
        super(new BlockEntityCreationInfo(GTLWirelessAeContent.WIRELESS_NETWORK_CORE_BE.get(), pos, state));
        this.mainNode.setVisualRepresentation(GTLWirelessAeContent.WIRELESS_NETWORK_CORE_ITEM.get());
        refreshExposedSides();
    }

    public static MachineDefinition definition() {
        return GTLWirelessAeContent.WIRELESS_NETWORK_CORE_DEFINITION.get();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level instanceof ServerLevel serverLevel) {
            ensureFrequency();
            refreshExposedSides();
            createMainNode(serverLevel);
            WirelessAeSavedData data = WirelessAeSavedData.get(serverLevel.getServer());
            data.setCore(this.frequency, GlobalPos.of(serverLevel.dimension(), this.worldPosition));
            UUID owner = this.mainNode.getNode().getOwningPlayerProfileId();
            if (owner != null) {
                data.setNetworkOwner(this.frequency, owner);
            }
            WirelessAeNetworkRuntime.requestReconnect(this.frequency);
        }
    }

    @Override
    public void onUnload() {
        if (this.frequency != null) {
            WirelessAeNetworkRuntime.disconnectFrequency(this.frequency);
        }
        this.mainNode.destroy();
        super.onUnload();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (this.frequency != null) {
            WirelessAeNetworkRuntime.disconnectFrequency(this.frequency);
        }
        this.mainNode.destroy();
    }

    @Override
    public IGridNode getActionableNode() {
        return this.mainNode.getNode();
    }

    @Override
    public IGridNode getGridNode(Direction direction) {
        refreshExposedSides();
        return isConnectionSide(direction) ? this.mainNode.getNode() : null;
    }

    @Override
    public AECableType getCableConnectionType(Direction direction) {
        refreshExposedSides();
        return isConnectionSide(direction) ? AECableType.SMART : AECableType.NONE;
    }

    public MetaMachine getMetaMachine() {
        return this;
    }

    @Override
    public MachineDefinition getDefinition() {
        return definition();
    }

    public UUID getFrequency() {
        return ensureFrequency();
    }

    public String getShortFrequency() {
        return WirelessAeNetworkRuntime.shortFrequency(getFrequency());
    }

    public void setOwner(Player player) {
        setOwnerUUID(player.getUUID());
        this.mainNode.setOwningPlayer(player);
        if (this.level instanceof ServerLevel serverLevel) {
            WirelessAeSavedData.get(serverLevel.getServer()).setNetworkOwner(getFrequency(), player.getUUID());
        }
    }

    public boolean migrateLegacyOwner(Player player) {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return false;
        }

        WirelessAeSavedData data = WirelessAeSavedData.get(serverLevel.getServer());
        UUID frequency = getFrequency();
        if (data.getNetworkOwner(frequency) != null || this.mainNode.getNode().getOwningPlayerProfileId() != null) {
            return false;
        }

        setOwner(player);
        return true;
    }

    public boolean isLinkedToAeNetwork() {
        return WirelessAeNetworkRuntime.findBridgeNode(this) != null;
    }

    public Direction getConnectionSide() {
        BlockState state = getBlockState();
        if (state.hasProperty(WirelessNetworkCoreBlock.FACING)) {
            return state.getValue(WirelessNetworkCoreBlock.FACING);
        }
        return Direction.NORTH;
    }

    public void removeNetwork(MinecraftServer server) {
        if (this.frequency == null) {
            return;
        }
        WirelessAeSavedData.get(server).removeNetwork(this.frequency);
        WirelessAeNetworkRuntime.disconnectFrequency(this.frequency);
    }

    public void refreshConnectionSide() {
        refreshConnectionSide(null, getConnectionSide());
    }

    public void refreshConnectionSide(Direction oldSide, Direction newSide) {
        Direction connectionSide = newSide == null ? getConnectionSide() : newSide;
        rebuildExposedSide(connectionSide);
        setChanged();
        if (this.frequency != null) {
            WirelessAeNetworkRuntime.disconnectFrequency(this.frequency);
            WirelessAeNetworkRuntime.requestReconnect(this.frequency);
        }
        BlockState state = getBlockState();
        LevelUpdate.send(this, state);
        notifyConnectionNeighbors(oldSide, connectionSide, state);
    }

    static MachineDefinition createDefinition() {
        return new MachineDefinition(ResourceLocation.fromNamespaceAndPath(GTLCore.MOD_ID, "wireless_network_core"),
                new MachineDefinition.Properties()
                        .blockHolder(GTLWirelessAeContent.WIRELESS_NETWORK_CORE)
                        .itemHolder(GTLWirelessAeContent.WIRELESS_NETWORK_CORE_ITEM)
                        .blockEntityTypeSupplier(GTLWirelessAeContent.WIRELESS_NETWORK_CORE_BE::get)
                        .rotationState(RotationState.ALL)
                        .shape(Shapes.block())
                        .appearance(() -> GTLWirelessAeContent.WIRELESS_NETWORK_CORE.get().defaultBlockState()));
    }

    private UUID ensureFrequency() {
        if (this.frequency == null) {
            this.frequency = UUID.randomUUID();
            getSyncDataHolder().markClientSyncFieldDirty("frequency");
            setChanged();
        }
        return this.frequency;
    }

    private void createMainNode(ServerLevel serverLevel) {
        if (!this.mainNode.isReady()) {
            this.mainNode.create(serverLevel, this.worldPosition);
        }
    }

    private boolean isConnectionSide(Direction direction) {
        return direction != null && direction == getConnectionSide();
    }

    private EnumSet<Direction> getExposedSides() {
        return EnumSet.of(getConnectionSide());
    }

    private void refreshExposedSides() {
        Direction connectionSide = getConnectionSide();
        this.mainNode.setExposedOnSides(EnumSet.of(connectionSide));

        IGridNode node = this.mainNode.getNode();
        if (node == null) {
            return;
        }

        try {
            for (Map.Entry<Direction, IGridConnection> entry : new ArrayList<>(node.getInWorldConnections().entrySet())) {
                if (entry.getKey() != connectionSide) {
                    entry.getValue().destroy();
                }
            }
        } catch (RuntimeException ignored) {
            // AE2 may update the connection map while neighboring nodes are changing.
        }
    }

    private void rebuildExposedSide(Direction connectionSide) {
        this.mainNode.setExposedOnSides(EnumSet.noneOf(Direction.class));
        this.mainNode.setExposedOnSides(EnumSet.of(connectionSide));
    }

    private void notifyConnectionNeighbors(Direction oldSide, Direction newSide, BlockState state) {
        Level level = this.level;
        if (level == null || level.isClientSide) {
            return;
        }

        level.updateNeighborsAt(this.worldPosition, state.getBlock());
        notifyConnectionNeighbor(level, oldSide, state);
        if (newSide != oldSide) {
            notifyConnectionNeighbor(level, newSide, state);
        }
    }

    private void notifyConnectionNeighbor(Level level, Direction side, BlockState state) {
        if (side == null) {
            return;
        }

        BlockPos neighborPos = this.worldPosition.relative(side);
        level.neighborChanged(neighborPos, state.getBlock(), this.worldPosition);
        level.updateNeighborsAt(neighborPos, state.getBlock());
    }

    @Override
    public void onRotated(@NotNull Direction oldFacing, @NotNull Direction newFacing) {
        super.onRotated(oldFacing, newFacing);
        refreshConnectionSide(oldFacing, newFacing);
    }

    private static final class LevelUpdate {

        private static void send(WirelessNetworkCoreBlockEntity core, BlockState state) {
            if (core.level != null) {
                core.level.sendBlockUpdated(core.worldPosition, state, state, 3);
            }
        }
    }
}
