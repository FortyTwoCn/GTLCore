package org.gtlcore.gtlcore.client.preview;

import org.gtlcore.gtlcore.utils.datastructure.LatestTask;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.concurrent.ConcurrentHashMap;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** An exclusive lease lasts until upload/release, so native buffers cannot grow with each batch. */
final class PreviewBuffers {

    private static final int LIMIT = 16;
    private static final AtomicInteger ALLOCATED = new AtomicInteger();
    private static final ArrayBlockingQueue<ByteBufferBuilder> FREE = new ArrayBlockingQueue<>(LIMIT);

    private static final ConcurrentHashMap<BufferBuilder, ByteBufferBuilder> OWNED = new ConcurrentHashMap<>();

    private PreviewBuffers() {}

    private static BufferBuilder lease(ByteBufferBuilder storage) {
        var builder = new BufferBuilder(storage, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        OWNED.put(builder, storage); return builder;
    }

    static BufferBuilder acquire(LatestTask task) throws InterruptedException {
        while (!task.cancelled()) {
            var builder = FREE.poll();
            if (builder != null) return lease(builder);
            int count = ALLOCATED.get();
            if (count < LIMIT && ALLOCATED.compareAndSet(count, count + 1)) {
                try {
                    return lease(new ByteBufferBuilder(128 * 1024));
                } catch (Throwable error) {
                    ALLOCATED.decrementAndGet();
                    throw error;
                }
            }
            builder = FREE.poll(25, TimeUnit.MILLISECONDS);
            if (builder != null) return lease(builder);
        }
        return null;
    }

    static void release(BufferBuilder builder) {
        var storage = OWNED.remove(builder);
        if (storage == null) throw new IllegalStateException("Preview buffer released twice");
        storage.clear();
        if (!FREE.offer(storage)) throw new IllegalStateException("Preview buffer pool ownership exceeded its bound");
    }
}
