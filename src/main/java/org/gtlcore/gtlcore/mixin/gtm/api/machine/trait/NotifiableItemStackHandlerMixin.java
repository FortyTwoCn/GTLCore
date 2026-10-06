package org.gtlcore.gtlcore.mixin.gtm.api.machine.trait;

import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongItemRecipeHandler;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;

import net.neoforged.neoforge.common.crafting.SizedIngredient;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(NotifiableItemStackHandler.class)
public abstract class NotifiableItemStackHandlerMixin {
    @Inject(method = "handleRecipe", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private static void gtlcore$handleLongRecipe(IO io, @Nullable GTRecipe recipe, List<SizedIngredient> left,
                                                boolean simulate, IO handlerIO, CustomItemStackHandler storage,
                                                @Nullable MetaMachine machine,
                                                CallbackInfoReturnable<List<SizedIngredient>> cir) {
        if (left.stream().anyMatch(ingredient -> LongIngredient.get(ingredient) != null)) {
            cir.setReturnValue(LongItemRecipeHandler.handle(io, recipe, left, simulate, handlerIO, storage, machine));
        }
    }
}
