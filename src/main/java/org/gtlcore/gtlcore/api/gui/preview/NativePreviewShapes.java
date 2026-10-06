package org.gtlcore.gtlcore.api.gui.preview;

import org.gtlcore.gtlcore.utils.datastructure.WeightedCache;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.multiblock.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.multiblock.pattern.ExpandablePattern;
import net.minecraft.core.Direction;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.*;
import java.util.function.Supplier;

/** Native pattern variants are generated on demand, rather than expanding every repeat combination. */
public final class NativePreviewShapes {
    private static final Map<MultiblockMachineDefinition, Supplier<List<NativePreviewGeometry>>> VISUALS = new WeakHashMap<>();
    private static final Map<MultiblockMachineDefinition, List<NativePreviewGeometry>> PAGES = new WeakHashMap<>();
    private static final WeightedCache<PageKey, NativePreviewGeometry> GEOMETRY = new WeightedCache<>(64L << 20,
            geometry -> 128L + geometry.states().size() * 112L, ignored -> {});
    private NativePreviewShapes() {}

    public static synchronized void register(MultiblockMachineDefinition definition, Supplier<List<NativePreviewGeometry>> visuals) {
        VISUALS.put(Objects.requireNonNull(definition), Objects.requireNonNull(visuals)); invalidate(definition);
    }

    public static synchronized List<NativePreviewGeometry> get(MultiblockMachineDefinition definition) {
        return PAGES.computeIfAbsent(definition, NativePreviewShapes::createPages);
    }

    public static synchronized boolean hasVisuals(MultiblockMachineDefinition definition) {
        return VISUALS.containsKey(definition);
    }

    public static synchronized void invalidate(MultiblockMachineDefinition definition) {
        PAGES.remove(definition); GEOMETRY.clear();
    }

    public static synchronized void clear() { PAGES.clear(); GEOMETRY.clear(); }

    private static List<NativePreviewGeometry> createPages(MultiblockMachineDefinition definition) {
        var visual = VISUALS.get(definition);
        if (visual != null) return List.copyOf(visual.get());
        var pattern = definition.getStructurePatterns().get("main").get();
        var bounds = new ArrayList<int[]>();
        if (pattern instanceof BlockPattern blockPattern) {
            for (var slice : blockPattern.getSlices()) bounds.add(new int[] { slice.getMinRepeats(), slice.getMaxRepeats() });
        } else if (pattern instanceof ExpandablePattern expandable) {
            for (var bound : expandable.getBoundsConstraints().apply()) bounds.add(new int[] { bound.leftInt(), bound.rightInt() });
        } else throw new IllegalArgumentException("Unsupported native preview pattern: " + definition.getId());
        long combinations = 1;
        for (var bound : bounds) {
            combinations = Math.multiplyExact(combinations, (long) bound[1] - bound[0] + 1);
            if (combinations > Integer.MAX_VALUE) throw new IllegalArgumentException("Too many native preview pages: " + definition.getId());
        }
        final int count = (int) combinations;
        var front = definition.getRotationState().defaultDirection;
        var up = definition.getRotationState() == com.gregtechceu.gtceu.api.data.RotationState.Y_AXIS ? Direction.NORTH : Direction.UP;
        return new AbstractList<>() {
            @Override public int size() { return count; }
            @Override public NativePreviewGeometry get(int index) {
                Objects.checkIndex(index, count); var key = new PageKey(this, index);
                synchronized (NativePreviewShapes.class) {
                    var cached = GEOMETRY.get(key); if (cached != null) return cached;
                    int page = index; var repeats = new Int2IntOpenHashMap(); var dimensions = new ArrayList<Integer>();
                    for (int i = 0; i < bounds.size(); i++) {
                        var bound = bounds.get(i); int range = bound[1] - bound[0] + 1;
                        int value = bound[0] + page % range; page /= range;
                        repeats.put(i, value); dimensions.add(value);
                    }
                    var result = NativePreviewGeometry.fromPattern(definition, pattern, repeats,
                            pattern instanceof ExpandablePattern ? dimensions : List.of(), front, up, false);
                    GEOMETRY.put(key, result); return result;
                }
            }
        };
    }

    // List.equals/hashCode would traverse every page, which would defeat lazy construction.
    private record PageKey(List<NativePreviewGeometry> pages, int index) {
        @Override public boolean equals(Object other) { return other instanceof PageKey key && pages == key.pages && index == key.index; }
        @Override public int hashCode() { return 31 * System.identityHashCode(pages) + index; }
    }
}
