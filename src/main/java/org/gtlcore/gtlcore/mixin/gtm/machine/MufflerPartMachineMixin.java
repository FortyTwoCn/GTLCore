package org.gtlcore.gtlcore.mixin.gtm.machine;

import org.gtlcore.gtlcore.api.recipe.RecipeResult;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.machine.multiblock.part.MufflerPartMachine;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MufflerPartMachine.class, remap = false)
public abstract class MufflerPartMachineMixin {
    @Inject(method = "modifyRecipe", at = @At("RETURN"), require = 1)
    private void gtlcore$reportObstructedFront(GTRecipe recipe, CallbackInfoReturnable<GTRecipe> cir) {
        if (cir.getReturnValue() != null) return;
        for (var controller : ((MufflerPartMachine) (Object) this).getControllers()) {
            if (controller instanceof IRecipeLogicMachine machine)
                RecipeResult.of(machine, RecipeResult.fail(Component.translatable("gtceu.multiblock.universal.muffler_obstructed")));
        }
    }
}
