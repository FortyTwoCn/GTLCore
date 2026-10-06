package org.gtlcore.gtlcore.integration.ae2;

import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEOutputPart;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IModifiableSyncOffset;
import com.gregtechceu.gtceu.integration.ae2.utils.KeyStorage;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Native GT output buffers keep material rejected by AE in the actual dropped machine. */
public final class NativeMEOutputPersistence {
    private NativeMEOutputPersistence() {}
    public static void write(ItemStack item, HolderLookup.Provider registries, KeyStorage buffer, int time, int interval) {
        var payload = new CompoundTag(); payload.put("buffer", buffer.serializeNBT(registries));
        payload.putInt("time", time); payload.putInt("interval", interval);
        var custom = item.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        custom.put("gtlcoreNativeMEOutput", payload); item.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
    }
    public static void read(CustomData data, HolderLookup.Provider registries, KeyStorage buffer, IMEOutputPart part) {
        var custom = data.copyTag();
        if (!custom.contains("gtlcoreNativeMEOutput", Tag.TAG_COMPOUND)) return;
        var payload = custom.getCompound("gtlcoreNativeMEOutput");
        if (!payload.contains("time", Tag.TAG_INT) || !payload.contains("interval", Tag.TAG_INT) ||
                !(payload.get("buffer") instanceof ListTag entries) || (!entries.isEmpty() && entries.getElementType() != Tag.TAG_COMPOUND))
            throw new IllegalArgumentException("Invalid native ME output item data");
        int time = payload.getInt("time"), interval = payload.getInt("interval");
        if (time < 0 || interval < 0) throw new IllegalArgumentException("Invalid native ME output cycle");
        var decoded = new AEOutputBuffer(() -> {}); decoded.deserializeNBT(registries, entries);
        buffer.storage.clear(); buffer.storage.putAll(decoded.storage);
        if (part instanceof IModifiableSyncOffset configurable) configurable.setMEUpdateInterval(interval);
        part.setTime(time); buffer.onChanged();
    }
    public static void writeSettings(DataComponentMap.Builder components, IMEOutputPart part, int interval) {
        var custom = components.build().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        var settings = new CompoundTag(); settings.putInt("time", part.getTime()); settings.putInt("interval", interval);
        custom.put("gtlcoreMEOutputCycle", settings); components.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
    }
    public static void readSettings(CustomData data, IMEOutputPart part) {
        var custom = data.copyTag();
        if (!custom.contains("gtlcoreMEOutputCycle", Tag.TAG_COMPOUND)) return;
        var settings = custom.getCompound("gtlcoreMEOutputCycle"); int time = settings.getInt("time"), interval = settings.getInt("interval");
        if (!settings.contains("time", Tag.TAG_INT) || !settings.contains("interval", Tag.TAG_INT))
            throw new IllegalArgumentException("Invalid native ME output cycle data");
        if (time < 0 || interval < 0) throw new IllegalArgumentException("Invalid native ME output cycle");
        if (part instanceof IModifiableSyncOffset configurable) configurable.setMEUpdateInterval(interval);
        part.setTime(time);
    }
}
