package org.gtlcore.gtlcore.migration;

import com.gregtechceu.gtceu.common.mui.GTByteBufAdapters;
import com.mojang.serialization.Codec;
import io.netty.buffer.Unpooled;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.gtlcore.gtlcore.api.gui.NativeGuiCodecAdapters;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NativeGuiCodecAdaptersIntegrationTest {
    @Test void nativeComponentAdapterAcceptsLiteralAndStyledComponentsWithoutLosingChanges() {
        var adapter = GTByteBufAdapters.COMPONENT;
        assertTrue(adapter.areEqual(Component.literal("0"), Component.literal("0")));
        assertFalse(adapter.areEqual(Component.literal("0"), Component.literal("1")));
        assertFalse(adapter.areEqual(Component.literal("0"), Component.literal("0").withStyle(ChatFormatting.RED)));
        assertTrue(adapter.areEqual(Component.translatable("gtceu.machine.parallel", 17),
                Component.translatable("gtceu.machine.parallel", 17)));
        assertFalse(adapter.areEqual(Component.translatable("gtceu.machine.parallel", 17),
                Component.translatable("gtceu.machine.parallel", 18)));
        assertTrue(adapter.areEqual(null, null));
        assertFalse(adapter.areEqual(null, Component.empty()));
    }

    @Test void nativeCodecEqualitySupportsPrimitiveAndArrayValues() {
        var number = NativeGuiCodecAdapters.make(Codec.LONG);
        assertTrue(number.areEqual(9_007_199_254_740_993L, 9_007_199_254_740_993L));
        assertFalse(number.areEqual(9_007_199_254_740_993L, 9_007_199_254_740_994L));
        var array = NativeGuiCodecAdapters.make(Codec.STRING.listOf());
        assertTrue(array.areEqual(List.of("item", "fluid"), List.of("item", "fluid")));
        assertFalse(array.areEqual(List.of("item", "fluid"), List.of("fluid", "item")));
        assertFalse(array.areEqual(List.of("item"), List.of("item", "fluid")));
    }

    @Test void nativeComponentPacketsRoundTripLiteralAndStructuredValuesAndConsumeTheBuffer() {
        var adapter = GTByteBufAdapters.COMPONENT;
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (var component : List.of(Component.literal("0"),
                    Component.translatable("gtceu.machine.parallel", 17).withStyle(ChatFormatting.AQUA))) {
                adapter.encode(buffer, component);
                assertTrue(adapter.areEqual(component, adapter.decode(buffer)));
                assertFalse(buffer.isReadable());
                buffer.clear();
            }
        } finally {
            buffer.release();
        }
    }
}
