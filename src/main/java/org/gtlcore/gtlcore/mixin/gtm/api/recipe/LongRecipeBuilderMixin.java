package org.gtlcore.gtlcore.mixin.gtm.api.recipe;

import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;

import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;

import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.fluids.crafting.DataComponentFluidIngredient;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native GT's Object output overload otherwise rejects SizedIngredient and silently omits the output. */
@Mixin(GTRecipeBuilder.class)
public class LongRecipeBuilderMixin {
    @Inject(method = "outputFluids(Lnet/neoforged/neoforge/fluids/FluidStack;)Lcom/gregtechceu/gtceu/data/recipe/builder/GTRecipeBuilder;",
            at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void gtlcore$componentFluidOutput(FluidStack output, CallbackInfoReturnable<GTRecipeBuilder> cir) {
        if (!output.isEmpty() && !output.isComponentsPatchEmpty()) {
            cir.setReturnValue(((GTRecipeBuilder) (Object) this).output(FluidRecipeCapability.CAP,
                    new SizedFluidIngredient(DataComponentFluidIngredient.of(true, output), output.getAmount())));
        }
    }

    @Inject(method = "outputFluids([Lnet/neoforged/neoforge/fluids/FluidStack;)Lcom/gregtechceu/gtceu/data/recipe/builder/GTRecipeBuilder;",
            at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void gtlcore$componentFluidOutputs(FluidStack[] outputs, CallbackInfoReturnable<GTRecipeBuilder> cir) {
        if (java.util.Arrays.stream(outputs).anyMatch(output -> !output.isEmpty() && !output.isComponentsPatchEmpty())) {
            var ingredients = java.util.Arrays.stream(outputs).map(output -> output.isComponentsPatchEmpty() ?
                    SizedFluidIngredient.of(output) : new SizedFluidIngredient(DataComponentFluidIngredient.of(true, output), output.getAmount()))
                    .toArray(SizedFluidIngredient[]::new);
            cir.setReturnValue(((GTRecipeBuilder) (Object) this).output(FluidRecipeCapability.CAP, ingredients));
        }
    }

    @Inject(method = "outputItems(Ljava/lang/Object;)Lcom/gregtechceu/gtceu/data/recipe/builder/GTRecipeBuilder;",
            at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void gtlcore$sizedOutput(Object output, CallbackInfoReturnable<GTRecipeBuilder> cir) {
        if (output instanceof SizedIngredient sized) {
            cir.setReturnValue(((GTRecipeBuilder) (Object) this).output(ItemRecipeCapability.CAP, sized));
        }
    }

    @Inject(method = "outputItems(Ljava/lang/Object;I)Lcom/gregtechceu/gtceu/data/recipe/builder/GTRecipeBuilder;",
            at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void gtlcore$sizedOutputWithCount(Object output, int count, CallbackInfoReturnable<GTRecipeBuilder> cir) {
        if (output instanceof SizedIngredient sized) {
            cir.setReturnValue(((GTRecipeBuilder) (Object) this).output(ItemRecipeCapability.CAP,
                    new SizedIngredient(LongIngredient.matchingIngredient(sized), count)));
        }
    }

    @Inject(method = "inputItems(Ljava/lang/Object;I)Lcom/gregtechceu/gtceu/data/recipe/builder/GTRecipeBuilder;",
            at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void gtlcore$replaceLongInputCount(Object input, int count, CallbackInfoReturnable<GTRecipeBuilder> cir) {
        if (input instanceof SizedIngredient sized && LongIngredient.get(sized) != null) {
            cir.setReturnValue(((GTRecipeBuilder) (Object) this).inputItems(
                    new SizedIngredient(LongIngredient.matchingIngredient(sized), count)));
        }
    }

}
