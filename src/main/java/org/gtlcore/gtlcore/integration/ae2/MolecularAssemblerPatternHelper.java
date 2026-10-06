package org.gtlcore.gtlcore.integration.ae2;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.core.definitions.AEItems;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/** Native AE patterns accepted by the Core molecular assembler, including shared reusable tools. */
public final class MolecularAssemblerPatternHelper {
    private MolecularAssemblerPatternHelper() {}

    public static boolean molecularFilter(ItemStack stack, @Nullable Level level) {
        if (AEItems.CRAFTING_PATTERN.is(stack)) {
            if (!(PatternDetailsHelper.decodePattern(stack, level) instanceof AECraftingPattern pattern)) return false;
            for (var input : pattern.getInputs()) {
                var choices = input.getPossibleInputs();
                if (choices.length == 0) return false;
                var remaining = input.getRemainingKey(choices[0].what());
                if (remaining != null && (!(remaining instanceof AEItemKey key) || key.toStack().isDamageableItem())) return false;
            }
            return true;
        }
        return AEItems.SMITHING_TABLE_PATTERN.is(stack) || AEItems.STONECUTTING_PATTERN.is(stack);
    }

    public static Pair<@Nullable IPatternDetails, @Nullable ObjectSet<Item>> createProcessingFromCraftPattern(
            IMolecularAssemblerSupportedPattern pattern, @Nullable Level level) {
        var normalInputs = new ArrayList<GenericStack>();
        ObjectSet<Item> reusableTools = new ObjectOpenHashSet<>();
        for (var input : pattern.getInputs()) {
            var choices = input.getPossibleInputs();
            if (choices.length == 0 || input.getMultiplier() <= 0) return Pair.of(null, null);
            var stack = choices[0];
            var remaining = input.getRemainingKey(stack.what());
            if (remaining != null) {
                if (!(remaining instanceof AEItemKey key) || key.toStack().isDamageableItem()) return Pair.of(null, null);
                reusableTools.add(key.getItem());
            } else {
                long amount;
                long capacity;
                try {
                    amount = Math.multiplyExact(stack.amount(), input.getMultiplier());
                    capacity = Math.multiplyExact((long) Integer.MAX_VALUE, stack.what().getAmountPerUnit());
                }
                catch (ArithmeticException overflow) { return Pair.of(null, null); }
                if (amount <= 0 || amount > capacity) return Pair.of(null, null);
                normalInputs.add(new GenericStack(stack.what(), amount));
            }
        }
        if (normalInputs.isEmpty() || pattern.getOutputs().isEmpty()) return Pair.of(null, null);
        var encoded = PatternDetailsHelper.encodeProcessingPattern(normalInputs, pattern.getOutputs());
        return Pair.of(PatternDetailsHelper.decodePattern(AEItemKey.of(encoded), level), reusableTools);
    }
}
