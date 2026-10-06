package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.item.StructureDetectBehavior;
import org.gtlcore.gtlcore.network.packet.SStructureDetectHighlight;
import com.gregtechceu.gtceu.api.multiblock.error.*;
import com.gregtechceu.gtceu.api.multiblock.util.BlockInfo;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NativeStructureDetectIntegrationTest {
    @Test void nativeCountErrorsKeepExactBoundsActualCountsCandidatesAndMirrorPosition() {
        var mutable = new BlockPos.MutableBlockPos(-7, 84, 19);
        for (var type : SinglePredicateError.ErrorType.values()) {
            var error = new SinglePredicateError(type, 17, 5, 12, 3, 9, "test", List.of(new BlockInfo(Blocks.IRON_BLOCK)));
            var detection = new StructureDetectBehavior.DetectionError("main", error, mutable);
            var lines = StructureDetectBehavior.INSTANCE.describe(detection, true);
            assertTrue(lines.stream().anyMatch(line -> line.getString().contains(Blocks.IRON_BLOCK.getName().getString())));
            var details = (TranslatableContents) lines.getLast().getContents();
            assertEquals("gtceu.multiblock.pattern.error.limited." + type.getSerializedName(), details.getKey());
            int bound = switch (type) { case MIN_COUNT -> 5; case MAX_COUNT -> 12; case MIN_LAYER_COUNT -> 3; case MAX_LAYER_COUNT -> 9; };
            assertArrayEquals(new Object[] { bound, 17 }, details.getArgs());
            var header = (TranslatableContents) lines.getFirst().getContents();
            var location = (TranslatableContents) ((Component) header.getArgs()[0]).getContents();
            assertEquals(-7, location.getArgs()[0]); assertEquals(84, location.getArgs()[1]); assertEquals(19, location.getArgs()[2]);
            assertEquals("item.gtlcore.structure_detect.error.3", ((TranslatableContents) ((Component) location.getArgs()[3]).getContents()).getKey());
        }
        var detection = new StructureDetectBehavior.DetectionError("main", PatternStringError.literal("original failure"), mutable);
        mutable.set(100, 100, 100);
        assertEquals(new BlockPos(-7, 84, 19), detection.pos());
        assertEquals("original failure", StructureDetectBehavior.INSTANCE.describe(detection, false).getFirst().getString());
    }

    @Test void nativeBlockAndFluidCandidatesRemainReadableInDiagnosticMessages() {
        var block = new StructureDetectBehavior.DetectionError("main", new BlockMatchingError(BlockPos.ZERO, List.of(Blocks.GOLD_BLOCK)), BlockPos.ZERO);
        assertTrue(StructureDetectBehavior.INSTANCE.describe(block, false).stream().anyMatch(line -> line.getString().contains(Blocks.GOLD_BLOCK.getName().getString())));
        var water = new BlockInfo(Blocks.WATER);
        var error = new SimplePatternError(BlockPos.ZERO, List.of(List.of(water)));
        var lines = StructureDetectBehavior.INSTANCE.describe(new StructureDetectBehavior.DetectionError("auxiliary", error, BlockPos.ZERO), false);
        assertTrue(lines.stream().anyMatch(line -> line.getString().contains(water.getBlockState().getFluidState().getType().getFluidType().getDescription().getString())));
    }

    @Test void nativeHighlightPayloadRoundTripsSignedPositionDimensionAndExactDeadline() {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var position = new BlockPos.MutableBlockPos(-300000, -41, 200000);
        var packet = new SStructureDetectHighlight(position, Level.NETHER, Long.MAX_VALUE - 15_000);
        position.set(1, 1, 1);
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            packet.toBytes(buf); var loaded = new SStructureDetectHighlight(); loaded.fromBytes(buf);
            assertFalse(buf.isReadable()); buf.clear(); loaded.toBytes(buf);
            assertEquals(new BlockPos(-300000, -41, 200000), BlockPos.of(buf.readVarLong()));
            assertEquals(Level.NETHER, buf.readResourceKey(net.minecraft.core.registries.Registries.DIMENSION));
            assertEquals(Long.MAX_VALUE - 15_000, buf.readVarLong()); assertFalse(buf.isReadable());
            assertEquals(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gtlcore", "s_structure_detect_highlight"), loaded.id());
            assertTrue(loaded.isClient());
        } finally { buf.release(); }
    }
}
