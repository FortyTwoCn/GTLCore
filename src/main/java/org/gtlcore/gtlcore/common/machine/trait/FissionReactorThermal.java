package org.gtlcore.gtlcore.common.machine.trait;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.math.BigInteger;

/** Original reactor formulas, with exact intermediate quantities and bounded native signals. */
public final class FissionReactorThermal {
    public static final int AMBIENT = 298, MAX_HEAT = 1500;

    public static int adjacentPairs(long[] positions) {
        var accepted = new LongOpenHashSet(positions);
        int pairs = 0;
        for (long packed : accepted) {
            var pos = BlockPos.of(packed);
            for (var direction : new Direction[] { Direction.EAST, Direction.UP, Direction.SOUTH })
                if (accepted.contains(pos.relative(direction).asLong())) pairs++;
        }
        return pairs;
    }

    public static long requiredCooling(int recipeHeat, int parallel, int heat) {
        if (recipeHeat <= 0 || parallel <= 0 || heat <= 0) return 0;
        return BigInteger.valueOf(recipeHeat).multiply(BigInteger.valueOf(parallel))
                .multiply(BigInteger.valueOf(heat)).divide(BigInteger.valueOf(MAX_HEAT))
                .min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
    }

    public static long coolerCapacity(int cooler, int adjacentPairs) {
        return Math.max(0, (cooler - adjacentPairs / 3L) * 8L);
    }

    public static int heated(int heat, int recipeHeat, int adjacency) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(AMBIENT,
                (long) heat + (long) Math.max(0, recipeHeat) * Math.max(1, adjacency)));
    }

    public static long waterAmount(long cooling) { return Math.multiplyExact(cooling, 800); }
    public static long sodiumPotassiumAmount(long cooling) { return Math.multiplyExact(cooling, 20); }
    public static long steamAmount(long cooling, int heat) {
        long water = waterAmount(cooling);
        long maximum = Math.multiplyExact(water, 160);
        if (heat >= 373) return maximum;
        return java.math.BigDecimal.valueOf(maximum).divide(
                java.math.BigDecimal.valueOf(Math.pow(1.4, 373 - heat)), 0, java.math.RoundingMode.DOWN).longValueExact();
    }

    public static int signal(int heat) { return Math.max(0, Math.min(15, 22500 / Math.max(1, heat))); }
    private FissionReactorThermal() {}
}
