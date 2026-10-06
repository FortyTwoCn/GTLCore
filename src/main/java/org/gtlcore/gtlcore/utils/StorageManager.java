package org.gtlcore.gtlcore.utils;

import org.gtlcore.gtlcore.integration.ae2.storage.InfinityCellDataStorage;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.Objects;

public class StorageManager extends SavedData {

    private final Object2ObjectMap<UUID, InfinityCellDataStorage> disks;
    private HolderLookup.Provider registries;

    public StorageManager() {
        disks = new Object2ObjectOpenHashMap<>();
        this.setDirty();
    }

    private StorageManager(Object2ObjectMap<UUID, InfinityCellDataStorage> disks) {
        this.disks = disks;
        this.setDirty();
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag nbt, @NotNull HolderLookup.Provider registries) {
        this.registries = registries;
        ListTag diskList = new ListTag();
        for (Map.Entry<UUID, InfinityCellDataStorage> entry : disks.entrySet()) {
            UUID uuid = entry.getKey();
            InfinityCellDataStorage storage = entry.getValue();

            if (uuid == null || storage == null) continue;

            CompoundTag disk = new CompoundTag();
            disk.putUUID("diskuuid", uuid);
            disk.put("diskdata", storage.toNbt());
            diskList.add(disk);
        }

        nbt.put("disklist", diskList);
        return nbt;
    }

    public static StorageManager readNbt(CompoundTag nbt) {
        Object2ObjectMap<UUID, InfinityCellDataStorage> disks = new Object2ObjectOpenHashMap<>();
        ListTag diskList = nbt.getList("disklist", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < diskList.size(); i++) {
            CompoundTag disk = diskList.getCompound(i);
            disks.put(disk.getUUID("diskuuid"), InfinityCellDataStorage.fromNbt(disk.getCompound("diskdata")));
        }
        return new StorageManager(disks);
    }

    public HolderLookup.Provider getRegistries() {
        return Objects.requireNonNull(registries, "Disk storage must be attached to a server before decoding AE2 keys");
    }

    public void updateDisk(UUID uuid, InfinityCellDataStorage infinityCellDataStorage) {
        disks.put(uuid, infinityCellDataStorage);
        setDirty();
    }

    public void removeDisk(UUID uuid) {
        disks.remove(uuid);
        setDirty();
    }

    public InfinityCellDataStorage getOrCreateDisk(UUID uuid, boolean isFastCell) {
        if (!disks.containsKey(uuid)) {
            updateDisk(uuid, new InfinityCellDataStorage(isFastCell));
        }
        return disks.get(uuid);
    }

    public void modifyDisk(UUID diskID, ListTag stackKeys, ListTag amounts, double totalAmount, boolean isFastCell) {
        InfinityCellDataStorage diskToModify = getOrCreateDisk(diskID, isFastCell);
        if (stackKeys != null && amounts != null) {
            diskToModify.stackKeys = stackKeys;
            diskToModify.amounts = amounts;
        }
        diskToModify.totalAmount = totalAmount;

        updateDisk(diskID, diskToModify);
    }

    public static StorageManager getInstance(MinecraftServer server) {
        ServerLevel world = server.overworld();
        // Preserve the old filename and NBT keys; these disks are not vanilla item stacks.
        StorageManager manager = world.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(StorageManager::new, (tag, registries) -> readNbt(tag)),
                "disk_manager");
        manager.registries = server.registryAccess();
        return manager;
    }
}
