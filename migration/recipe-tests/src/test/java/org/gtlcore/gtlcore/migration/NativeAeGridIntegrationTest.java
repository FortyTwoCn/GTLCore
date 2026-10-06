package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEIOPartMachine;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IModifiableSyncOffset;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.common.data.machines.GTAEMachines;
import com.gregtechceu.gtceu.integration.ae2.machine.trait.GridNodeHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NativeAeGridIntegrationTest {
    private static NativeAeGridFixtures.Part part() {
        return (NativeAeGridFixtures.Part) NativeAeGridFixtures.PART.getBlockEntityType().create(BlockPos.ZERO,
                NativeAeGridFixtures.PART.getBlock().defaultBlockState());
    }

    @Test void nodeAndPolymorphicIoTraitsAreAttachedAndPersistExactlyOnce() {
        var part = part();
        assertSame(part, part.getNodeHolder().getMachine());
        assertSame(part, part.checkpointTrait().getMachine());
        assertEquals(1, part.getTraits(GridNodeHolder.class).size());
        assertEquals(1, part.getTraits(MEIOPartMachine.MEIOTrait.class).size());
        assertSame(part.getMETrait(), part.getPersistentTrait("meTrait"));
        assertEquals(IO.BOTH, part.getMETrait().getIO());
        part.checkpointTrait().checkpoint = 73;
        part.getPersistentData().putBoolean("isAllFacing", true);
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var saved = part.saveWithoutMetadata(registries);
        var copy = part(); copy.loadAdditional(saved, registries);
        assertEquals(73, copy.checkpointTrait().checkpoint);
        assertTrue(copy.getPersistentData().getBoolean("isAllFacing"));
        assertFalse(copy.isOnline());
    }

    @Test void onlineTransitionsAreSentThroughNativeClientSync() {
        var server = part(); var client = part();
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        server.setOnline(true);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            server.getSyncDataHolder().writeClientPacket(registries, buffer);
            client.getSyncDataHolder().readClientPacket(registries, buffer);
            assertTrue(client.isOnline());
            buffer.clear(); server.setOnline(false);
            server.getSyncDataHolder().writeClientPacket(registries, buffer);
            client.getSyncDataHolder().readClientPacket(registries, buffer);
            assertFalse(client.isOnline());
        } finally { buffer.release(); }
    }
    @Test void nativeGtBusAndHatchMixinsPersistTheirRefreshIntervals() {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        for (var definition : java.util.List.of(GTAEMachines.ITEM_EXPORT_BUS_ME, GTAEMachines.FLUID_EXPORT_HATCH_ME)) {
            var host = definition.getBlockEntityType().create(BlockPos.ZERO, definition.getBlock().defaultBlockState());
            assertInstanceOf(IModifiableSyncOffset.class, host);
            var offset = (IModifiableSyncOffset) host;
            assertEquals(0, offset.getMEUpdateInterval());
            offset.setMEUpdateInterval(63);
            assertThrows(IllegalArgumentException.class, () -> offset.setMEUpdateInterval(-1));
            assertEquals(63, offset.getMEUpdateInterval());
            var restored = definition.getBlockEntityType().create(BlockPos.ZERO, definition.getBlock().defaultBlockState());
            restored.loadAdditional(host.saveWithoutMetadata(registries), registries);
            assertEquals(63, ((IModifiableSyncOffset) restored).getMEUpdateInterval());
            ((IModifiableSyncOffset) restored).setMEUpdateInterval(0);
            assertEquals(0, ((IModifiableSyncOffset) restored).getMEUpdateInterval());
        }
    }
}
