package org.gtlcore.gtlcore.api.machine.trait.AECraft;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;

import java.util.function.Function;

/** Registry-aware persistence shared by the production IO part and native lifecycle verification. */
public final class MolecularAssemblerOutputStorage implements INBTSerializable<CompoundTag> {
    private final IMolecularAssemblerOutputMachine host;

    public MolecularAssemblerOutputStorage(IMolecularAssemblerOutputMachine host) { this.host = host; }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        save(tag, registries, host);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
        load(tag, registries, host);
    }

    public static void save(CompoundTag tag, HolderLookup.Provider registries, IMolecularAssemblerOutputMachine host) {
        tag.put("buffer", write(host.getBuffer(), key -> key.toTagGeneric(registries)));
        tag.put("outputItems", write(host.getOutputItems(), key -> GenericStack.writeTag(registries, key)));
    }

    private static <K> ListTag write(Object2LongMap<K> amounts, Function<K, CompoundTag> encoder) {
        var result = new ListTag();
        for (var entry : amounts.object2LongEntrySet()) {
            if (entry.getLongValue() <= 0) continue;
            var tag = encoder.apply(entry.getKey());
            tag.putLong("real", entry.getLongValue());
            result.add(tag);
        }
        return result;
    }

    public static void load(CompoundTag tag, HolderLookup.Provider registries, IMolecularAssemblerOutputMachine host) {
        var buffer = read(tag, "buffer", entry -> AEKey.fromTagGeneric(registries, entry));
        var queue = read(tag, "outputItems", entry -> GenericStack.readTag(registries, entry));
        for (var key : queue.keySet()) {
            if (!(key.what() instanceof AEItemKey) || key.amount() <= 0) {
                throw new IllegalArgumentException("Invalid molecular assembler queued output");
            }
        }
        // Decode both maps before replacing either one; a failed registry lookup cannot partially load the queue.
        host.getBuffer().clear(); host.getBuffer().putAll(buffer);
        host.getOutputItems().clear(); host.getOutputItems().putAll(queue);
    }

    private static <K> Object2LongMap<K> read(CompoundTag tag, String name, Function<CompoundTag, K> decoder) {
        var result = new Object2LongLinkedOpenHashMap<K>();
        if (!tag.contains(name)) return result;
        if (!tag.contains(name, Tag.TAG_LIST)) throw new IllegalArgumentException("Invalid molecular assembler " + name);
        for (var entry : (ListTag) tag.get(name)) {
            if (!(entry instanceof CompoundTag item)) throw new IllegalArgumentException("Invalid molecular assembler " + name + " entry");
            K key = decoder.apply(item);
            long amount = item.getLong("real");
            if (key == null || amount <= 0) throw new IllegalArgumentException("Invalid molecular assembler " + name + " entry");
            result.put(key, Math.addExact(result.getLong(key), amount));
        }
        return result;
    }
}
