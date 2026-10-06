package org.gtlcore.gtlcore.mixin.gtm.api.capability;

import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidParallelLogic;
import org.gtlcore.gtlcore.api.recipe.IAdvancedContentModifier;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntProviderFluidIngredient;
import com.gregtechceu.gtceu.api.recipe.lookup.ingredient.AbstractMapIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import java.util.List;

import com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native entry points; ordinary ingredients keep GTCEu's implementation. */
@Mixin(FluidRecipeCapability.class)
public class FluidRecipeCapabilityMixin {
    @Inject(method = "getMaxParallelByInput", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void gtlcore$longFluidInputParallel(IRecipeCapabilityHolder holder, GTRecipe recipe, int limit,
                                          boolean tick, CallbackInfoReturnable<Integer> cir) {
        var contents = (tick ? recipe.tickInputs : recipe.inputs).get(FluidRecipeCapability.CAP);
        if (LongFluidParallelLogic.hasLongIngredients(contents) || LongFluidParallelLogic.hasLongInventory(holder)) {
            cir.setReturnValue((int) LongFluidParallelLogic.getMaxParallelByInput(holder, recipe, limit, tick));
        }
    }

    @Inject(method = "limitMaxParallelByOutput", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void gtlcore$longFluidOutputParallel(IRecipeCapabilityHolder holder, GTRecipe recipe, int limit,
                                           boolean tick, CallbackInfoReturnable<Integer> cir) {
        if (holder instanceof FluidRecipeCapability.ICustomParallel) return;
        var contents = (tick ? recipe.tickOutputs : recipe.outputs).get(FluidRecipeCapability.CAP);
        if (contents == null || contents.isEmpty()) return;
        boolean large = LongFluidParallelLogic.hasLongIngredients(contents) || contents.stream().anyMatch(content ->
                LongFluidIngredient.amount(FluidRecipeCapability.CAP.of(content.content())) * (double) limit > Integer.MAX_VALUE);
        if (large) cir.setReturnValue((int) LongFluidParallelLogic.limitMaxParallelByOutput(holder, recipe, limit, tick));
    }
    @Inject(method = "copyWithModifier(Lnet/neoforged/neoforge/fluids/crafting/SizedFluidIngredient;Lcom/gregtechceu/gtceu/api/recipe/content/ContentModifier;)Lnet/neoforged/neoforge/fluids/crafting/SizedFluidIngredient;",
            at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void gtlcore$modifyFluidQuantity(SizedFluidIngredient content, ContentModifier modifier,
                                             CallbackInfoReturnable<SizedFluidIngredient> cir) {
        if (content.ingredient() instanceof IntProviderFluidIngredient) return;
        if (LongFluidIngredient.get(content) != null ||
                ((Object) modifier instanceof IAdvancedContentModifier precise && precise.gtlcore$hasDivision()) ||
                content.amount() * modifier.multiplier() + modifier.addition() > Integer.MAX_VALUE) {
            cir.setReturnValue(LongFluidIngredient.withAmount(content,
                    Math.max(1, IAdvancedContentModifier.applyLong(modifier, LongFluidIngredient.amount(content)))));
        }
    }

    @Inject(method = "getDefaultMapIngredient", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void gtlcore$indexMatchingFluid(Object value, CallbackInfoReturnable<List<AbstractMapIngredient>> cir) {
        if (value instanceof SizedFluidIngredient sized && LongFluidIngredient.get(sized) != null) {
            cir.setReturnValue(((FluidRecipeCapability) (Object) this)
                    .getDefaultMapIngredient(LongFluidIngredient.matchingIngredient(sized)));
        }
    }
}
