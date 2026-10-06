package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.integration.ae2.chamber.ChamberSnapshotCodec;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NativeChamberSnapshotTest {

    private static final RegistryAccess REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

    private record Snapshot(ItemStack icon, Component name, AEKey item, AEKey fluid, long amount, byte[] padding) {}

    private static Snapshot snapshot() {
        var icon = new ItemStack(Items.IRON_INGOT, 3);
        icon.set(DataComponents.CUSTOM_NAME, Component.literal("Chamber icon with components"));
        var fluid = new FluidStack(Fluids.WATER, 1);
        fluid.set(DataComponents.CUSTOM_NAME, Component.literal("Chamber fluid with components"));
        byte[] padding = new byte[ChamberSnapshotCodec.MAX_CHUNK_BYTES * 2 + 37];
        for (int i = 0; i < padding.length; i++) padding[i] = (byte) (i * 31);
        return new Snapshot(icon, Component.translatable("chamber.snapshot", icon.getHoverName().copy().withColor(0xabcdef)).withColor(0x123456),
                AEItemKey.of(icon), AEFluidKey.of(fluid), Long.MAX_VALUE, padding);
    }

    private static List<ChamberSnapshotCodec.Chunk> encode(Snapshot snapshot) {
        var chunks = new ArrayList<ChamberSnapshotCodec.Chunk>();
        ChamberSnapshotCodec.encode(REGISTRIES, buffer -> {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, snapshot.icon());
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, ItemStack.EMPTY);
            ComponentSerialization.STREAM_CODEC.encode(buffer, snapshot.name());
            AEKey.writeOptionalKey(buffer, snapshot.item());
            AEKey.writeOptionalKey(buffer, snapshot.fluid());
            AEKey.writeOptionalKey(buffer, null);
            buffer.writeVarLong(snapshot.amount());
            buffer.writeByteArray(snapshot.padding());
        }, chunks::add);
        return chunks;
    }

    private static Snapshot decode(RegistryFriendlyByteBuf buffer) {
        var icon = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
        assertTrue(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer).isEmpty());
        var name = ComponentSerialization.STREAM_CODEC.decode(buffer);
        var item = AEKey.readOptionalKey(buffer);
        var fluid = AEKey.readOptionalKey(buffer);
        assertNull(AEKey.readOptionalKey(buffer));
        return new Snapshot(icon, name, item, fluid, buffer.readVarLong(), buffer.readByteArray(ChamberSnapshotCodec.MAX_SNAPSHOT_BYTES));
    }

    private static void accept(ChamberSnapshotCodec.Receiver receiver, long revision, ChamberSnapshotCodec.Chunk chunk, List<Snapshot> published) {
        receiver.accept(REGISTRIES, revision, chunk.totalBytes(), chunk.offset(), chunk.data(), NativeChamberSnapshotTest::decode, published::add);
    }

    @Test void nativeComponentsKeysLongAmountsAndEmptyStacksPublishOnlyAfterTheLastChunk() {
        var source = snapshot(); var chunks = encode(source); assertTrue(chunks.size() >= 3);
        var receiver = new ChamberSnapshotCodec.Receiver(); var published = new ArrayList<Snapshot>();
        for (int i = 0; i < chunks.size(); i++) {
            accept(receiver, 17, chunks.get(i), published);
            assertEquals(i == chunks.size() - 1 ? 1 : 0, published.size());
        }
        var restored = published.getFirst();
        assertTrue(ItemStack.matches(source.icon(), restored.icon())); assertEquals(source.name(), restored.name());
        assertEquals(source.item(), restored.item()); assertEquals(source.fluid(), restored.fluid());
        assertEquals(Long.MAX_VALUE, restored.amount()); assertArrayEquals(source.padding(), restored.padding());
    }

    @Test void wrongRevisionOutOfOrderAndMissingChunksNeverPublishAndAFreshTransferRecovers() {
        var chunks = encode(snapshot()); var receiver = new ChamberSnapshotCodec.Receiver(); var published = new ArrayList<Snapshot>();
        accept(receiver, 4, chunks.getFirst(), published); accept(receiver, 5, chunks.get(1), published);
        accept(receiver, 4, chunks.getLast(), published); assertTrue(published.isEmpty());
        accept(receiver, 7, chunks.getFirst(), published); accept(receiver, 7, chunks.getLast(), published);
        accept(receiver, 7, chunks.get(1), published); assertTrue(published.isEmpty());
        for (var chunk : chunks) accept(receiver, 8, chunk, published);
        assertEquals(1, published.size());
    }

    @Test void aNewFirstChunkReplacesAnUnfinishedSnapshotWithoutCombiningTheirContents() {
        var first = encode(snapshot()); var secondSource = snapshot(); secondSource.icon().setCount(11);
        var second = encode(secondSource); var receiver = new ChamberSnapshotCodec.Receiver(); var published = new ArrayList<Snapshot>();
        accept(receiver, 1, first.getFirst(), published);
        for (var chunk : second) accept(receiver, 2, chunk, published);
        assertEquals(1, published.size()); assertEquals(11, published.getFirst().icon().getCount());
    }

    @Test void oversizeAndFailedEncodersSendNoPartialSnapshot() {
        var chunks = new ArrayList<ChamberSnapshotCodec.Chunk>();
        assertThrows(IndexOutOfBoundsException.class, () -> ChamberSnapshotCodec.encode(REGISTRIES,
                buffer -> buffer.writeZero(ChamberSnapshotCodec.MAX_SNAPSHOT_BYTES + 1), chunks::add));
        assertTrue(chunks.isEmpty());
        assertThrows(IllegalArgumentException.class, () -> ChamberSnapshotCodec.encode(REGISTRIES, buffer -> {
            buffer.writeZero(ChamberSnapshotCodec.MAX_CHUNK_BYTES + 1); throw new IllegalArgumentException("Encoding failed");
        }, chunks::add));
        assertTrue(chunks.isEmpty());
    }

    @Test void malformedLengthsTruncationAndTrailingBytesDoNotPublishOrPoisonTheNextTransfer() {
        var receiver = new ChamberSnapshotCodec.Receiver(); var published = new ArrayList<Integer>();
        receiver.accept(REGISTRIES, 1, ChamberSnapshotCodec.MAX_SNAPSHOT_BYTES + 1, 0, new byte[]{1}, RegistryFriendlyByteBuf::readInt, published::add);
        receiver.accept(REGISTRIES, 1, 2, -1, new byte[]{1}, RegistryFriendlyByteBuf::readInt, published::add);
        receiver.accept(REGISTRIES, 1, 2, 0, new byte[ChamberSnapshotCodec.MAX_CHUNK_BYTES + 1], RegistryFriendlyByteBuf::readInt, published::add);
        assertTrue(published.isEmpty());
        assertThrows(IndexOutOfBoundsException.class, () -> receiver.accept(REGISTRIES, 2, 1, 0, new byte[]{1}, RegistryFriendlyByteBuf::readInt, published::add));
        assertThrows(IllegalArgumentException.class, () -> receiver.accept(REGISTRIES, 3, 5, 0, new byte[]{0, 0, 0, 7, 1}, RegistryFriendlyByteBuf::readInt, published::add));
        assertTrue(published.isEmpty());
        receiver.accept(REGISTRIES, 4, 4, 0, new byte[]{0, 0, 0, 9}, RegistryFriendlyByteBuf::readInt, published::add);
        assertEquals(List.of(9), published);
    }
}
