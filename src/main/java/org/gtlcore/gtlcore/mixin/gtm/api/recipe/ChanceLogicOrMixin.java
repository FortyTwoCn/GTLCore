package org.gtlcore.gtlcore.mixin.gtm.api.recipe;

import org.gtlcore.gtlcore.api.recipe.chance.LongChanceLogic;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

@Mixin(targets = "com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic$1")
public abstract class ChanceLogicOrMixin {
    @Inject(method = "roll", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void gtlcore$exactOrRoll(RecipeCapability<?> cap, List<Content> entries, Object2IntMap<?> cache,
                                   int times, CallbackInfoReturnable<List<Content>> cir) {
        cir.setReturnValue(LongChanceLogic.OR.roll(cap, entries, cache, (long) times));
    }
}
