package org.gtlcore.gtlcore.integration.ae2.handler;

import org.gtlcore.gtlcore.api.recipe.ingredient.CacheHashStrategies;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.*;
import lombok.Getter;

/** Predicate cache for one pattern slot. Quantities stay in the source inventory and are checked on every lookup. */
public final class SlotCacheManager implements INBTSerializable<CompoundTag> {
    private final Object2ObjectMap<Ingredient, AEItemKey> itemBestMatchCache =
            new Object2ObjectOpenCustomHashMap<>(CacheHashStrategies.IngredientHashStrategy.INSTANCE);
    private final Object2ObjectMap<FluidIngredient, AEFluidKey> fluidBestMatchCache =
            new Object2ObjectOpenCustomHashMap<>(CacheHashStrategies.FluidIngredientHashStrategy.INSTANCE);
    @Getter private int circuitCache = -1;
    @Getter private ItemStack circuitStack = ItemStack.EMPTY;

    public void setCircuitCache(int configuration) {
        if (configuration == -1) { clearCircuitCache(); return; }
        circuitStack = IntCircuitBehaviour.stack(configuration);
        circuitCache = configuration;
    }
    public void clearCircuitCache() { circuitCache = -1; circuitStack = ItemStack.EMPTY; }
    public void clearAllCaches() { itemBestMatchCache.clear(); fluidBestMatchCache.clear(); clearCircuitCache(); }

    public AEItemKey getBestItemMatch(SizedIngredient ingredient, Object2LongMap<AEItemKey> inventory, long amount) {
        return getBestItemMatch(LongIngredient.matchingIngredient(ingredient), inventory, amount);
    }
    public AEFluidKey getBestFluidMatch(SizedFluidIngredient ingredient, Object2LongMap<AEFluidKey> inventory, long amount) {
        return getBestFluidMatch(LongFluidIngredient.matchingIngredient(ingredient), inventory, amount);
    }
    public AEItemKey getBestItemMatchSimulate(SizedIngredient ingredient, Object2LongMap<AEItemKey> inventory,
                                            Object2LongMap<AEItemKey> catalysts, long amount) {
        return getBestItemMatchSimulate(LongIngredient.matchingIngredient(ingredient), inventory, catalysts, amount);
    }
    public AEFluidKey getBestFluidMatchSimulate(SizedFluidIngredient ingredient, Object2LongMap<AEFluidKey> inventory,
                                              Object2LongMap<AEFluidKey> catalysts, long amount) {
        return getBestFluidMatchSimulate(LongFluidIngredient.matchingIngredient(ingredient), inventory, catalysts, amount);
    }
    public AEItemKey getBestItemMatch(Ingredient ingredient, Object2LongMap<AEItemKey> inventory, long amount) {
        return getBestItemMatchSimulate(ingredient, inventory, Object2LongMaps.emptyMap(), amount);
    }
    public AEFluidKey getBestFluidMatch(FluidIngredient ingredient, Object2LongMap<AEFluidKey> inventory, long amount) {
        return getBestFluidMatchSimulate(ingredient, inventory, Object2LongMaps.emptyMap(), amount);
    }
    public AEItemKey getBestItemMatchSimulate(Ingredient ingredient, Object2LongMap<AEItemKey> inventory,
                                            Object2LongMap<AEItemKey> catalysts, long amount) {
        if (amount <= 0) return null;
        var cached = itemBestMatchCache.get(ingredient);
        if (cached != null && ingredient.test(cached.toStack()) && available(cached, inventory, catalysts, amount)) return cached;
        // Keep the encoded recipe candidate preference, then consider component-bearing variants in the actual stock.
        for (var stack : ingredient.getItems()) {
            var key = AEItemKey.of(stack);
            if (key != null && ingredient.test(key.toStack()) && available(key, inventory, catalysts, amount)) {
                itemBestMatchCache.put(ingredient, key); return key;
            }
        }
        for (var stock : java.util.List.of(inventory, catalysts)) for (var key : stock.keySet()) {
            if (ingredient.test(key.toStack()) && available(key, inventory, catalysts, amount)) {
                itemBestMatchCache.put(ingredient, key); return key;
            }
        }
        itemBestMatchCache.remove(ingredient);
        return null;
    }
    public AEFluidKey getBestFluidMatchSimulate(FluidIngredient ingredient, Object2LongMap<AEFluidKey> inventory,
                                              Object2LongMap<AEFluidKey> catalysts, long amount) {
        if (amount <= 0) return null;
        var cached = fluidBestMatchCache.get(ingredient);
        if (cached != null && ingredient.test(cached.toStack(1)) && available(cached, inventory, catalysts, amount)) return cached;
        for (var stack : ingredient.getStacks()) {
            var key = AEFluidKey.of(stack);
            if (key != null && ingredient.test(key.toStack(1)) && available(key, inventory, catalysts, amount)) {
                fluidBestMatchCache.put(ingredient, key); return key;
            }
        }
        for (var stock : java.util.List.of(inventory, catalysts)) for (var key : stock.keySet()) {
            if (ingredient.test(key.toStack(1)) && available(key, inventory, catalysts, amount)) {
                fluidBestMatchCache.put(ingredient, key); return key;
            }
        }
        fluidBestMatchCache.remove(ingredient);
        return null;
    }
    private static <K> boolean available(K key, Object2LongMap<K> inventory, Object2LongMap<K> catalysts, long amount) {
        return inventory.getLong(key) >= amount || catalysts.getLong(key) >= amount;
    }

    @Override public CompoundTag serializeNBT(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putInt("circuit", circuitCache);
        tag.put("itemCache", encodeCache(registries, itemBestMatchCache, Ingredient.CODEC));
        tag.put("fluidCache", encodeCache(registries, fluidBestMatchCache, FluidIngredient.CODEC));
        return tag;
    }
    private static <P, K extends AEKey> ListTag encodeCache(HolderLookup.Provider registries, Object2ObjectMap<P, K> cache, Codec<P> codec) {
        var list = new ListTag(); var ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        for (var entry : cache.object2ObjectEntrySet()) {
            var tag = new CompoundTag();
            tag.put("ingredient", codec.encodeStart(ops, entry.getKey()).getOrThrow());
            tag.put("aekey", entry.getValue().toTagGeneric(registries));
            list.add(tag);
        }
        return list;
    }
    @Override public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
        clearAllCaches(); var ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        for (var entry : tag.getList("itemCache", Tag.TAG_COMPOUND)) {
            var item = (CompoundTag) entry;
            if (!item.contains("ingredient")) continue;
            var predicate = Ingredient.CODEC.parse(ops, item.get("ingredient")).result();
            var key = AEKey.fromTagGeneric(registries, item.getCompound("aekey"));
            if (predicate.isPresent() && key instanceof AEItemKey value && predicate.get().test(value.toStack())) {
                itemBestMatchCache.put(predicate.get(), value);
            }
        }
        for (var entry : tag.getList("fluidCache", Tag.TAG_COMPOUND)) {
            var item = (CompoundTag) entry;
            if (!item.contains("ingredient")) continue;
            var predicate = FluidIngredient.CODEC.parse(ops, item.get("ingredient")).result();
            var key = AEKey.fromTagGeneric(registries, item.getCompound("aekey"));
            if (predicate.isPresent() && key instanceof AEFluidKey value && predicate.get().test(value.toStack(1))) {
                fluidBestMatchCache.put(predicate.get(), value);
            }
        }
        if (tag.contains("circuit", Tag.TAG_INT)) {
            int circuit = tag.getInt("circuit");
            if (circuit >= 0 && circuit <= IntCircuitBehaviour.CIRCUIT_MAX) setCircuitCache(circuit);
        }
    }
}
