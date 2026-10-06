package org.gtlcore.gtlcore.mixin.ae2.stacks;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import appeng.api.stacks.AEKey;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.lang.ref.WeakReference;

@Mixin(AEKey.class)
public abstract class AEKeyMixin {

    @Unique
    private WeakReference<HolderLookup.Provider> gtlcore$tagRegistries;
    @Unique
    private CompoundTag gtlcore$tagGenericCache;

    // Keep AE2's native codec (#t, data components, and missing-content recovery).
    // A returned tag is caller-owned, and a cache cannot cross registry contexts.
    @WrapMethod(method = "toTagGeneric", remap = false)
    private synchronized CompoundTag gtlcore$cachedTag(HolderLookup.Provider registries,
                                                       Operation<CompoundTag> original) {
        if (gtlcore$tagGenericCache == null || gtlcore$tagRegistries.get() != registries) {
            gtlcore$tagGenericCache = original.call(registries);
            gtlcore$tagRegistries = new WeakReference<>(registries);
        }
        return gtlcore$tagGenericCache.copy();
    }
}
