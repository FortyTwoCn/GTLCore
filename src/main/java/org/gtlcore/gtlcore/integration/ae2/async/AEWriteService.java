package org.gtlcore.gtlcore.integration.ae2.async;

import org.gtlcore.gtlcore.integration.ae2.AEOutputIngredients;

import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;



import appeng.api.stacks.*;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Accepted output belongs to the machine immediately; only draining its ledger is asynchronous. */
public final class AEWriteService implements AutoCloseable {

    public static final AEWriteService INSTANCE = new AEWriteService();
    public static int THREAD_PRIORITY = Thread.NORM_PRIORITY - 1;
    public static int TIME_OUT = 10;
    /** Kept for addon binary compatibility. Saving no longer depends on a worker timeout. */
    public static long FLUSH_TIMEOUT = 500;

    private volatile ThreadPoolExecutor executor;

    private ThreadPoolExecutor executor() {
        ThreadPoolExecutor current = executor;
        if (current != null && !current.isShutdown()) return current;
        synchronized (this) {
            current = executor;
            if (current == null || current.isShutdown()) {
                current = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
                        new ArrayBlockingQueue<>(4096), this::createWorker, (task, pool) -> task.run());
                executor = current;
            }
            return current;
        }
    }

    private Thread createWorker(Runnable r) {
        Thread t = new Thread(r, "AE-Writer");
        t.setDaemon(true);
        t.setPriority(Math.max(Thread.MIN_PRIORITY, Math.min(Thread.MAX_PRIORITY, THREAD_PRIORITY)));
        return t;
    }

    private void execute(Runnable task) {
        try {
            executor().execute(task);
        } catch (RejectedExecutionException e) {
            task.run();
        }
    }

    public void submitIngredientLeft(WeakReference<AEAccumulator> accRef, List<SizedIngredient> left) {
        if (left == null || left.isEmpty()) return;
        var acc = accRef.get();
        if (acc == null) throw new IllegalStateException("Output owner disappeared before accepting items");
        if (left.size() == 1) {
            GenericStack output = itemOutput(left.get(0));
            if (output != null) acc.add(output.what(), output.amount());
            return;
        }
        // Resolve quantities, random providers and NBT before acknowledging the
        // recipe. No worker may retain mutable ingredient objects or sample again.
        Object2LongOpenHashMap<AEKey> accepted = new Object2LongOpenHashMap<>();
        for (SizedIngredient ingredient : left) {
            GenericStack output = itemOutput(ingredient);
            if (output != null) accepted.put(output.what(), Math.addExact(accepted.getLong(output.what()), output.amount()));
        }
        acc.addAll(accepted);
    }

    private static GenericStack itemOutput(SizedIngredient ingredient) {
        return AEOutputIngredients.item(ingredient, false);
    }

    public void submitFluidIngredientLeft(WeakReference<AEAccumulator> accRef, List<SizedFluidIngredient> left) {
        if (left == null || left.isEmpty()) return;
        var acc = accRef.get();
        if (acc == null) throw new IllegalStateException("Output owner disappeared before accepting fluids");
        if (left.size() == 1) {
            GenericStack output = fluidOutput(left.get(0));
            if (output != null) acc.add(output.what(), output.amount());
            return;
        }
        Object2LongOpenHashMap<AEKey> accepted = new Object2LongOpenHashMap<>();
        for (SizedFluidIngredient ingredient : left) {
            GenericStack output = fluidOutput(ingredient);
            if (output != null) accepted.put(output.what(), Math.addExact(accepted.getLong(output.what()), output.amount()));
        }
        acc.addAll(accepted);
    }

    private static GenericStack fluidOutput(SizedFluidIngredient ingredient) {
        return AEOutputIngredients.fluid(ingredient, false);
    }

    public void prepareDrainedData(WeakReference<AEAccumulator> accRef,
                                   Queue<Object2LongOpenHashMap<AEKey>> targetQueue,
                                   AtomicBoolean drainRequested) {
        execute(() -> {
            try {
                drainNow(accRef, targetQueue);
            } finally {
                drainRequested.set(false);
            }
        });
    }

    private void drainNow(WeakReference<AEAccumulator> accRef, Queue<Object2LongOpenHashMap<AEKey>> targetQueue) {
        var acc = accRef.get();
        if (acc == null) return;
        // Transfer and publication share one monitor. A save cannot observe the
        // gap where the ledger is empty but a worker still owns unpublished data.
        synchronized (acc) {
            if (acc.isEmpty()) return;
            Object2LongOpenHashMap<AEKey> drained = new Object2LongOpenHashMap<>();
            acc.drainTo(drained);
            if (!drained.isEmpty()) targetQueue.add(drained);
        }
    }

    /** Materialize this machine's accepted output without waiting for unrelated writers. */
    public boolean flushBlocking(WeakReference<AEAccumulator> accRef,
                                 Queue<Object2LongOpenHashMap<AEKey>> targetQueue,
                                 AtomicBoolean drainRequested, long timeoutMs) {
        drainNow(accRef, targetQueue);
        // An already queued drain may still run. Leave its flag set until that
        // task finishes, so repeated saves do not enqueue duplicate work.
        return true;
    }

    public void shutDownGracefully() {
        ThreadPoolExecutor pool = executor;
        if (pool == null) return;
        pool.shutdown();
        try {
            if (!pool.awaitTermination(TIME_OUT, TimeUnit.SECONDS))
                pool.shutdownNow().forEach(Runnable::run);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pool.shutdownNow().forEach(Runnable::run);
        }
    }

    @Override
    public void close() {
        shutDownGracefully();
    }
}
