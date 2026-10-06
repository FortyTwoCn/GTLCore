package org.gtlcore.gtlcore.mixin.gtm.api.capability;

import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.api.recipe.IAdvancedContentModifier;

import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntProviderIngredient;
import com.gregtechceu.gtceu.api.recipe.lookup.ingredient.AbstractMapIngredient;

import net.neoforged.neoforge.common.crafting.SizedIngredient;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ItemRecipeCapability.class)
public class LongItemRecipeCapabilityMixin {
    @Inject(method = "copyWithModifier(Lnet/neoforged/neoforge/common/crafting/SizedIngredient;Lcom/gregtechceu/gtceu/api/recipe/content/ContentModifier;)Lnet/neoforged/neoforge/common/crafting/SizedIngredient;",
            at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void gtlcore$modifyQuantity(SizedIngredient content, ContentModifier modifier,
                                        CallbackInfoReturnable<SizedIngredient> cir) {
        if (content.ingredient().getCustomIngredient() instanceof IntProviderIngredient) return;
        if (LongIngredient.get(content) != null ||
                ((Object) modifier instanceof IAdvancedContentModifier precise && precise.gtlcore$hasDivision()) ||
                content.count() * modifier.multiplier() + modifier.addition() > Integer.MAX_VALUE) {
            cir.setReturnValue(LongIngredient.withAmount(content,
                    Math.max(1, IAdvancedContentModifier.applyLong(modifier, LongIngredient.amount(content)))));
        }
    }

    @Inject(method = "getDefaultMapIngredient", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void gtlcore$indexMatchingIngredient(Object value, CallbackInfoReturnable<List<AbstractMapIngredient>> cir) {
        if (value instanceof SizedIngredient sized && LongIngredient.get(sized) != null) {
            cir.setReturnValue(((ItemRecipeCapability) (Object) this)
                    .getDefaultMapIngredient(LongIngredient.matchingIngredient(sized)));
        }
    }
}
