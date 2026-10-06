package org.gtlcore.gtlcore.mixin.gtm.registry;

import com.gregtechceu.gtceu.integration.kjs.recipe.GTRecipeSchema;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.gregtechceu.gtceu.api.recipe.ingredient.EnergyStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GTRecipeSchema.GTKubeRecipe.class)
public abstract class GTRecipeJSMixin {

    @Shadow(remap = false)
    public abstract GTRecipeSchema.GTKubeRecipe addData(String key, int data);

    @Inject(method = "EUt(Lcom/gregtechceu/gtceu/api/recipe/ingredient/EnergyStack$WithIO;)Lcom/gregtechceu/gtceu/integration/kjs/recipe/GTRecipeSchema$GTKubeRecipe;", at = @At("HEAD"), remap = false, require = 1)
    public void EUt(EnergyStack.WithIO eu, CallbackInfoReturnable<GTRecipeSchema.GTKubeRecipe> cir) {
        this.addData("euTier", GTUtil.getTierByVoltage(eu.voltage()));
    }
}
