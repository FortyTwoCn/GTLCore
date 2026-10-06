package org.gtlcore.gtlcore.mixin.gtm.recipe.Ingredient;

import com.gregtechceu.gtceu.api.recipe.ingredient.IntProviderIngredient;

import net.minecraft.world.item.crafting.Ingredient;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(IntProviderIngredient.class)
public interface IntProviderIngredientAccessor {

    @Accessor(value = "inner", remap = false)
    Ingredient gtlcore$getInner();

    @Accessor(value = "sampledCount", remap = false)
    int getSampledCount();
}
