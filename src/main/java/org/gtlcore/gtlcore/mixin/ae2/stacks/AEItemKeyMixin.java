package org.gtlcore.gtlcore.mixin.ae2.stacks;

import appeng.api.stacks.AEItemKey;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AEItemKey.class)
public abstract class AEItemKeyMixin {
    @Unique
    private volatile int gtlcore$fuzzySearchMaxValue = -1;

    @WrapMethod(method = "getFuzzySearchMaxValue", remap = false)
    private int gtlcore$cachedMaxDamage(Operation<Integer> original) {
        int value = gtlcore$fuzzySearchMaxValue;
        if (value < 0) gtlcore$fuzzySearchMaxValue = value = original.call();
        return value;
    }
}
