package org.gtlcore.gtlcore.common.data;

import org.gtlcore.gtlcore.common.recipe.condition.GravityCondition;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.condition.RecipeConditionType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.registry.GTRegistration;
import com.mojang.serialization.MapCodec;
import com.tterrag.registrate.util.entry.RegistryEntry;

public final class GTLRecipeConditions {
    private GTLRecipeConditions() {}
    public static final RegistryEntry<RecipeConditionType<?>, RecipeConditionType<GravityCondition>> GRAVITY = register("gravity", GravityCondition::new, GravityCondition.CODEC);
    private static <T extends RecipeCondition<T>> RegistryEntry<RecipeConditionType<?>, RecipeConditionType<T>> register(
            String name, RecipeConditionType.ConditionFactory<T> factory, MapCodec<T> codec) {
        return GTRegistration.REGISTRATE.simple(name, GTRegistries.Keys.RECIPE_CONDITION, () -> new RecipeConditionType<>(factory, codec));
    }
    public static void init() {}
}
