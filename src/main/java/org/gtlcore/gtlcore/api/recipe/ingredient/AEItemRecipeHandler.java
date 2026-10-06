package org.gtlcore.gtlcore.api.recipe.ingredient;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntProviderIngredient;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEItemSlot;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import org.jetbrains.annotations.Nullable;
import java.util.*;

/** AE item transactions keep long amounts and component predicates through simulation and partial consumption. */
public final class AEItemRecipeHandler {
    private AEItemRecipeHandler() {}

    public static List<SizedIngredient> handleBuffered(IO io, @Nullable GTRecipe recipe, List<SizedIngredient> left,
                                                       boolean simulate, ExportOnlyAEItemSlot[] slots,
                                                       @Nullable MetaMachine machine) {
        if (io != IO.IN) return left;
        long[] available = new long[slots.length]; Arrays.fill(available, -1);
        Runnable[] listeners = suppressListeners(slots); boolean[] changed = new boolean[slots.length];
        try {
            for (var iterator = left.listIterator(); iterator.hasNext();) {
                var sized = iterator.next(); var matching = LongIngredient.matchingIngredient(sized);
                if (matching.hasNoItems()) { iterator.remove(); continue; }
                long needed = amount(sized, simulate);
                for (int i = 0; i < slots.length && needed > 0; i++) {
                    var stock = slots[i].getStock();
                    if (stock == null || stock.amount() <= 0 || !(stock.what() instanceof AEItemKey key) ||
                            !matching.test(key.toStack())) continue;
                    if (available[i] < 0) available[i] = stock.amount();
                    long extracted = Math.min(needed, available[i]);
                    if (extracted <= 0) continue;
                    available[i] -= extracted; needed -= extracted;
                    if (!simulate) {
                        slots[i].setStock(available[i] == 0 ? null : new GenericStack(key, available[i]));
                        changed[i] = true;
                        LongItemRecipeHandler.recordConsumed(machine, recipe, LongIngredient.create(key.toStack(), extracted));
                    }
                }
                update(iterator, sized, needed);
            }
            return left;
        } finally { restoreListeners(slots, listeners, changed, simulate); }
    }

    public static List<SizedIngredient> handleNetwork(IO io, @Nullable GTRecipe recipe, List<SizedIngredient> left,
                                                      boolean simulate, ExportOnlyAEItemSlot[] slots,
                                                      @Nullable MetaMachine machine, MEStorage network, IActionSource source) {
        if (io != IO.IN) return left;
        Set<AEItemKey> keys = new LinkedHashSet<>();
        for (var slot : slots) {
            var config = slot.getConfig();
            if (config != null && config.what() instanceof AEItemKey key) keys.add(key);
        }
        Map<AEItemKey, Long> available = new HashMap<>();
        Runnable[] listeners = suppressListeners(slots); boolean[] changed = new boolean[slots.length];
        try {
            for (var iterator = left.listIterator(); iterator.hasNext();) {
                var sized = iterator.next(); var matching = LongIngredient.matchingIngredient(sized);
                if (matching.hasNoItems()) { iterator.remove(); continue; }
                long needed = amount(sized, simulate);
                for (var key : keys) {
                    if (needed <= 0) break;
                    if (!matching.test(key.toStack())) continue;
                    long extracted;
                    if (simulate) {
                        long stock = available.computeIfAbsent(key, value -> network.extract(value, Long.MAX_VALUE, Actionable.SIMULATE, source));
                        extracted = Math.min(needed, stock); available.put(key, stock - extracted);
                    } else {
                        extracted = network.extract(key, needed, Actionable.MODULATE, source);
                        if (extracted > 0) {
                            for (int i = 0; i < slots.length; i++) {
                                var config = slots[i].getConfig();
                                if (config == null || !key.equals(config.what())) continue;
                                var stock = slots[i].getStock();
                                long remaining = stock == null ? 0 : Math.max(0, stock.amount() - extracted);
                                slots[i].setStock(remaining == 0 ? null : new GenericStack(key, remaining));
                                changed[i] = true;
                            }
                            LongItemRecipeHandler.recordConsumed(machine, recipe, LongIngredient.create(key.toStack(), extracted));
                        }
                    }
                    needed -= extracted;
                }
                update(iterator, sized, needed);
            }
            return left;
        } finally { restoreListeners(slots, listeners, changed, simulate); }
    }
    private static long amount(SizedIngredient ingredient, boolean simulate) {
        var custom = LongIngredient.matchingIngredient(ingredient).getCustomIngredient();
        return simulate && custom instanceof IntProviderIngredient provider ? provider.getMaxRoll() : LongIngredient.amount(ingredient);
    }
    private static void update(ListIterator<SizedIngredient> iterator, SizedIngredient original, long needed) {
        if (needed <= 0) iterator.remove();
        else {
            var custom = LongIngredient.matchingIngredient(original).getCustomIngredient();
            iterator.set(custom instanceof IntProviderIngredient provider ?
                    new SizedIngredient(provider.getInner(), Math.toIntExact(needed)) : LongIngredient.withAmount(original, needed));
        }
    }
    private static Runnable[] suppressListeners(ExportOnlyAEItemSlot[] slots) {
        Runnable[] listeners = new Runnable[slots.length];
        for (int i = 0; i < slots.length; i++) { listeners[i] = slots[i].getOnContentsChanged(); slots[i].setOnContentsChanged(() -> {}); }
        return listeners;
    }
    private static void restoreListeners(ExportOnlyAEItemSlot[] slots, Runnable[] listeners, boolean[] changed, boolean simulate) {
        Set<Runnable> notified = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = 0; i < slots.length; i++) slots[i].setOnContentsChanged(listeners[i]);
        for (int i = 0; i < slots.length; i++) {
            if (!simulate && changed[i] && listeners[i] != null && notified.add(listeners[i])) listeners[i].run();
        }
    }
}
