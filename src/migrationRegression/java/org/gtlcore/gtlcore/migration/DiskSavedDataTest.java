package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.integration.ae2.storage.InfinityCellDataStorage;
import org.gtlcore.gtlcore.utils.StorageManager;

import net.minecraft.core.HolderLookup;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class DiskSavedDataTest {
    private static final HolderLookup.Provider REGISTRIES = HolderLookup.Provider.create(Stream.empty());

    @BeforeAll
    static void bootstrapVanilla() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void diskDataPreservesUuidKeysAnd128BitAmounts() {
        UUID uuid = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
        CompoundTag key = new CompoundTag();
        key.putString("#type", "ae2:i");
        key.putString("id", "gtlcore:infinity_cell");
        ListTag keys = new ListTag();
        keys.add(key);
        ListTag amounts = new ListTag();
        amounts.add(new LongArrayTag(new long[] { 0x123456789abcdefL, -123456789L }));
        CompoundTag diskData = new CompoundTag();
        diskData.putBoolean("isFastCell", true);
        diskData.put("keys", keys);
        diskData.put("amounts", amounts);
        diskData.putDouble("totalAmount", 0x1.0p100);
        CompoundTag diskEntry = new CompoundTag();
        diskEntry.putUUID("diskuuid", uuid);
        diskEntry.put("diskdata", diskData);
        ListTag disks = new ListTag();
        disks.add(diskEntry);
        CompoundTag saved = new CompoundTag();
        saved.put("disklist", disks);

        StorageManager loaded = StorageManager.readNbt(saved);
        assertEquals(saved, loaded.save(new CompoundTag(), REGISTRIES));
        assertArrayEquals(new long[] { 0x123456789abcdefL, -123456789L }, loaded.getOrCreateDisk(uuid, true).amounts.getLongArray(0));
        loaded.removeDisk(uuid);
        assertTrue(loaded.save(new CompoundTag(), REGISTRIES).getList("disklist", 10).isEmpty());
    }

    @Test
    void nonFastDiskKeepsDecimalStrings() {
        InfinityCellDataStorage cell = new InfinityCellDataStorage(false);
        cell.amounts.add(StringTag.valueOf("1844674407370955161600000"));
        InfinityCellDataStorage restored = InfinityCellDataStorage.fromNbt(cell.toNbt());
        assertFalse(restored.isFastCell);
        assertEquals(cell.amounts, restored.amounts);
    }

}
