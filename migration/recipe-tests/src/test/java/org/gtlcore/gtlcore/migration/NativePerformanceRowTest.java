package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.machine.PerformanceMonitorMachine.Row;
import brachy.modularui.value.sync.GenericListSyncHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NativePerformanceRowTest {
    @Test void nativeRowPacketPreservesDimensionSignedPositionTranslationAndLargeTiming() {
        var row = new Row(ResourceLocation.parse("gtlcore:flat"), new BlockPos(-30000000, -64, 29999999), "block.gtlcore.performance_monitor", Integer.MAX_VALUE);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try { Row.write(buffer, row); assertEquals(row, Row.read(buffer)); assertFalse(buffer.isReadable()); }
        finally { buffer.release(); }
    }
    @Test void rowsSnapshotMutablePositionsAndRejectNegativeNativePacketTiming() {
        var position = new BlockPos.MutableBlockPos(1, 2, 3); var dimension = ResourceLocation.parse("minecraft:overworld");
        var row = new Row(dimension, position, "block.gtlcore.performance_monitor", 0); position.set(9, 9, 9);
        assertEquals(new BlockPos(1, 2, 3), row.position());
        assertDoesNotThrow(row::locationText); assertDoesNotThrow(row::label);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try { buffer.writeResourceLocation(dimension); buffer.writeBlockPos(row.position()); buffer.writeUtf(row.nameKey()); buffer.writeVarInt(-1);
            assertThrows(IllegalArgumentException.class, () -> Row.read(buffer)); }
        finally { buffer.release(); }
    }
    @Test void nativeModularUiListCodecRetainsDuplicateTimingsAndIndependentReadOnlyRows() {
        var dimension = ResourceLocation.parse("minecraft:overworld");
        var rows = List.of(new Row(dimension, new BlockPos(1, 2, 3), "block.gtlcore.first", 20), new Row(dimension, new BlockPos(4, 5, 6), "block.gtlcore.second", 20));
        var source = handler(rows); var target = handler(List.of()); source.updateCacheFromSource(true);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try { source.write(buffer); target.read(buffer); assertEquals(rows, target.getValue()); assertFalse(buffer.isReadable());
            assertNotSame(source.getValue(), target.getValue()); assertThrows(UnsupportedOperationException.class, () -> target.getValue().clear()); }
        finally { buffer.release(); }
    }
    private static GenericListSyncHandler<RegistryFriendlyByteBuf, Row> handler(List<Row> rows) {
        return GenericListSyncHandler.<RegistryFriendlyByteBuf, Row>builder().getter(() -> rows).serializer(Row::write).deserializer(Row::read).copy(row -> row).build();
    }
}
