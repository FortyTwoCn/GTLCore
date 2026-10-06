package org.gtlcore.gtlcore.integration.ae2.pattern;

import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Optional;

/** Exact arithmetic and native item metadata for ExtendedAE's pattern operations. */
public final class NativePatternModifierAmounts {
    private NativePatternModifierAmounts() {}
    public static boolean canScale(GenericStack[] stacks, int scale, boolean divide) {
        if (scale <= 0) return false;
        for (var stack : stacks) {
            if (stack == null) continue;
            if (stack.amount() <= 0) return false;
            if (divide) {
                if (stack.amount() % scale != 0) return false;
            } else {
                int unit = stack.what().getAmountPerUnit();
                if (unit <= 0 || stack.amount() > ((long) Integer.MAX_VALUE * unit) / scale) return false;
            }
        }
        return true;
    }
    public static long replacement(long amount, long withAmount, long targetAmount) {
        if (amount <= 0 || withAmount <= 0 || targetAmount <= 0) throw new IllegalArgumentException("Invalid pattern amount");
        return BigInteger.valueOf(amount).multiply(BigInteger.valueOf(withAmount)).divide(BigInteger.valueOf(targetAmount))
                .max(BigInteger.ONE).min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
    }
    public static GenericStack[] compact(GenericStack[] stacks) {
        var merged = new LinkedHashMap<AEKey, Long>();
        for (var stack : stacks) {
            if (stack == null) continue;
            if (stack.amount() <= 0) throw new IllegalArgumentException("Invalid pattern amount");
            long previous = merged.getOrDefault(stack.what(), 0L);
            if (stack.amount() > Long.MAX_VALUE - previous) throw new IllegalArgumentException("Pattern total exceeds long capacity");
            merged.put(stack.what(), previous + stack.amount());
        }
        return merged.entrySet().stream().map(entry -> new GenericStack(entry.getKey(), entry.getValue())).toArray(GenericStack[]::new);
    }
    public static void copyMetadata(ItemStack original, ItemStack encoded) {
        for (var entry : original.getComponentsPatch().entrySet()) {
            var type = entry.getKey();
            if (type == AEComponents.ENCODED_PROCESSING_PATTERN || type == AEComponents.ENCODED_CRAFTING_PATTERN
                    || type == AEComponents.ENCODED_STONECUTTING_PATTERN || type == AEComponents.ENCODED_SMITHING_TABLE_PATTERN) continue;
            copyComponent(encoded, type, entry.getValue());
        }
        encoded.setCount(original.getCount());
    }
    @SuppressWarnings("unchecked")
    private static <T> void copyComponent(ItemStack encoded, DataComponentType<T> type, Optional<?> value) {
        if (value.isPresent()) encoded.set(type, (T) value.get()); else encoded.remove(type);
    }
}
