package org.gtlcore.gtlcore.mixin.gtm.machine;

import org.gtlcore.gtlcore.api.recipe.CoreRecipeModifiers;
import org.gtlcore.gtlcore.common.machine.multiblock.generator.GeneratorArrayMachine;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.SimpleGeneratorMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.math.BigInteger;

@Mixin(SimpleGeneratorMachine.class)
public abstract class SimpleGeneratorMachineMixin {
    @Inject(method = "recipeModifier", at = @At("HEAD"), remap = false, cancellable = true, require = 1)
    private static void gtlcore$generator(MetaMachine machine, GTRecipe recipe, CallbackInfoReturnable<ModifierFunction> cir) {
        if (!(machine instanceof SimpleGeneratorMachine generator)) return;
        long eu = recipe.getOutputEUt().getTotalEU(); int tier = generator.getTier(), amperage = GeneratorArrayMachine.getAmperage(tier);
        int efficiency = GeneratorArrayMachine.getEfficiency(generator.getRecipeType(), tier);
        if (eu <= 0 || amperage <= 0 || efficiency <= 0) { cir.setReturnValue(ModifierFunction.NULL); return; }
        int limit = BigInteger.valueOf(GTValues.V[tier]).multiply(BigInteger.valueOf(amperage)).divide(BigInteger.valueOf(eu))
                .min(BigInteger.valueOf(Integer.MAX_VALUE)).intValueExact();
        cir.setReturnValue(source -> {
            var result = CoreRecipeModifiers.fastParallel(generator, source, limit, false).getFirst();
            if (result != null) result.duration = Math.max(1, (int) (result.duration * (efficiency / 100.0)));
            return result;
        });
    }
}
