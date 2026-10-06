package org.gtlcore.gtlcore.api.machine.trait.AECraft;

import com.gregtechceu.gtceu.utils.ISubscription;

public interface IMolecularAssemblerHandlerTrait extends IMolecularAssemblerHandler {

    ISubscription addChangedListener(Runnable listener);
}
