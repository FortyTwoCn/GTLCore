package org.gtlcore.gtlcore.mixin.gtm.api.machine;

import com.gregtechceu.gtceu.api.multiblock.pattern.PatternState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Access to the native cache update; context maintenance uses PatternState's public API. */
@Mixin(value = PatternState.class, remap = false)
public interface IMultiblockStateInvoker {
    @Invoker("updateCache") void gtlcore$updateCache();
}
