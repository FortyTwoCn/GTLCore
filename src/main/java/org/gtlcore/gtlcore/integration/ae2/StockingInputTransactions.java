package org.gtlcore.gtlcore.integration.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import it.unimi.dsi.fastutil.objects.*;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import static org.gtlcore.gtlcore.utils.NumberUtils.saturatedAdd;

/** Reserves distinct AE keys before committing local ownership; partial network withdrawals are refunded exactly once. */
public final class StockingInputTransactions {
    private StockingInputTransactions() {}
    public static <K extends AEKey, T> boolean handle(Object2LongMap<T> demands, Object2LongMap<K> owned,
            Object2LongMap<K> catalysts, List<K> configured, java.util.function.BiPredicate<T, K> matches,
            java.util.function.ToLongFunction<K> availableInNetwork, boolean simulate,
            @Nullable MEStorage network, IActionSource actionSource, Object2LongMap<AEKey> buffer, Runnable changed) {
        var workingOwned = new Object2LongOpenHashMap<>(owned);
        var workingStock = new Object2LongOpenHashMap<K>();
        var withdrawals = new Object2LongOpenHashMap<K>();
        var candidates = new java.util.LinkedHashSet<K>(owned.keySet());
        candidates.addAll(configured);
        if (simulate) candidates.addAll(catalysts.keySet());
        for (K key : configured) workingStock.put(key, Math.max(0, availableInNetwork.applyAsLong(key)));
        for (var demand : Object2LongMaps.fastIterable(demands)) {
            long need = demand.getLongValue(); if (need <= 0) continue;
            K matched = null;
            for (K key : candidates) {
                if (!matches.test(demand.getKey(), key)) continue;
                if (simulate && catalysts.getLong(key) >= need || saturatedAdd(workingOwned.getLong(key), workingStock.getLong(key)) >= need) {
                    matched = key; break;
                }
            }
            if (matched == null) return false;
            if (simulate && catalysts.getLong(matched) >= need) continue;
            long local = Math.min(need, workingOwned.getLong(matched));
            long rest = workingOwned.getLong(matched) - local;
            if (rest == 0) workingOwned.removeLong(matched); else workingOwned.put(matched, rest);
            long stock = need - local;
            if (stock > 0) {
                workingStock.put(matched, workingStock.getLong(matched) - stock);
                withdrawals.put(matched, Math.addExact(withdrawals.getLong(matched), stock));
            }
        }
        if (simulate) return true;
        if (!withdrawals.isEmpty() && network == null) return false;
        // Reserve enough room for a refused network rollback before taking ownership of any extracted material.
        try {
            for (var entry : Object2LongMaps.fastIterable(withdrawals)) Math.addExact(buffer.getLong(entry.getKey()), entry.getLongValue());
        } catch (ArithmeticException overflow) { return false; }
        var taken = new Object2LongOpenHashMap<K>();
        for (var entry : Object2LongMaps.fastIterable(withdrawals)) {
            long extracted = network.extract(entry.getKey(), entry.getLongValue(), Actionable.MODULATE, actionSource);
            if (extracted > 0) taken.put(entry.getKey(), extracted);
            if (extracted != entry.getLongValue()) {
                for (var refund : Object2LongMaps.fastIterable(taken)) {
                    long returned = network.insert(refund.getKey(), refund.getLongValue(), Actionable.MODULATE, actionSource);
                    long pending = refund.getLongValue() - returned;
                    if (pending > 0) buffer.put(refund.getKey(), Math.addExact(buffer.getLong(refund.getKey()), pending));
                }
                changed.run(); return false;
            }
        }
        owned.clear(); owned.putAll(workingOwned); demands.clear(); changed.run();
        return true;
    }

}
