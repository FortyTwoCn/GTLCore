package org.gtlcore.gtlcore.integration.ae2.chamber;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;

import io.netty.buffer.Unpooled;

import java.util.function.Consumer;
import java.util.function.Function;

/** Registry-aware bounded transport, shared by the server sender and client receiver. */
public final class ChamberSnapshotCodec {

    public static final int MAX_CHUNK_BYTES = 128 * 1024;
    public static final int MAX_SNAPSHOT_BYTES = 16 * 1024 * 1024;

    private ChamberSnapshotCodec() {}

    public record Chunk(int totalBytes, int offset, byte[] data) {}

    public static void encode(RegistryAccess registries, Consumer<RegistryFriendlyByteBuf> encoder,
                              Consumer<Chunk> sender) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(4096, MAX_SNAPSHOT_BYTES), registries);
        try {
            // Finish encoding before sending anything: a failed encoder must not leave a partial transfer.
            encoder.accept(buffer);
            int total = buffer.readableBytes();
            if (total == 0) throw new IllegalArgumentException("Empty chamber snapshot");
            for (int offset = 0; offset < total;) {
                byte[] data = new byte[Math.min(MAX_CHUNK_BYTES, total - offset)];
                buffer.readBytes(data);
                sender.accept(new Chunk(total, offset, data));
                offset += data.length;
            }
        } finally {
            buffer.release();
        }
    }

    public static final class Receiver {

        private byte[] pending;
        private long revision;
        private int received;

        public <T> void accept(RegistryAccess registries, long incomingRevision, int total, int offset,
                               byte[] chunk, Function<RegistryFriendlyByteBuf, T> decoder, Consumer<T> publisher) {
            if (total <= 0 || total > MAX_SNAPSHOT_BYTES || offset < 0 || chunk.length == 0 ||
                    chunk.length > MAX_CHUNK_BYTES || offset > total - chunk.length) {
                clear();
                return;
            }
            if (offset == 0) {
                pending = new byte[total];
                revision = incomingRevision;
                received = 0;
            }
            if (pending == null || revision != incomingRevision || pending.length != total || received != offset) {
                clear();
                return;
            }
            System.arraycopy(chunk, 0, pending, offset, chunk.length);
            received += chunk.length;
            if (received == total) {
                var buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(pending), registries);
                clear();
                try {
                    T decoded = decoder.apply(buffer);
                    if (buffer.isReadable()) throw new IllegalArgumentException("Trailing chamber snapshot data");
                    publisher.accept(decoded);
                } finally {
                    buffer.release();
                }
            }
        }

        private void clear() {
            pending = null;
            received = 0;
        }
    }
}
