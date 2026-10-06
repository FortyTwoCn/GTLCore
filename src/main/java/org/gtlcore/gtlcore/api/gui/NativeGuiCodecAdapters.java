package org.gtlcore.gtlcore.api.gui;

import brachy.modularui.utils.serialization.network.IByteBufAdapter;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.FriendlyByteBuf;
import java.util.Objects;

/** Native codec packets and equality for all valid JSON values, including literal chat components. */
public final class NativeGuiCodecAdapters {
    private NativeGuiCodecAdapters() {}

    public static <T> IByteBufAdapter<FriendlyByteBuf, T> make(Codec<T> codec) {
        return new IByteBufAdapter<>() {
            @Override public void encode(FriendlyByteBuf buffer, T value) { buffer.writeJsonWithCodec(codec, value); }
            @Override public T decode(FriendlyByteBuf buffer) { return buffer.readJsonWithCodec(codec); }
            @Override public boolean areEqual(T first, T second) {
                if (first == second) return true;
                if (first == null || second == null) return false;
                return Objects.equals(codec.encodeStart(JsonOps.INSTANCE, first).getOrThrow(),
                        codec.encodeStart(JsonOps.INSTANCE, second).getOrThrow());
            }
        };
    }
}
