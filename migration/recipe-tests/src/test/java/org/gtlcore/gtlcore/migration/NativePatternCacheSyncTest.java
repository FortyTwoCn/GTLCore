package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.integration.ae2.PatternCacheSyncTransformer;
import com.gregtechceu.gtceu.api.sync_system.TypeDeclaration;
import com.gregtechceu.gtceu.api.sync_system.data_transformers.ValueTransformer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class NativePatternCacheSyncTest {
    private static ValueTransformer.TransformerContext<boolean[]> context(boolean[] flags) {
        return new ValueTransformer.TransformerContext<>(null, new TypeDeclaration(boolean[].class), flags,
                "cacheRecipe", true, false, RegistryAccess.EMPTY);
    }

    @Test void realNativePacketRoundTripPreservesAll648FlagsAndFinalArrayIdentity() {
        boolean[] source = new boolean[648], target = new boolean[648];
        for (int i = 0; i < source.length; i++) source[i] = i % 5 == 0 || i == 647;
        Arrays.fill(target, true);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            var transformer = PatternCacheSyncTransformer.INSTANCE;
            transformer.writeToPacket(buffer, source, context(source));
            assertSame(target, transformer.readFromPacket(buffer, context(target)));
            assertArrayEquals(source, target); assertFalse(buffer.isReadable());
            target[647] = false; assertTrue(source[647]);
        } finally { buffer.release(); }
    }

    @Test void malformedLengthAndTruncatedPacketsLeaveTheEntireCacheUnchanged() {
        boolean[] target = {true, false, true}; var previous = target.clone();
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            for (int length : new int[]{-1, 0, 2, 4, Integer.MAX_VALUE}) {
                buffer.clear(); buffer.writeVarInt(length); buffer.writeZero(3);
                assertThrows(io.netty.handler.codec.DecoderException.class,
                        () -> PatternCacheSyncTransformer.INSTANCE.readFromPacket(buffer, context(target)));
                assertArrayEquals(previous, target);
            }
            buffer.clear(); buffer.writeVarInt(3); buffer.writeBoolean(false);
            assertThrows(io.netty.handler.codec.DecoderException.class,
                    () -> PatternCacheSyncTransformer.INSTANCE.readFromPacket(buffer, context(target)));
            assertArrayEquals(previous, target);
        } finally { buffer.release(); }
    }

    @Test void invalidFlagDoesNotPartiallyOverwriteEarlierValidFlags() {
        boolean[] target = {true, false, true}; var previous = target.clone();
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            buffer.writeVarInt(3); buffer.writeBytes(new byte[]{0, 1, 2});
            assertThrows(IllegalArgumentException.class,
                    () -> PatternCacheSyncTransformer.INSTANCE.readFromPacket(buffer, context(target)));
            assertArrayEquals(previous, target);
        } finally { buffer.release(); }
    }

    @Test void nativeNbtRoundTripAlsoKeepsFixedCapacityAndRejectsInvalidFlagsAtomically() {
        boolean[] source = {false, true, false, true}, target = {true, false, true, false};
        var transformer = PatternCacheSyncTransformer.INSTANCE;
        var encoded = transformer.serializeNBT(source, context(source));
        assertSame(target, transformer.deserializeNBT(encoded, context(target))); assertArrayEquals(source, target);
        assertThrows(IllegalArgumentException.class, () -> transformer.deserializeNBT(new ByteArrayTag(new byte[]{1, 0}), context(target)));
        assertThrows(IllegalArgumentException.class, () -> transformer.deserializeNBT(new ByteArrayTag(new byte[]{1, 0, 0, -1}), context(target)));
        assertArrayEquals(source, target);
    }
}
