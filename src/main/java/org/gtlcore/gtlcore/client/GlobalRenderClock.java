package org.gtlcore.gtlcore.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Global render clock - Provides smooth, pause-aware tick counting
 *
 * <p>
 * Replaces the traditional {@code machine.offsetTimer + partialTicks} approach:
 * <ul>
 * <li>Uses system time for completely smooth tick counting (calculated independently per frame)</li>
 * <li>Automatically detects and handles game pause (tick doesn't increase when paused)</li>
 * <li>Zero-overhead sharing (all renderers share the same clock)</li>
 * </ul>
 *
 * <p>
 * <b>Thread Safety:</b> This class is designed to be called <b>only from the Minecraft render thread</b>.
 * No synchronization is needed because Minecraft's rendering system is single-threaded.
 * Do not call these methods from other threads (e.g., server tick thread, async tasks).
 */
@OnlyIn(Dist.CLIENT)
public class GlobalRenderClock {

    private static long startTimeNanos = System.nanoTime();
    private static long totalPausedNanos = 0L;
    private static long pauseStartNanos = 0L;
    private static boolean wasPaused = false;

    private GlobalRenderClock() {
        throw new AssertionError();
    }

    private static long getElapsedNanos() {
        Minecraft minecraft = Minecraft.getInstance();
        boolean isPaused = minecraft.isPaused();

        if (isPaused && !wasPaused) {
            pauseStartNanos = System.nanoTime();
            wasPaused = true;
        } else if (!isPaused && wasPaused) {
            totalPausedNanos += (System.nanoTime() - pauseStartNanos);
            wasPaused = false;
        }

        long currentNanos = isPaused ? pauseStartNanos : System.nanoTime();
        return currentNanos - startTimeNanos - totalPausedNanos;
    }

    /**
     * Get the current smooth tick value
     *
     * @return Smooth tick count as a float (1 tick = 50ms)
     */
    public static float getSmoothTick() {
        // 1 tick = 50ms = 50,000,000 ns
        return (float) (getElapsedNanos() / 50_000_000.0);
    }

    /**
     * Get elapsed time in milliseconds
     *
     * @return Elapsed milliseconds since clock start (excluding paused time)
     */
    public static long getElapsedMillis() {
        // 1ms = 1,000,000 ns
        return getElapsedNanos() / 1_000_000L;
    }

    /**
     * Reset the clock to zero
     */
    public static void reset() {
        startTimeNanos = System.nanoTime();
        totalPausedNanos = 0L;
        pauseStartNanos = 0L;
        wasPaused = false;
    }
}
