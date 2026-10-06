package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.pattern.NativePatternPredicateLookup;
import com.gregtechceu.gtceu.api.multiblock.Predicates;
import com.gregtechceu.gtceu.api.multiblock.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.multiblock.pattern.MultiblockPatternBuilder;
import com.gregtechceu.gtceu.api.multiblock.util.AbstractStructureHelper;
import com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class NativePatternPredicateLookupIntegrationTest {
    @Test void repeatableAsymmetricPatternMatchesTheNativeHelperInAll48Poses() {
        var pattern = (BlockPattern) MultiblockPatternBuilder.start(RelativeDirection.LEFT, RelativeDirection.FRONT, RelativeDirection.DOWN)
                .slice("ABC", "CBA").sliceRepeatable(0, 4, "BAC", "ACB").slice("CAB", "BCA")
                .where('A', Predicates.blocks(Blocks.STONE)).where('B', Predicates.blocks(Blocks.GLASS))
                .where('C', Predicates.blocks(Blocks.IRON_BLOCK)).build();
        for (int count : new int[] { 0, 1, 4 }) {
            var repeats = new Int2IntOpenHashMap(Map.of(0, 1, 1, count, 2, 1));
            var nativeHelper = AbstractStructureHelper.blockPattern(repeats);
            for (var front : Direction.values()) for (var up : Direction.values()) {
                if (front.getAxis() == up.getAxis()) continue;
                for (boolean mirrored : new boolean[] { false, true }) {
                    var lookup = NativePatternPredicateLookup.blockPattern(pattern, repeats, front, up, mirrored);
                    for (var pos : BlockPos.betweenClosed(-1, -1, -1, 7, 7, 7)) {
                        var expected = nativeHelper.getPredicateFromPos(pattern, pos, front, up, mirrored);
                        var actual = lookup.apply(pos);
                        if (expected.isAir()) assertTrue(actual.isAir());
                        else assertSame(expected, actual, front + "/" + up + "/" + mirrored + "/" + pos);
                    }
                }
            }
        }
        assertThrows(IllegalArgumentException.class, () -> NativePatternPredicateLookup.blockPattern(pattern,
                new Int2IntOpenHashMap(Map.of(1, 5)), Direction.NORTH, Direction.UP, false));
    }

    @Test @Timeout(10) void millionBlockPreviewQueriesDoNotAllocateOrRotateAMillionBlockPatternPerPosition() {
        var pattern = (BlockPattern) MultiblockPatternBuilder.start().sliceRepeatable(1, 1000, "ABC", "CBA")
                .where('A', Predicates.blocks(Blocks.STONE)).where('B', Predicates.blocks(Blocks.GLASS))
                .where('C', Predicates.blocks(Blocks.IRON_BLOCK)).build();
        var lookup = NativePatternPredicateLookup.blockPattern(pattern, new Int2IntOpenHashMap(Map.of(0, 1000)),
                Direction.EAST, Direction.UP, true);
        int queries = 0;
        for (int pass = 0; pass < 200; pass++) for (var pos : BlockPos.betweenClosed(0, 0, 0, 999, 1, 2)) {
            assertFalse(lookup.apply(pos).isAir()); queries++;
        }
        assertEquals(1_200_000, queries);
    }
}
