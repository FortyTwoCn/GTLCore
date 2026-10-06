package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.pattern.GTLPredicates;
import org.gtlcore.gtlcore.api.pattern.PatternMatchData;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.multiblock.pattern.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static com.gregtechceu.gtceu.api.multiblock.Predicates.*;
import static com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection.*;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativePatternSearchGameTests {
    private static MultiblockControllerMachine placeController(GameTestHelper helper) {
        var pos = new BlockPos(4, 2, 5); helper.setBlock(pos, NativeTerminalFixtures.REPEATING.getBlock());
        var controller = (MultiblockControllerMachine) helper.getBlockEntity(pos); controller.setFrontFacing(Direction.NORTH); return controller;
    }
    private static PatternState state(MultiblockControllerMachine controller) {
        var state = new PatternState(); state.setController(controller, controller.getBlockPos()); return state;
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void overlappingRepeatedAndFinalSlicesBacktrackWithoutLeavingProbeCountsOrCacheEntries(GameTestHelper helper) {
        var controller = placeController(helper); var center = controller.getBlockPos();
        for (int offset = 1; offset <= 3; offset++) helper.getLevel().setBlock(center.north(offset), Blocks.IRON_BLOCK.defaultBlockState(), 3);
        var pattern = (BlockPattern) MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("S").sliceRepeatable(1, 3, "I").slice("I")
                .where('S', controller(NativeTerminalFixtures.REPEATING.get()))
                .where('I', GTLPredicates.countBlock("native_search_iron", Blocks.IRON_BLOCK).setExactLimit(3)).build();
        var state = state(controller);
        helper.assertTrue(pattern.checkPatternAt(helper.getLevel(), state, center, Direction.NORTH, Direction.UP, false), "Overlapping repetition boundary failed to backtrack");
        helper.assertTrue(pattern.getRepetitionCount(1) == 2 && PatternMatchData.of(state.getContext()).blockCount("native_search_iron") == 3, "Failed branches changed the accepted native counts");
        helper.assertTrue(state.getCache().size() == 4 && !state.getCache().containsKey(center.north(4).asLong()) && !state.hasErrors(), "Speculative slice was retained in the successful cache or errors");
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void globalMaxFailureRestoresGtlDataBeforeTheNextSlice(GameTestHelper helper) {
        var controller = placeController(helper); var center = controller.getBlockPos();
        for (int offset = 1; offset <= 3; offset++) helper.getLevel().setBlock(center.north(offset), Blocks.IRON_BLOCK.defaultBlockState(), 3);
        var pattern = (BlockPattern) MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("S").sliceRepeatable(1, 3, "A").slice("B")
                .where('S', controller(NativeTerminalFixtures.REPEATING.get()))
                .where('A', GTLPredicates.countBlock("native_quota_a", Blocks.IRON_BLOCK).setExactLimit(2))
                .where('B', GTLPredicates.countBlock("native_quota_b", Blocks.IRON_BLOCK).setExactLimit(1)).build();
        var state = state(controller);
        helper.assertTrue(pattern.checkPatternAt(helper.getLevel(), state, center, Direction.NORTH, Direction.UP, false), "Native max-count probe prevented a valid layout");
        var data = PatternMatchData.of(state.getContext());
        helper.assertTrue(pattern.getRepetitionCount(1) == 2 && data.blockCount("native_quota_a") == 2 && data.blockCount("native_quota_b") == 1 && state.getCache().size() == 4, "Rejected max-count slice survived context replay");
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void optionalZeroRepeatAndNativeMultiSliceGroupsRetainTheirSemantics(GameTestHelper helper) {
        var controller = placeController(helper); var center = controller.getBlockPos();
        helper.getLevel().setBlock(center.north(), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        var optional = (BlockPattern) MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("S").sliceRepeatable(0, 3, "I").slice("G")
                .where('S', controller(NativeTerminalFixtures.REPEATING.get())).where('I', blocks(Blocks.IRON_BLOCK)).where('G', blocks(Blocks.GOLD_BLOCK)).build();
        var first = state(controller);
        helper.assertTrue(optional.checkPatternAt(helper.getLevel(), first, center, Direction.NORTH, Direction.UP, false) && optional.getRepetitionCount(1) == 0 && first.getCache().size() == 2, "Optional zero-repetition slice was rejected or cached a failed probe");
        for (int offset = 1; offset <= 4; offset++) helper.getLevel().setBlock(center.north(offset), (offset % 2 == 1 ? Blocks.IRON_BLOCK : Blocks.GOLD_BLOCK).defaultBlockState(), 3);
        var strategy = new BasicSliceStrategy().multiSlice(1, 2, 1, 3);
        var grouped = (BlockPattern) MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("S").slice("I").slice("G").sliceStrategy(strategy)
                .where('S', controller(NativeTerminalFixtures.REPEATING.get())).where('I', blocks(Blocks.IRON_BLOCK)).where('G', blocks(Blocks.GOLD_BLOCK)).build();
        helper.assertTrue(grouped.checkPatternAt(helper.getLevel(), state(controller), center, Direction.NORTH, Direction.UP, false) && strategy.getMultiSliceRepeats(1) == 2, "Native multi-slice grouping was replaced by per-slice repetition");
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void structureChecksRejectUnloadedChunksWithoutLoadingThem(GameTestHelper helper) {
        var controller = placeController(helper); var unloaded = controller.getBlockPos().offset(16384, 0, 16384);
        helper.assertTrue(!helper.getLevel().hasChunkAt(unloaded), "Chunk guard fixture unexpectedly started loaded");
        var pattern = (BlockPattern) MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("I").where('I', blocks(Blocks.IRON_BLOCK)).build();
        var state = state(controller);
        helper.assertTrue(!pattern.checkPatternAt(helper.getLevel(), state, unloaded, Direction.NORTH, Direction.UP, false) && state.hasErrors() && !helper.getLevel().hasChunkAt(unloaded), "Structure checking loaded an unavailable chunk or accepted it");
        helper.succeed();
    }
}
