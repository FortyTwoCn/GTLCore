package org.gtlcore.gtlcore.integration.ftb;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;

/** The native map remains anchored to its opening chunk, including negative world coordinates. */
public final class NativeProspectorCoordinates {
    private static final String PREFIX = "prospect_point";
    private NativeProspectorCoordinates() {}

    public static Optional<BlockPos> point(ChunkPos anchor, int radius, double mouseX, double mouseZ) {
        long width = (2L * radius - 1) * 16;
        if (radius < 1 || !Double.isFinite(mouseX) || !Double.isFinite(mouseZ) || mouseX < 0 || mouseZ < 0 || mouseX >= width || mouseZ >= width)
            return Optional.empty();
        long x = (anchor.x - (long) radius + 1) * 16 + (long) Math.floor(mouseX);
        long z = (anchor.z - (long) radius + 1) * 16 + (long) Math.floor(mouseZ);
        if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE || z < Integer.MIN_VALUE || z > Integer.MAX_VALUE) return Optional.empty();
        return Optional.of(new BlockPos((int) x, 0, (int) z));
    }

    private static long index(String name) {
        if (name == null || !name.startsWith(PREFIX)) return 0;
        String suffix = name.substring(PREFIX.length());
        if (suffix.isEmpty() || suffix.length() > 19 || suffix.chars().anyMatch(c -> c < '0' || c > '9')) return 0;
        try { return Math.max(0, Long.parseLong(suffix)); }
        catch (NumberFormatException ignored) { return 0; }
    }

    public static boolean isGeneratedWaypoint(String name) { return index(name) > 0; }

    public static String nextWaypointName(Collection<String> names) {
        long greatest = 0;
        for (var name : names) greatest = Math.max(greatest, index(name));
        if (greatest < Long.MAX_VALUE) return PREFIX + (greatest + 1);
        var used = new HashSet<>(names);
        for (long i = 1; ; i++) if (!used.contains(PREFIX + i)) return PREFIX + i;
    }
}
