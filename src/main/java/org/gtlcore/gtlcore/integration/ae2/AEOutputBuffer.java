package org.gtlcore.gtlcore.integration.ae2;

import com.gregtechceu.gtceu.integration.ae2.utils.KeyStorage;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.CompoundTag;
import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

/** Native persisted ledger; accepted async outputs are materialized before the native SaveField is read. */
public final class AEOutputBuffer extends KeyStorage {
    private final Runnable beforeSave;
    public AEOutputBuffer(Runnable beforeSave) { this.beforeSave = beforeSave; }

    @Override public ListTag serializeNBT(HolderLookup.Provider registries) {
        beforeSave.run();
        return super.serializeNBT(registries);
    }

    @Override public void deserializeNBT(HolderLookup.Provider registries, ListTag tags) {
        var decoded = new Object2LongOpenHashMap<AEKey>();
        for (var entry : tags) {
            if (!(entry instanceof CompoundTag tag)) throw new IllegalArgumentException("Invalid AE output buffer entry");
            var key = AEKey.fromTagGeneric(registries, tag.getCompound("key"));
            long amount = tag.getLong("value");
            if (key == null || amount <= 0) throw new IllegalArgumentException("Invalid AE output buffer key or amount");
            decoded.put(key, Math.addExact(decoded.getLong(key), amount));
        }
        storage.clear(); storage.putAll(decoded);
    }
}
