package org.gtlcore.gtlcore.mixin.gtm.api.machine.trait;

import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidRecipeHandler;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(NotifiableFluidTank.class)
public class NotifiableFluidTankMixin {
    @Inject(method = "handleRecipeInner", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void gtlcore$longFluidTransaction(IO io, @Nullable GTRecipe recipe, List<SizedFluidIngredient> left,
                                             boolean simulate, CallbackInfoReturnable<List<SizedFluidIngredient>> cir) {
        if (left.stream().noneMatch(ingredient -> LongFluidIngredient.get(ingredient) != null)) return;
        var tank = (NotifiableFluidTank) (Object) this;
        cir.setReturnValue(LongFluidRecipeHandler.handle(io, recipe, left, simulate, tank.getHandlerIO(),
                tank.getStorages(), tank.isAllowSameFluids(), tank.getMachine()));
    }
}
