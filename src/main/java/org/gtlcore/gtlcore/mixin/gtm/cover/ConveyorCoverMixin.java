package org.gtlcore.gtlcore.mixin.gtm.cover;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.common.cover.ConveyorCover;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ConveyorCover.class)
public abstract class ConveyorCoverMixin {
    // Includes robot arms: initialize the native limit, setting and budget together.
    @ModifyVariable(method = "<init>(Lcom/gregtechceu/gtceu/api/cover/CoverDefinition;Lcom/gregtechceu/gtceu/api/capability/ICoverable;Lnet/minecraft/core/Direction;II)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 1, remap = false, require = 1)
    private static int gtlcore$itemRate(int nativeRate, CoverDefinition definition, ICoverable holder,
                                      Direction side, int tier, int requestedRate) {
        return tier == GTValues.ULV ? Integer.MAX_VALUE : 2 * (1 << (2 * Math.min(tier, GTValues.LuV)));
    }
}
