package org.gtlcore.gtlcore.api.machine.trait;

import org.gtlcore.gtlcore.api.capability.IMERecipeHandler;

import com.gregtechceu.gtceu.api.capability.recipe.IO;

import com.gregtechceu.gtceu.utils.ISubscription;

/**
 * @author Dragonators
 * @param <T>
 */
public interface IMERecipeHandlerTrait<T, S> extends IMERecipeHandler<T, S> {

    IO getIo();

    ISubscription addChangedListener(Runnable var1);

    ISubscription addBufferChangedListener(Runnable var1);

    default int getPriority() {
        return switch (getIo()) {
            case OUT -> 10;
            case BOTH -> 5;
            case IN, NONE -> 0;
        };
    }
}
