package org.gtlcore.gtlcore.mixin.jei;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

@Pseudo
@Mixin(targets = "mezz.jei.gui.util.FocusUtil", remap = false)
public abstract class ClientConfigMixin {

    // FTB Quests exposes filled buckets as item focuses, so JEI must also inspect their contained fluid.
    @ModifyExpressionValue(method = "createFocuses",
            at = @At(value = "INVOKE", target = "Ljava/lang/Boolean;booleanValue()Z", ordinal = 0),
            remap = false, require = 1)
    private boolean gtlcore$enableFluidContainerRecipeLookup(boolean configured) {
        return true;
    }
}
