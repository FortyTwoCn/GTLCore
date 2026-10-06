package org.gtlcore.gtlcore.api.machine.trait;

import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;

/** Controller-specific exclusions, e.g. a dedicated steam-energy tank. */
public interface IRecipeInputHandlerPolicy {
    default boolean isRecipeInputHandlerExcluded(IRecipeHandler<?> handler) { return false; }
}
