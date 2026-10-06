package org.gtlcore.gtlcore.mixin.gtm.gui;

import org.gtlcore.gtlcore.integration.jei.NativeRecipeEnergy;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.recipe.gui.CapabilityContentBuilder;
import com.gregtechceu.gtceu.api.recipe.gui.GTRecipeTypeUILayout;
import brachy.modularui.widgets.layout.Flow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Use the native ModularUI recipe capability layout for Core energy rows. */
@Mixin(GTRecipeTypeUILayout.class)
public abstract class GTRecipeWidgetMixin {
    @Inject(method = "<init>", at = @At("TAIL"), remap = false, require = 1)
    private void gtlcore$exactEnergy(CallbackInfo ci) {
        var info = ((GTRecipeTypeUILayout) (Object) this).capabilityInfo(EURecipeCapability.CAP);
        if (info.capabilityWidgetBuilder != CapabilityContentBuilder.EU) return;
        info.capabilityWidgetBuilder = (widget, content, io, perTick, recipeType, recipe, chanceTier, recipeTier) -> {
            if (widget instanceof Flow flow) NativeRecipeEnergy.fill(flow, recipe);
        };
    }
}
