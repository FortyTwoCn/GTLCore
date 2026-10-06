package org.gtlcore.gtlcore.integration.ae2;

import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAESlot;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;

/** Transfers owned buffers without truncating quantities or discarding refused network insertions. */
public final class AEBufferedSlotTransactions {
    private AEBufferedSlotTransactions() {}

    public static void balance(ExportOnlyAESlot slot, MEStorage storage, IActionSource source) {
        var excess = slot.exceedStack();
        if (excess != null && excess.amount() > 0) {
            long accepted = storage.insert(excess.what(), excess.amount(), Actionable.MODULATE, source);
            subtract(slot, accepted);
            // A refused old key must stay in its slot until it can be returned.
            // Excess of the requested key also stays owned by the buffer.
            if (accepted < excess.amount()) return;
        }
        var requested = slot.requestStack();
        if (requested == null || requested.amount() <= 0) return;
        long extracted = storage.extract(requested.what(), requested.amount(), Actionable.MODULATE, source);
        if (extracted <= 0) return;
        var previous = slot.getStock();
        long amount = previous == null ? extracted : Math.addExact(previous.amount(), extracted);
        slot.setStock(new GenericStack(requested.what(), amount));
    }

    public static void flush(ExportOnlyAESlot slot, MEStorage storage, IActionSource source) {
        var stock = slot.getStock();
        if (stock != null && stock.amount() > 0)
            subtract(slot, storage.insert(stock.what(), stock.amount(), Actionable.MODULATE, source));
    }

    private static void subtract(ExportOnlyAESlot slot, long accepted) {
        if (accepted <= 0) return;
        var stock = slot.getStock();
        long remaining = stock.amount() - accepted;
        slot.setStock(remaining == 0 ? null : new GenericStack(stock.what(), remaining));
    }
}
