package org.gtlcore.gtlcore.mixin.gtm.api.recipe;

import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.api.recipe.NativeRecipeState;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Extension state travels through the same native recipe codecs as inputs and outputs. */
@Mixin(GTRecipe.class)
public abstract class GTRecipeMixin implements IGTRecipe {
    @Override
    public int getEuTier() {
        GTRecipe recipe = (GTRecipe) (Object) this;
        return recipe.data.contains("euTier") ? Math.max(0, recipe.data.getInt("euTier")) :
                RecipeHelper.getPreOCRecipeEuTier(recipe);
    }

    @Override public long getRealParallels() { return NativeRecipeState.realParallels((GTRecipe) (Object) this); }
    @Override public void setRealParallels(long value) { NativeRecipeState.realParallels((GTRecipe) (Object) this, value); }
    @Override public int getBatchSize() { return NativeRecipeState.batchSize((GTRecipe) (Object) this); }
    @Override public void setBatchSize(int value) { NativeRecipeState.batchSize((GTRecipe) (Object) this, value); }
    @Override public boolean isBatchProcessed() { return NativeRecipeState.flag((GTRecipe) (Object) this, "batch_processed"); }
    @Override public void setBatchProcessed(boolean value) { NativeRecipeState.flag((GTRecipe) (Object) this, "batch_processed", value); }
    @Override public boolean isSubTickParallelized() { return NativeRecipeState.flag((GTRecipe) (Object) this, "subtick_parallelized"); }
    @Override public void setSubTickParallelized(boolean value) { NativeRecipeState.flag((GTRecipe) (Object) this, "subtick_parallelized", value); }
    @Override public void setHasTick(boolean value) { NativeRecipeState.flag((GTRecipe) (Object) this, "has_tick", value); }

    @Inject(method = "hasTick", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void gtlcore$tickOverride(CallbackInfoReturnable<Boolean> cir) {
        var override = NativeRecipeState.tickOverride((GTRecipe) (Object) this);
        if (override != null) cir.setReturnValue(override);
    }

    @Inject(method = "copyWithoutTicks", at = @At("RETURN"), remap = false, require = 1)
    private void gtlcore$removeTickOverride(CallbackInfoReturnable<GTRecipe> cir) {
        var copy = cir.getReturnValue();
        copy.inputs.replaceAll((cap, list) -> list.stream().map(content -> content.copy(cap))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new)));
        copy.outputs.replaceAll((cap, list) -> list.stream().map(content -> content.copy(cap))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new)));
        NativeRecipeState.clearTickOverride(copy);
    }
}
