package org.gtlcore.gtlcore.integration.ae2;

import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAESlot;

/** Owned physical ME buffers; virtual stocking views must return no slots here. */
public interface IBufferedMEInventory {
    ExportOnlyAESlot[] getBufferedMESlots();
}
