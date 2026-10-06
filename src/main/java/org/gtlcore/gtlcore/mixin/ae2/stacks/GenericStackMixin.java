package org.gtlcore.gtlcore.mixin.ae2.stacks;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import appeng.api.stacks.GenericStack;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GenericStack.class)
public abstract class GenericStackMixin {

    @WrapMethod(method = "writeTag", remap = false)
    private static CompoundTag gtlcore$ownedTag(HolderLookup.Provider registries,
                                                @Nullable GenericStack stack,
                                                Operation<CompoundTag> original) {
        // Preserve the native key+amount codec; never mutate a cached key tag.
        return original.call(registries, stack).copy();
    }
}
