package org.gtlcore.gtlcore.api.gui.preview;

import com.gregtechceu.gtceu.api.multiblock.predicates.BasePredicate;
import com.gregtechceu.gtceu.api.multiblock.util.BlockInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.*;

/** An insertion-ordered preview with the same candidate counts as the native helper's scans. */
public final class NativePreviewBlockMap extends AbstractMap<BlockPos, BlockInfo> {
    private final Map<BlockPos, BlockInfo> blocks = new LinkedHashMap<>();
    private final Map<BlockInfo, Counts> counts = new HashMap<>();
    private final Map<BasePredicate, List<BlockInfo>> candidates = new IdentityHashMap<>();

    @Override public Set<Entry<BlockPos, BlockInfo>> entrySet() {
        // Mutations must update the indexes. Native population uses put, not Entry.setValue.
        return Collections.unmodifiableMap(blocks).entrySet();
    }
    @Override public int size() { return blocks.size(); }
    @Override public boolean containsKey(Object key) { return blocks.containsKey(key); }
    @Override public BlockInfo get(Object key) { return blocks.get(key); }
    @Override public BlockInfo put(BlockPos pos, BlockInfo info) {
        var key = pos.immutable();
        var previous = blocks.put(key, Objects.requireNonNull(info));
        if (!Objects.equals(previous, info)) {
            if (previous != null) adjust(key, previous, -1);
            adjust(key, info, 1);
        }
        return previous;
    }
    @Override public BlockInfo remove(Object key) {
        var previous = blocks.remove(key);
        if (previous != null) adjust((BlockPos) key, previous, -1);
        return previous;
    }
    @Override public void clear() { blocks.clear(); counts.clear(); candidates.clear(); }

    public int countGlobal(BasePredicate predicate) {
        int result = 0;
        for (var info : candidates(predicate)) {
            var count = counts.get(info);
            if (count != null) result += count.global;
        }
        return result;
    }
    public int countLayer(BasePredicate predicate, Direction direction, int coordinate) {
        int result = 0;
        for (var info : candidates(predicate)) {
            var count = counts.get(info);
            if (count != null) result += count.axes[direction.getAxis().ordinal()].get(coordinate);
        }
        return result;
    }
    private List<BlockInfo> candidates(BasePredicate predicate) {
        // List.contains counts a block only once, even if the candidate list contains duplicates.
        return candidates.computeIfAbsent(predicate, key -> key.getCandidates().stream().distinct().toList());
    }
    private void adjust(BlockPos pos, BlockInfo info, int delta) {
        var count = counts.computeIfAbsent(info, ignored -> new Counts());
        count.global += delta;
        for (var axis : Direction.Axis.values()) {
            var map = count.axes[axis.ordinal()];
            int coordinate = axis.choose(pos.getX(), pos.getY(), pos.getZ());
            int updated = map.addTo(coordinate, delta) + delta;
            if (updated == 0) map.remove(coordinate);
        }
        if (count.global == 0) counts.remove(info);
    }
    private static final class Counts {
        int global;
        final Int2IntOpenHashMap[] axes = { new Int2IntOpenHashMap(), new Int2IntOpenHashMap(), new Int2IntOpenHashMap() };
    }
}
