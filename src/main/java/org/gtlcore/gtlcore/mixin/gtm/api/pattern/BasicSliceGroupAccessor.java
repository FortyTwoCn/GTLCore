package org.gtlcore.gtlcore.mixin.gtm.api.pattern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(targets = "com.gregtechceu.gtceu.api.multiblock.pattern.BasicSliceStrategy$MultiblockSlice", remap = false)
public interface BasicSliceGroupAccessor {
    @Accessor("minRepeats") int gtlcore$getMin();
    @Accessor("maxRepeats") int gtlcore$getMax();
    @Accessor("startInclusive") int gtlcore$getStart();
    @Accessor("endExclusive") int gtlcore$getEnd();
    @Accessor("actualRepeats") void gtlcore$setActual(int actual);
}
