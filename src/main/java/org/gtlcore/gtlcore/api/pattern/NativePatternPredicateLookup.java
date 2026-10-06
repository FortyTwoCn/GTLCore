package org.gtlcore.gtlcore.api.pattern;

import com.gregtechceu.gtceu.api.multiblock.MultiPredicate;
import com.gregtechceu.gtceu.api.multiblock.Predicates;
import com.gregtechceu.gtceu.api.multiblock.pattern.BlockPattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import java.util.function.Function;

/** Queries the native helper's normalized coordinates without rotating the entire pattern per block. */
public final class NativePatternPredicateLookup {
    private NativePatternPredicateLookup() {}

    public static Function<BlockPos, MultiPredicate> blockPattern(BlockPattern pattern, Int2IntMap repeats,
                                                                 Direction front, Direction up, boolean flipped) {
        if (front.getAxis() == up.getAxis()) throw new IllegalArgumentException("Pattern axes coincide");
        var slices = pattern.getSlices();
        int totalSlices = 0;
        for (int i = 0; i < slices.length; i++) {
            int count = repeats.getOrDefault(i, slices[i].getMinRepeats());
            if (count < slices[i].getMinRepeats() || count > slices[i].getMaxRepeats())
                throw new IllegalArgumentException("Repetition outside native slice bounds: " + i);
            totalSlices = Math.addExact(totalSlices, count);
        }
        var repeatedSlices = new char[totalSlices][][];
        int index = 0;
        for (int i = 0; i < slices.length; i++) {
            int count = repeats.getOrDefault(i, slices[i].getMinRepeats());
            for (int repeat = 0; repeat < count; repeat++) repeatedSlices[index++] = slices[i].getPattern();
        }
        int[] dimensions = { totalSlices, pattern.getDimensions()[1], pattern.getDimensions()[2] };
        Direction[] axes = new Direction[3];
        for (int i = 0; i < axes.length; i++)
            axes[i] = pattern.getDirections()[i].getRelativeFacing(front, up, flipped);
        var air = Predicates.air();
        return pos -> {
            int slice = coordinate(pos, axes[0], dimensions[0]);
            int row = coordinate(pos, axes[1], dimensions[1]);
            int column = coordinate(pos, axes[2], dimensions[2]);
            if (slice < 0 || slice >= dimensions[0] || row < 0 || row >= dimensions[1]
                    || column < 0 || column >= dimensions[2]) return air;
            return pattern.getPredicates().get(repeatedSlices[slice][row][column]);
        };
    }

    private static int coordinate(BlockPos pos, Direction direction, int dimension) {
        int value = direction.getAxis().choose(pos.getX(), pos.getY(), pos.getZ());
        return direction.getAxisDirection() == Direction.AxisDirection.POSITIVE ? value : dimension - 1 - value;
    }
}
