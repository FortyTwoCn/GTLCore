package org.gtlcore.gtlcore.migration;

import com.gtladd.gtladditions.integration.ae2.MEStorageCapabilityProvider;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.StorageMachine;
import appeng.api.storage.MEStorage;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Actual production provider queries native block entities; this does not create an AE grid. */
class NativeMeStorageCapabilityIntegrationTest {
    private static BlockEntityCreationInfo info() {
        var definition = GTMultiMachines.VACUUM_FREEZER;
        return new BlockEntityCreationInfo(definition.getBlockEntityType(), BlockPos.ZERO,
                definition.getBlock().defaultBlockState());
    }

    private static final class StorageHost extends StorageMachine implements MEStorage {
        StorageHost() { super(info(), 1); }
        @Override public Component getDescription() { return Component.literal("AE capability fixture"); }
    }

    @Test void providerExposesOnlyTheQueriedStorageMachinesCurrentFront() {
        var provider = new MEStorageCapabilityProvider();
        var host = new StorageHost(); var other = new StorageHost();
        var front = host.getFrontFacing();
        assertSame(host, provider.getCapability(host, front));
        assertSame(other, provider.getCapability(other, front));
        assertNull(provider.getCapability(host, null));
        for (var direction : Direction.values()) {
            if (direction != front) assertNull(provider.getCapability(host, direction));
        }
        host.setBlockState(host.getBlockState().setValue(host.getRotationState().property, front.getOpposite()));
        assertNull(provider.getCapability(host, front));
        assertSame(host, provider.getCapability(host, front.getOpposite()));
        assertSame(other, provider.getCapability(other, front));
    }

    @Test void providerRejectsNativeMachinesWithoutStorageAndUnrelatedBlockEntities() {
        var provider = new MEStorageCapabilityProvider();
        var machine = new StorageMachine(info(), 1);
        assertNull(provider.getCapability(machine, machine.getFrontFacing()));
        var unrelated = new BlockEntity(BlockEntityType.CHEST, BlockPos.ZERO, Blocks.CHEST.defaultBlockState()) {};
        assertNull(provider.getCapability(unrelated, Direction.NORTH));
        assertNull(provider.getCapability(unrelated, null));
    }
}
