package org.gtlcore.gtlcore.mixin.gtm.api.pattern;

import org.gtlcore.gtlcore.api.gui.preview.NativePreviewBlockMap;
import com.gregtechceu.gtceu.api.multiblock.predicates.BasePredicate;
import com.gregtechceu.gtceu.api.multiblock.util.AbstractStructureHelper;
import com.gregtechceu.gtceu.api.multiblock.util.BlockInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Map;

/** Preserve the native population algorithm while indexing its preview candidate counts. */
@Mixin(AbstractStructureHelper.class)
public abstract class NativePreviewPopulationMixin {
    @Inject(method = "countPopulatedGlobal", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private static void gtlcore$previewGlobal(Map<BlockPos, BlockInfo> blocks, BasePredicate predicate,
                                             CallbackInfoReturnable<Integer> cir) {
        if (blocks instanceof NativePreviewBlockMap indexed) cir.setReturnValue(indexed.countGlobal(predicate));
    }
    @Inject(method = "countPopulatedInLayer", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private static void gtlcore$previewLayer(Map<BlockPos, BlockInfo> blocks, BasePredicate predicate,
                                            Direction direction, int offset, CallbackInfoReturnable<Integer> cir) {
        if (blocks instanceof NativePreviewBlockMap indexed) cir.setReturnValue(indexed.countLayer(predicate, direction, offset));
    }
}
