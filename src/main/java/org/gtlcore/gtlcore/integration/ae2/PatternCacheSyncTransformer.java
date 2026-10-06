package org.gtlcore.gtlcore.integration.ae2;

import com.gregtechceu.gtceu.api.sync_system.ClassSyncData;
import com.gregtechceu.gtceu.api.sync_system.data_transformers.ValueTransformer;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import io.netty.handler.codec.DecoderException;

/** The pinned GT object-array transformer cannot serialize primitive boolean arrays. */
public final class PatternCacheSyncTransformer implements ValueTransformer<boolean[]> {
    public static final PatternCacheSyncTransformer INSTANCE = new PatternCacheSyncTransformer();
    private PatternCacheSyncTransformer() {}

    public static void bind(Class<?> machineType) {
        var field = ClassSyncData.getClassData(machineType).getClientSyncFields().get("cacheRecipe");
        if (field == null || field.type.getRawType() != boolean[].class)
            throw new IllegalStateException("Pattern-buffer cache sync field is missing or has changed type");
        field.setTransformer(INSTANCE);
    }

    @Override public Tag serializeNBT(boolean[] value, TransformerContext<boolean[]> context) {
        byte[] bytes = new byte[value.length];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) (value[i] ? 1 : 0);
        return new ByteArrayTag(bytes);
    }

    @Override public boolean[] deserializeNBT(Tag tag, TransformerContext<boolean[]> context) {
        if (!(tag instanceof ByteArrayTag bytes)) throw new IllegalArgumentException("Pattern cache flags require a byte array");
        return apply(bytes.getAsByteArray(), context);
    }

    @Override public void writeToPacket(RegistryFriendlyByteBuf buffer, boolean[] value, TransformerContext<boolean[]> context) {
        buffer.writeVarInt(value.length);
        for (boolean flag : value) buffer.writeBoolean(flag);
    }

    @Override public boolean[] readFromPacket(RegistryFriendlyByteBuf buffer, TransformerContext<boolean[]> context) {
        int length = buffer.readVarInt();
        var current = context.currentValue();
        // Pattern buffers have fixed, authoritative capacities on both ends. Reject an invalid frame before editing.
        if (current == null || length != current.length || buffer.readableBytes() < length)
            throw new DecoderException("Pattern cache flag count does not match the native inventory");
        byte[] flags = new byte[length];
        buffer.readBytes(flags);
        return apply(flags, context);
    }

    private static boolean[] apply(byte[] flags, TransformerContext<boolean[]> context) {
        var current = context.currentValue();
        if (current == null || flags.length != current.length)
            throw new IllegalArgumentException("Pattern cache flag count does not match the native inventory");
        for (byte flag : flags) if (flag != 0 && flag != 1) throw new IllegalArgumentException("Invalid pattern cache flag");
        for (int i = 0; i < flags.length; i++) current[i] = flags[i] != 0;
        return current; // Preserve the final field's array instead of asking VarHandle to replace it.
    }
}
