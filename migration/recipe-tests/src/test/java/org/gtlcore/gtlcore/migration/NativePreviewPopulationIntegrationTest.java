package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.gui.preview.NativePreviewBlockMap;
import com.gregtechceu.gtceu.api.multiblock.Predicates;
import com.gregtechceu.gtceu.api.multiblock.pattern.MultiblockPatternBuilder;
import com.gregtechceu.gtceu.api.multiblock.util.AbstractStructureHelper;
import com.gregtechceu.gtceu.api.multiblock.util.BlockInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NativePreviewPopulationIntegrationTest {
    @Test void indexedCountsMatchScansForReplacementRemovalMutableKeysAndDuplicateCandidates() {
        var map = new NativePreviewBlockMap();
        var predicate = Predicates.blocks(Blocks.STONE, Blocks.GLASS, Blocks.STONE).predicates().getFirst();
        var moving = new BlockPos.MutableBlockPos(3, -2, 7);
        map.put(moving, new BlockInfo(Blocks.STONE)); moving.set(100, 100, 100);
        map.put(new BlockPos(4, -2, 8), new BlockInfo(Blocks.GLASS));
        map.put(new BlockPos(4, -2, 8), new BlockInfo(Blocks.GLASS));
        map.put(new BlockPos(-2, 3, 7), new BlockInfo(Blocks.DIRT));
        assertEquals(2, map.countGlobal(predicate));
        assertEquals(2, map.countLayer(predicate, Direction.DOWN, -2));
        assertEquals(1, map.countLayer(predicate, Direction.NORTH, 7));
        map.put(new BlockPos(3, -2, 7), new BlockInfo(Blocks.DIRT));
        assertEquals(1, map.countGlobal(predicate));
        map.remove(new BlockPos(4, -2, 8));
        assertEquals(0, map.countGlobal(predicate));
        map.putAll(Map.of(BlockPos.ZERO, new BlockInfo(Blocks.STONE)));
        assertEquals(1, map.countLayer(predicate, Direction.EAST, 0));
        map.clear(); assertEquals(0, map.countGlobal(predicate)); assertTrue(map.isEmpty());
    }

    @Test void indexedNativePopulationPreservesMinMaxGlobalAndSliceLimitsInAll48Poses() {
        var glass = Predicates.blocks(Blocks.GLASS).setMinCount(2).setMaxCount(4).setMinSliceCount(1).setMaxSliceCount(1);
        var pattern = MultiblockPatternBuilder.start().slice("XXX", "X X")
                .sliceRepeatable(1, 3, "XX ", " XX").slice("X X", "XXX")
                .where('X', glass.or(Predicates.blocks(Blocks.STONE))).build();
        var repeats = new Int2IntOpenHashMap(Map.of(0, 1, 1, 2, 2, 1));
        for (var front : Direction.values()) for (var up : Direction.values()) {
            if (front.getAxis() == up.getAxis()) continue;
            for (boolean flipped : new boolean[] { false, true }) {
                var expected = new LinkedHashMap<BlockPos, BlockInfo>();
                var actual = new NativePreviewBlockMap();
                AbstractStructureHelper.blockPattern(repeats).populate(expected, pattern, null, front, up, flipped);
                AbstractStructureHelper.blockPattern(repeats).populate(actual, pattern, null, front, up, flipped);
                assertEquals(expected, actual, front + "/" + up + "/" + flipped);
                assertEquals(new ArrayList<>(expected.keySet()), new ArrayList<>(actual.keySet()));
                assertEquals(4, actual.values().stream().filter(info -> info.getBlockState().is(Blocks.GLASS)).count());
            }
        }
    }

    @Test @Timeout(15) void nativeHelperPopulates125000BlocksWithIndexedCandidateCounts() {
        var rows = new String[50]; Arrays.fill(rows, "A".repeat(50));
        var pattern = MultiblockPatternBuilder.start().sliceRepeatable(50, 50, rows)
                .where('A', Predicates.blocks(Blocks.STONE)).build();
        var blocks = new NativePreviewBlockMap();
        AbstractStructureHelper.blockPattern(new Int2IntOpenHashMap(Map.of(0, 50)))
                .populate(blocks, pattern, null, Direction.NORTH, Direction.UP, false);
        assertEquals(125_000, blocks.size());
        assertTrue(blocks.values().stream().allMatch(info -> info.getBlockState().is(Blocks.STONE)));
    }
}
