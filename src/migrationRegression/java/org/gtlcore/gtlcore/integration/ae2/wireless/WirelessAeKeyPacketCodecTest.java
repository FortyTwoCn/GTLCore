package org.gtlcore.gtlcore.integration.ae2.wireless;

import org.gtlcore.gtlcore.integration.ae2.storage.CellItemData;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WirelessAeKeyPacketCodecTest {

    @BeforeAll
    static void bootstrapVanilla() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void itemPayloadRetainsNativeComponentsAndCellIdentity() {
        var stack = new ItemStack(Items.PAPER, 32);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Native wireless cell"));
        var uuid = CellItemData.ensureDiskUUID(stack);
        CellItemData.setStoredCount(stack, 0x1.0p100);
        var key = AEItemKey.of(stack);
        assertNotNull(key);

        var decoded = assertInstanceOf(AEItemKey.class, roundTrip(key));
        assertEquals(key, decoded);
        assertEquals(uuid, CellItemData.getDiskUUID(decoded.toStack()));
        assertEquals(0x1.0p100, CellItemData.getStoredCount(decoded.toStack()));
        assertEquals(stack.get(DataComponents.CUSTOM_NAME), decoded.toStack().get(DataComponents.CUSTOM_NAME));
    }

    @Test
    void fluidPayloadRetainsComponentsIndependentOfAmount() {
        var fluid = new FluidStack(Fluids.WATER, 4000);
        fluid.set(DataComponents.CUSTOM_NAME, Component.literal("Tagged process fluid"));
        var key = AEFluidKey.of(fluid);
        assertNotNull(key);
        fluid.setAmount(1);
        assertEquals(key, AEFluidKey.of(fluid));
        assertEquals(key, roundTrip(key));
    }

    @Test
    void unknownWireTypeIsRejected() {
        var buffer = newBuffer();
        try {
            buffer.writeByte(255);
            assertThrows(IllegalArgumentException.class, () -> WirelessAeKeyPacketCodec.read(buffer));
        } finally {
            buffer.release();
        }
    }

    private static AEKey roundTrip(AEKey key) {
        var buffer = newBuffer();
        try {
            WirelessAeKeyPacketCodec.write(buffer, key);
            var decoded = WirelessAeKeyPacketCodec.read(buffer);
            assertEquals(0, buffer.readableBytes());
            return decoded;
        } finally {
            buffer.release();
        }
    }

    private static RegistryFriendlyByteBuf newBuffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(),
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }
}
