package org.gtlcore.gtlcore.api.machine.trait.MEPart;

import com.gregtechceu.gtceu.integration.ae2.utils.KeyStorage;

/** Gives the native recipe handler the machine's actual persisted output ledger. */
public interface IMENativeOutputBuffer {
    KeyStorage gtlcore$getOutputBuffer();
}
