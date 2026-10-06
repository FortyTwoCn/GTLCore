package org.gtlcore.gtlcore.integration.ae2;

import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAESlot;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import com.glodblock.github.extendedae.common.me.taglist.TagPriorityList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/** TagPriorityList retains ExtendedAE's wildcard/operator semantics on native AE2 keys. */
public final class TagFilteredStocking {
    private TagFilteredStocking() {}
    public static List<GenericStack> select(MEStorage network, IActionSource source, String white, String black,
                                            boolean byCount, Predicate<AEKey> kind, int limit) {
        var filter = new TagPriorityList(white, black);
        var candidates = new ArrayList<GenericStack>();
        for (var entry : network.getAvailableStacks()) {
            var key = entry.getKey(); long amount = entry.getLongValue();
            if (amount <= 0 || !kind.test(key) || !filter.isListed(key)) continue;
            long available = network.extract(key, amount, Actionable.SIMULATE, source);
            if (available <= 0) continue;
            candidates.add(new GenericStack(key, available));
            if (!byCount && candidates.size() == limit) break;
        }
        if (byCount) candidates.sort(Comparator.comparingLong(GenericStack::amount).reversed());
        return candidates.size() <= limit ? candidates : new ArrayList<>(candidates.subList(0, limit));
    }
    public static void afterExtraction(ExportOnlyAESlot slot, AEKey key, long amount) {
        var previous = slot.getStock();
        long left = previous == null || !key.equals(previous.what()) ? 0 : Math.max(0, previous.amount() - amount);
        slot.setStock(left <= 0 ? null : new GenericStack(key, left));
        // Native setStock(null) suppresses the callback when the view was already empty.
        if (previous == null) slot.getOnContentsChanged().run();
    }
}
