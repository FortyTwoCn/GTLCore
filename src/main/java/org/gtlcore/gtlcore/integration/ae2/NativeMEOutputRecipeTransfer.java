package org.gtlcore.gtlcore.integration.ae2;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.gregtechceu.gtceu.integration.ae2.utils.KeyStorage;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Function;

/** Native recipe outputs reserve space across equal component keys; only accepted quantities leave the recipe. */
public final class NativeMEOutputRecipeTransfer {
    private NativeMEOutputRecipeTransfer() {}
    public static <T> List<T> accept(KeyStorage buffer, List<T> left, boolean simulate,
                                   Function<T, GenericStack> resolve, Function<GenericStack, T> remainder) {
        var reserved = new Object2LongOpenHashMap<AEKey>(buffer.storage);
        var rejected = new ArrayList<T>();
        boolean changed = false;
        for (var ingredient : left) {
            var output = resolve.apply(ingredient);
            if (output == null) continue;
            if (output.amount() <= 0) throw new IllegalArgumentException("Invalid resolved ME output amount");
            long existing = reserved.getLong(output.what());
            if (existing < 0) throw new IllegalStateException("Negative owned ME output");
            long accepted = Math.min(output.amount(), Long.MAX_VALUE - existing);
            reserved.put(output.what(), Math.addExact(existing, accepted));
            if (accepted != output.amount()) rejected.add(remainder.apply(new GenericStack(output.what(), output.amount() - accepted)));
            changed |= accepted > 0;
        }
        left.clear(); left.addAll(rejected);
        if (!simulate && changed) { buffer.storage.clear(); buffer.storage.putAll(reserved); buffer.onChanged(); }
        return left;
    }
}
