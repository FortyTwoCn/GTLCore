package org.gtlcore.gtlcore.api.machine.trait.AECraft;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

/** Server-owned craft queue and refund buffer of a molecular assembler IO part. */
public interface IMolecularAssemblerOutputMachine {
    Object2LongMap<GenericStack> getOutputItems();

    Object2LongMap<AEKey> getBuffer();

    void notifyOutputBufferChanged();
}
