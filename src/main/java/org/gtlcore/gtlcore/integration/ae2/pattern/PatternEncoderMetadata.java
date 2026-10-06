package org.gtlcore.gtlcore.integration.ae2.pattern;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.UUID;

public final class PatternEncoderMetadata {

    private static final String ROOT_KEY = "gtlcore";
    private static final String ENCODER_ID_KEY = "patternEncoderId";
    private static final String ENCODER_NAME_KEY = "patternEncoderName";

    private PatternEncoderMetadata() {}

    public static void writeEncoder(ItemStack patternStack, UUID encoderId, String encoderName) {
        if (patternStack.isEmpty()) {
            return;
        }

        CompoundTag tag = patternStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag gtlcoreTag = tag.contains(ROOT_KEY, Tag.TAG_COMPOUND) ?
                tag.getCompound(ROOT_KEY) :
                new CompoundTag();
        gtlcoreTag.putUUID(ENCODER_ID_KEY, encoderId);
        if (encoderName != null && !encoderName.isBlank()) {
            gtlcoreTag.putString(ENCODER_NAME_KEY, encoderName);
        } else {
            gtlcoreTag.remove(ENCODER_NAME_KEY);
        }
        tag.put(ROOT_KEY, gtlcoreTag);
        patternStack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static Optional<Encoder> readEncoder(ItemStack patternStack) {
        CompoundTag tag = patternStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag == null || !tag.contains(ROOT_KEY, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }

        CompoundTag gtlcoreTag = tag.getCompound(ROOT_KEY);
        if (!gtlcoreTag.hasUUID(ENCODER_ID_KEY)) {
            return Optional.empty();
        }
        String encoderName = gtlcoreTag.contains(ENCODER_NAME_KEY, Tag.TAG_STRING) ?
                gtlcoreTag.getString(ENCODER_NAME_KEY) :
                "";
        return Optional.of(new Encoder(gtlcoreTag.getUUID(ENCODER_ID_KEY), encoderName));
    }

    static void removeEncoder(ItemStack patternStack) {
        CustomData.update(DataComponents.CUSTOM_DATA, patternStack, tag -> {
            if (!tag.contains(ROOT_KEY, Tag.TAG_COMPOUND)) return;
            var core = tag.getCompound(ROOT_KEY); core.remove(ENCODER_ID_KEY); core.remove(ENCODER_NAME_KEY);
            if (core.isEmpty()) tag.remove(ROOT_KEY); else tag.put(ROOT_KEY, core);
        });
    }

    public record Encoder(UUID id, String name) {

        public String displayName() {
            return name.isBlank() ? id.toString() : name;
        }
    }
}
