package org.gtlcore.gtlcore.api.recipe.ingredient;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntProviderFluidIngredient;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEFluidSlot;

import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/** Native AE2 long transactions; simulated reservations are shared by all ingredients of one recipe. */
public final class AEFluidRecipeHandler {
    private AEFluidRecipeHandler() {}

    public static List<SizedFluidIngredient> handleBuffered(IO io, @Nullable GTRecipe recipe,
                                                            List<SizedFluidIngredient> left, boolean simulate,
                                                            ExportOnlyAEFluidSlot[] slots, MetaMachine machine) {
        if (io != IO.IN) return left;
        Runnable[] listeners = new Runnable[slots.length];
        boolean[] changed = new boolean[slots.length];
        long[] available = new long[slots.length];
        Arrays.fill(available, -1);
        for (int i = 0; i < slots.length; i++) {
            listeners[i] = slots[i].getOnContentsChanged();
            slots[i].setOnContentsChanged(() -> {});
        }
        try {
            for (var iterator = left.listIterator(); iterator.hasNext();) {
                var sized = iterator.next();
                var matching = LongFluidIngredient.matchingIngredient(sized);
                if (matching.isEmpty()) { iterator.remove(); continue; }
                long needed = amount(sized, simulate);
                for (int i = 0; i < slots.length && needed > 0; i++) {
                    var stock = slots[i].getStock();
                    if (stock == null || !(stock.what() instanceof AEFluidKey key) || stock.amount() <= 0) continue;
                    if (!matching.test(key.toStack(1))) continue;
                    if (available[i] < 0) available[i] = stock.amount();
                    long extracted = Math.min(needed, available[i]);
                    if (extracted <= 0) continue;
                    available[i] -= extracted;
                    needed -= extracted;
                    if (!simulate) {
                        slots[i].setStock(available[i] == 0 ? null : new GenericStack(key, available[i]));
                        changed[i] = true;
                        LongFluidRecipeHandler.recordConsumed(machine, recipe,
                                LongFluidIngredient.create(key.toStack(1), extracted));
                    }
                }
                update(iterator, sized, needed);
            }
            return left;
        } finally {
            for (int i = 0; i < slots.length; i++) slots[i].setOnContentsChanged(listeners[i]);
            if (!simulate) {
                Set<Runnable> notified = Collections.newSetFromMap(new IdentityHashMap<>());
                for (int i = 0; i < slots.length; i++) {
                    if (changed[i] && notified.add(listeners[i])) listeners[i].run();
                }
            }
        }
    }

    public static List<SizedFluidIngredient> handleNetwork(IO io, @Nullable GTRecipe recipe,
                                                           List<SizedFluidIngredient> left, boolean simulate,
                                                           ExportOnlyAEFluidSlot[] slots, MetaMachine machine,
                                                           MEStorage network, IActionSource source) {
        if (io != IO.IN) return left;
        Set<AEFluidKey> keys = new LinkedHashSet<>();
        for (var slot : slots) {
            var config = slot.getConfig();
            if (config != null && config.what() instanceof AEFluidKey key) keys.add(key);
        }
        Map<AEFluidKey, Long> available = new HashMap<>();
        for (var iterator = left.listIterator(); iterator.hasNext();) {
            var sized = iterator.next();
            var matching = LongFluidIngredient.matchingIngredient(sized);
            if (matching.isEmpty()) { iterator.remove(); continue; }
            long needed = amount(sized, simulate);
            for (var key : keys) {
                if (needed <= 0) break;
                if (!matching.test(key.toStack(1))) continue;
                long extracted;
                if (simulate) {
                    long stock = available.computeIfAbsent(key,
                            value -> network.extract(value, Long.MAX_VALUE, Actionable.SIMULATE, source));
                    extracted = Math.min(needed, stock);
                    available.put(key, stock - extracted);
                } else {
                    extracted = network.extract(key, needed, Actionable.MODULATE, source);
                    if (extracted > 0) {
                        for (var slot : slots) {
                            var config = slot.getConfig();
                            if (config == null || !key.equals(config.what())) continue;
                            var stock = slot.getStock();
                            long remaining = stock == null ? 0 : Math.max(0, stock.amount() - extracted);
                            slot.setStock(remaining == 0 ? null : new GenericStack(key, remaining));
                        }
                        LongFluidRecipeHandler.recordConsumed(machine, recipe,
                                LongFluidIngredient.create(key.toStack(1), extracted));
                    }
                }
                needed -= extracted;
            }
            update(iterator, sized, needed);
        }
        return left;
    }

    private static long amount(SizedFluidIngredient sized, boolean simulate) {
        var matching = LongFluidIngredient.matchingIngredient(sized);
        return matching instanceof IntProviderFluidIngredient provider && simulate ?
                provider.getMaxRoll() : LongFluidIngredient.amount(sized);
    }

    private static void update(ListIterator<SizedFluidIngredient> iterator, SizedFluidIngredient original, long needed) {
        if (needed <= 0) iterator.remove();
        else {
            var matching = LongFluidIngredient.matchingIngredient(original);
            if (matching instanceof IntProviderFluidIngredient provider) {
                iterator.set(new SizedFluidIngredient(provider.getInner(), Math.toIntExact(needed)));
            } else iterator.set(LongFluidIngredient.withAmount(original, needed));
        }
    }
}
