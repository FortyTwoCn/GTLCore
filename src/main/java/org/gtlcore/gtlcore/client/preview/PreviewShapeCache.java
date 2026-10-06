package org.gtlcore.gtlcore.client.preview;

import org.gtlcore.gtlcore.utils.datastructure.WeightedCache;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import org.gtlcore.gtlcore.api.gui.preview.NativePreviewGeometry;
import org.gtlcore.gtlcore.api.gui.preview.NativePreviewShapes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PreviewShapeCache {

    private static final WeightedCache<NativePreviewGeometry, SparsePreviewShape> SPARSE = new WeightedCache<>(64L << 20, SparsePreviewShape::estimatedBytes, ignored -> {});

    private PreviewShapeCache() {}

    public static List<NativePreviewGeometry> get(MultiblockMachineDefinition definition) {
        return NativePreviewShapes.get(definition);
    }

    public static void put(MultiblockMachineDefinition definition, List<NativePreviewGeometry> shapes) {
        NativePreviewShapes.register(definition, () -> List.copyOf(shapes));
    }

    public static boolean isLarge(NativePreviewGeometry shape) {
        return shape != null && shape.states().size() >= PreviewSettings.minPositions();
    }

    public static void invalidate(MultiblockMachineDefinition definition) {
        NativePreviewShapes.invalidate(definition);
        // A mesh may outlive its raw shape cache entry, including oversized/evicted shapes.
        SPARSE.clear();
        PreviewLifecycle.clear();
    }

    public static SparsePreviewShape sparse(NativePreviewGeometry shape, MultiblockMachineDefinition definition) {
        var cached = SPARSE.get(shape);
        if (cached != null) return cached;
        var result = new SparsePreviewShape(shape, definition);
        SPARSE.put(shape, result);
        return result;
    }

    public static void clear() {
        NativePreviewShapes.clear();
        SPARSE.clear();
    }


}
