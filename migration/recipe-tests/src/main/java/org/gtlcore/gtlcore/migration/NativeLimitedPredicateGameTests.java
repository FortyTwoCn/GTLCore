package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.pattern.GTLPredicates;
import org.gtlcore.gtlcore.api.pattern.PatternMatchData;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.multiblock.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.multiblock.pattern.MultiblockPatternBuilder;
import com.gregtechceu.gtceu.api.multiblock.pattern.PatternState;
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
public final class NativeLimitedPredicateGameTests {
    private static MultiblockControllerMachine placeController(GameTestHelper helper) {
        var pos = new BlockPos(4, 2, 5); helper.setBlock(pos, NativeTerminalFixtures.REPEATING.getBlock());
        var machine = (MultiblockControllerMachine) helper.getBlockEntity(pos); machine.setFrontFacing(Direction.NORTH); return machine;
    }
    private static PatternState state(MultiblockControllerMachine controller) {
        var state = new PatternState(); state.setController(controller, controller.getBlockPos()); return state;
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeRepeatedSliceCountsAllOverlappingLimitsAndRollsBackRejectedProbes(GameTestHelper helper) {
        var controller = placeController(helper); var center = controller.getBlockPos();
        for (int n = 1; n <= 3; n++) helper.getLevel().setBlock(center.north(n), Blocks.IRON_BLOCK.defaultBlockState(), 3);
        var alternatives = blocks(Blocks.IRON_BLOCK)
                .or(GTLPredicates.countBlock("overlap_first", Blocks.IRON_BLOCK).setExactLimit(2).setMaxSliceCount(1))
                .or(GTLPredicates.countBlock("overlap_second", Blocks.IRON_BLOCK).setExactLimit(2).setMaxSliceCount(1));
        var pattern = (BlockPattern) MultiblockPatternBuilder.start(FRONT, UP, LEFT)
                .slice("S").sliceRepeatable(1, 3, "A").slice("B")
                .where('S', controller(NativeTerminalFixtures.REPEATING.get())).where('A', alternatives)
                .where('B', blocks(Blocks.IRON_BLOCK)).build();
        var state = state(controller);
        helper.assertTrue(pattern.checkPatternAt(helper.getLevel(), state, center, Direction.NORTH, Direction.UP, false),
                "Native repeated pattern did not recover after the third limited block exceeded its maximum");
        for (var limited : alternatives.predicates()) if (limited.getMaxCount() == 2)
            helper.assertTrue(state.getContext().getGlobalCount(limited) == 2, "Only the first overlapping native predicate counted, or a failed probe leaked");
        var data = PatternMatchData.of(state.getContext());
        helper.assertTrue(pattern.getRepetitionCount(1) == 2 && data.blockCount("overlap_first") == 2 &&
                data.blockCount("overlap_second") == 2 && state.getCache().size() == 4 && !state.hasErrors(),
                "Overlapping Core position data or native cache retained a rejected branch");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeMaximumCannotFallBackToAnUnlimitedAlternativeAndRepairsRecheckFreshCounts(GameTestHelper helper) {
        var controller = placeController(helper); var center = controller.getBlockPos();
        for (int n = 1; n <= 2; n++) helper.getLevel().setBlock(center.north(n), Blocks.IRON_BLOCK.defaultBlockState(), 3);
        var alternatives = blocks(Blocks.IRON_BLOCK).or(GTLPredicates.countBlock("bounded_iron", Blocks.IRON_BLOCK).setExactLimit(1));
        var pattern = (BlockPattern) MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("S").slice("A").slice("A")
                .where('S', controller(NativeTerminalFixtures.REPEATING.get())).where('A', alternatives.or(blocks(Blocks.GOLD_BLOCK))).build();
        var state = state(controller);
        helper.assertTrue(!pattern.checkPatternAt(helper.getLevel(), state, center, Direction.NORTH, Direction.UP, false) && state.hasErrors(),
                "Unlimited native alternative hid the explicit maximum");
        helper.getLevel().setBlock(center.north(2), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        helper.assertTrue(pattern.checkPatternAt(helper.getLevel(), state, center, Direction.NORTH, Direction.UP, false) &&
                PatternMatchData.of(state.getContext()).blockCount("bounded_iron") == 1 && !state.hasErrors(),
                "Native repair reused failed predicate counts or rejected the unlimited gold alternative");
        helper.succeed();
    }
}
