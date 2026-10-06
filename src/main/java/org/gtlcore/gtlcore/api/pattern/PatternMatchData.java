package org.gtlcore.gtlcore.api.pattern;

import com.gregtechceu.gtceu.api.multiblock.PredicateContext;

import net.minecraft.core.BlockPos;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

/** Per-check GTL data attached to the native predicate context, including repeat-slice rollback. */
public final class PatternMatchData {
    private com.gregtechceu.gtceu.api.block.ICoilType coil;
    private final Map<String, Integer> tiers = new HashMap<>();
    private final Map<String, LongOpenHashSet> countedBlocks = new HashMap<>();
    private final ArrayList<Runnable> undo = new ArrayList<>();
    private final ArrayDeque<Integer> slices = new ArrayDeque<>();

    public static PatternMatchData of(PredicateContext context) {
        return ((Access) context).gtlcore$getPatternMatchData();
    }

    public boolean matchTier(String name, int tier) {
        Integer current = tiers.get(name);
        if (current != null) return current == tier;
        tiers.put(name, tier);
        undo.add(() -> tiers.remove(name));
        return true;
    }

    public OptionalInt tier(String name) {
        Integer tier = tiers.get(name);
        return tier == null ? OptionalInt.empty() : OptionalInt.of(tier);
    }

    /** Uniform coils participate in the same repeat-slice rollback as casing tiers. */
    public boolean matchCoil(com.gregtechceu.gtceu.api.block.ICoilType type) {
        if (coil != null) return coil == type;
        coil = java.util.Objects.requireNonNull(type);
        undo.add(() -> coil = null);
        return true;
    }

    public com.gregtechceu.gtceu.api.block.ICoilType coil() { return coil; }

    public void countBlock(String name, BlockPos pos) {
        LongOpenHashSet positions = countedBlocks.computeIfAbsent(name, key -> new LongOpenHashSet());
        long packed = pos.asLong();
        if (positions.add(packed)) undo.add(() -> positions.remove(packed));
    }

    public int blockCount(String name) {
        LongOpenHashSet positions = countedBlocks.get(name);
        return positions == null ? 0 : positions.size();
    }

    /** Accepted positions only; callers cannot mutate the predicate's rollback state. */
    public long[] blockPositions(String name) {
        LongOpenHashSet positions = countedBlocks.get(name);
        return positions == null ? new long[0] : positions.toLongArray();
    }

    public void pushSlice() {
        slices.push(undo.size());
    }

    public void popSlice() {
        if (slices.isEmpty()) return;
        int checkpoint = slices.pop();
        for (int i = undo.size() - 1; i >= checkpoint; i--) undo.remove(i).run();
    }

    public void reset() {
        coil = null;
        tiers.clear();
        countedBlocks.clear();
        undo.clear();
        slices.clear();
    }

    public interface Access {
        PatternMatchData gtlcore$getPatternMatchData();
    }
}
