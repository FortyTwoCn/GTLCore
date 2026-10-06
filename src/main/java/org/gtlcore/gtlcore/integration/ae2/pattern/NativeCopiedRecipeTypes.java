package org.gtlcore.gtlcore.integration.ae2.pattern;

import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import org.jetbrains.annotations.Nullable;

/** The pinned native GT chemical builder copies recipes into its large type at generation time. */
public final class NativeCopiedRecipeTypes {
    private NativeCopiedRecipeTypes() {}
    @Nullable public static GTRecipeType smallType(GTRecipeType type) {
        return type == GTRecipeTypes.LARGE_CHEMICAL_RECIPES.value() ? GTRecipeTypes.CHEMICAL_RECIPES.value() : null;
    }
}
