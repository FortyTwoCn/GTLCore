package org.gtlcore.gtlcore.mixin.gtm.api.recipe;

import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;
import org.gtlcore.gtlcore.api.recipe.IRecipeIterator;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.lookup.RecipeDB;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.*;
import java.util.Iterator;
import java.util.function.Predicate;

/** The former GTRecipeLookup hook now runs at the native RecipeDB-backed recipe type entry point. */
@Mixin(GTRecipeType.class)
public abstract class GTRecipeLookupMixin {
    @Shadow(remap = false) @Final private RecipeDB db;
    @WrapMethod(method = "searchRecipe", remap = false)
    private Iterator<GTRecipe> gtlcore$groupedSearch(IRecipeCapabilityHolder holder, Predicate<GTRecipe> predicate,
                                                    Operation<Iterator<GTRecipe>> original) {
        if (!(holder instanceof IRecipeCapabilityMachine machine) || !machine.usesCoreRecipeRouting())
            return original.call(holder, predicate);
        return IRecipeIterator.search((GTRecipeType) (Object) this, db, holder, machine, predicate);
    }
}
