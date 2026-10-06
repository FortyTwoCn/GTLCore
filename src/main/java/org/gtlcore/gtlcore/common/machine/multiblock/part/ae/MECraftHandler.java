package org.gtlcore.gtlcore.common.machine.multiblock.part.ae;

import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.api.machine.trait.AECraft.IMolecularAssemblerOutputMachine;

import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

import org.jetbrains.annotations.Nullable;

public class MECraftHandler extends NotifiableMAHandlerTrait {

    @Override
    protected List<Class<?>> validMachineClasses() {
        return List.of(IMolecularAssemblerOutputMachine.class);
    }

    protected IMolecularAssemblerOutputMachine getOutputMachine() {
        return (IMolecularAssemblerOutputMachine) getMachine();
    }

    @Override
    public void handleRecipeOutput(GTRecipe recipe) {
        if (!tryHandleRecipeOutput(recipe)) throw new IllegalStateException("Molecular assembler output buffer is full");
    }

    @Override
    public boolean tryHandleRecipeOutput(GTRecipe recipe) {
        final var buffer = getOutputMachine().getBuffer();
        Map<AEKey, Long> totals = new LinkedHashMap<>();
        for (Content content : recipe.outputs.getOrDefault(ItemRecipeCapability.CAP, Collections.emptyList())) {
            var ingredient = ItemRecipeCapability.CAP.of(content.content());
            var candidates = LongIngredient.matchingIngredient(ingredient).getItems();
            if (candidates.length == 0) return false;
            var key = AEItemKey.of(candidates[0]);
            long amount = LongIngredient.amount(ingredient);
            long previous = totals.getOrDefault(key, buffer.getLong(key));
            if (amount <= 0 || previous < 0 || amount > Long.MAX_VALUE - previous) return false;
            totals.put(key, previous + amount);
        }
        totals.forEach(buffer::put);
        markAsChanged();
        getOutputMachine().notifyOutputBufferChanged();
        return true;
    }

    @Override
    public @Nullable GTRecipe extractGTRecipe(long parallelAmount, int tickDuration) {
        return extractGTRecipe(parallelAmount, tickDuration, false);
    }

    /** Additions' infinity mode uses one tick and drains every representable queued key. */
    public @Nullable GTRecipe extractAllGTRecipe() {
        return extractGTRecipe(Long.MAX_VALUE, 1, true);
    }

    private @Nullable GTRecipe extractGTRecipe(long parallelAmount, int tickDuration, boolean unlimited) {
        if (parallelAmount <= 0 || tickDuration <= 0) return null;
        var queue = getOutputMachine().getOutputItems();
        List<Content> outputList = new ObjectArrayList<>();
        Map<GenericStack, Long> toExtract = new LinkedHashMap<>();
        Map<AEItemKey, Long> amounts = new LinkedHashMap<>();
        long remain = parallelAmount;
        for (var it = Object2LongMaps.fastIterator(queue); it.hasNext() && (unlimited || remain > 0);) {
            var entry = it.next();
            var key = entry.getKey();
            if (!(key.what() instanceof AEItemKey aeItemKey)) {
                continue;
            }
            long multiply = entry.getLongValue();
            if (multiply <= 0 || key.amount() <= 0) continue;
            long previous = amounts.getOrDefault(aeItemKey, 0L);
            long extract = Math.min(multiply, (Long.MAX_VALUE - previous) / key.amount());
            if (!unlimited) extract = Math.min(extract, remain);
            if (extract == 0) continue;
            long amount = Math.multiplyExact(extract, key.amount());
            outputList.add(new Content(LongIngredient.create(aeItemKey.toStack(), amount),
                    ChanceLogic.getMaxChancedValue(), ChanceLogic.getMaxChancedValue()));
            toExtract.put(key, extract);
            amounts.put(aeItemKey, previous + amount);
            if (!unlimited) remain -= extract;
        }
        if (outputList.isEmpty()) return null;
        var output = GTRecipeBuilder.ofRaw().duration(tickDuration).build();
        output.outputs.put(ItemRecipeCapability.CAP, outputList);
        // Build and validate the entire pending recipe before consuming any queued craft.
        toExtract.forEach((key, amount) -> {
            long rest = queue.getLong(key) - amount;
            if (rest == 0) queue.removeLong(key);
            else queue.put(key, rest);
        });
        markAsChanged();
        return output;
    }
}
