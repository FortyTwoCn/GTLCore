package org.gtlcore.gtlcore.integration.ae2.graph;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.integration.ae2.graph.core.PlanningBudget;

import net.minecraft.Util;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Only world snapshot work uses a tick budget. Ready solver work never waits for a tick. */
@EventBusSubscriber(modid = GTLCore.MOD_ID)
public final class GraphSnapshots {

    private static final Deque<Request> pending = new ArrayDeque<>();
    private static long spentNanos;
    private static boolean draining;

    private GraphSnapshots() {}

    public static void enqueue(GtlPatternCatalog.Capture capture, PlanningBudget budget,
                               CompletableFuture<GtlPatternCatalog.Snapshot> future, Consumer<Timing> timing) {
        pending.addLast(new Request(capture, budget, future, timing));
        drain();
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        drain();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void start(ServerTickEvent.Pre event) {
        spentNanos = 0;
    }

    /** Share the tick allowance with immediate captures; do not impose a second tick wait on tiny orders. */
    private static void drain() {
        if (draining || pending.isEmpty()) return;
        if (spentNanos >= 2_000_000L) return;
        long began = System.nanoTime(), deadline = began + 2_000_000L - spentNanos;
        try {
            drainUntil(deadline, false);
        } finally {
            spentNanos += System.nanoTime() - began;
        }
    }

    /**
     * MinecraftServer calls this only after polling its regular/chunk tasks in
     * waitUntilNextTick. This is still the server thread, not async world access.
     * Leave a millisecond before the real next-tick deadline; never use vanilla's
     * extendable delayed-task deadline. An overloaded server keeps the 2 ms cap.
     */
    public static boolean idle(long nextTickNanos) {
        if (draining || pending.isEmpty()) return false;
        long remainingNanos = nextTickNanos - Util.getNanos();
        if (remainingNanos <= 1_000_000L) return false;
        return drainUntil(System.nanoTime() + Math.min(1_000_000L, remainingNanos - 1_000_000L), true);
    }

    private static boolean drainUntil(long deadline, boolean idle) {
        boolean worked = false;
        draining = true;
        try {
            while (!pending.isEmpty() && System.nanoTime() - deadline < 0) {
                Request request = pending.removeFirst();
                if (request.future.isDone()) continue;
                worked = true;
                long start = System.nanoTime();
                try (var timing = request.budget.work(PlanningBudget.Phase.SNAPSHOT)) {
                    boolean complete = false;
                    // Amortize diagnostic clocks and queue operations across a
                    // bounded batch. Keep the same global deadline and check
                    // cancellation inside every capture step.
                    for (int step = 0; step < 128 && System.nanoTime() - deadline < 0; step++) {
                        if (request.capture.step(deadline)) {
                            complete = true;
                            break;
                        }
                    }
                    long elapsed = System.nanoTime() - start;
                    request.nanos += elapsed;
                    request.maxSliceNanos = Math.max(request.maxSliceNanos, elapsed);
                    if (idle) {
                        request.idleNanos += elapsed;
                        request.idleSlices++;
                    } else request.tickSlices++;
                    if (complete) {
                        request.timing.accept(new Timing(request.nanos, request.idleNanos, request.tickSlices,
                                request.idleSlices, request.maxSliceNanos));
                        request.future.complete(request.capture.result());
                    } else pending.addLast(request);
                } catch (Throwable error) {
                    request.future.completeExceptionally(error);
                }
            }
        } finally {
            draining = false;
        }
        return worked;
    }

    public record Timing(long activeNanos, long idleNanos, int tickSlices, int idleSlices, long maxSliceNanos) {}

    @SubscribeEvent
    public static void reload(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) GtlPatternCatalog.dataReloaded();
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        for (Request request : pending) request.future.cancel(false);
        pending.clear();
        spentNanos = 0;
    }

    private static final class Request {

        final GtlPatternCatalog.Capture capture;
        final PlanningBudget budget;
        final CompletableFuture<GtlPatternCatalog.Snapshot> future;
        final Consumer<Timing> timing;
        long nanos, idleNanos, maxSliceNanos;
        int tickSlices, idleSlices;

        Request(GtlPatternCatalog.Capture capture, PlanningBudget budget, CompletableFuture<GtlPatternCatalog.Snapshot> future, Consumer<Timing> timing) {
            this.capture = capture;
            this.budget = budget;
            this.future = future;
            this.timing = timing;
        }
    }
}
