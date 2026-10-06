package org.gtlcore.gtlcore.mixin.gtm.api.pattern;

import org.gtlcore.gtlcore.api.pattern.PatternLayoutSearch;
import org.gtlcore.gtlcore.api.pattern.PreviewMatcherTiming;
import org.gtlcore.gtlcore.api.pattern.WorldPatternTiming;
import org.gtlcore.gtlcore.api.pattern.PatternCheckScope;
import com.gregtechceu.gtceu.api.multiblock.MultiPredicate;
import com.gregtechceu.gtceu.api.multiblock.error.PatternStringError;
import com.gregtechceu.gtceu.api.multiblock.pattern.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Native ordered slice checks preserve GT's predicates, counts and context rollback during repetition search. */
@Mixin(value = BlockPattern.class, remap = false)
public abstract class BlockPatternMixin {
    @Unique private static final ThreadLocal<PatternCheckScope> gtlcore$scope = new ThreadLocal<>();

    @WrapMethod(method = "checkPatternAt")
    private boolean gtlcore$match(Level level, PatternState state, BlockPos center, Direction front, Direction up,
                                 boolean flipped, Operation<Boolean> original) {
        var previous = gtlcore$scope.get(); var scope = new PatternCheckScope(); gtlcore$scope.set(scope);
        try (var timing = WorldPatternTiming.beginMatcher(level, state, front, flipped)) {
            var block = (BlockPattern) (Object) this;
            boolean variable = java.util.Arrays.stream(block.getSlices()).anyMatch(slice -> slice.getMinRepeats() != slice.getMaxRepeats());
            boolean matched;
            if (variable && gtlcore$simpleGroups(block)) {
                WorldPatternTiming.attempt(state, "native_repetition_search"); PreviewMatcherTiming.incrementalAttempt();
                matched = gtlcore$search(block, level, state, center, front, up, flipped, scope);
            } else {
                WorldPatternTiming.attempt(state, variable ? "native_group_strategy" : "native_fixed_linear");
                PreviewMatcherTiming.attempt(!variable);
                matched = original.call(level, state, center, front, up, flipped);
            }
            if (scope.unloaded) matched = false;
            if (timing != null) timing.result(matched);
            return matched;
        } finally {
            if (previous == null) gtlcore$scope.remove(); else gtlcore$scope.set(previous);
        }
    }

    @Unique private static boolean gtlcore$simpleGroups(BlockPattern block) {
        if (!(block.getSliceStrategy() instanceof BasicSliceStrategy strategy)) return false;
        var groups = ((BasicSliceStrategyAccessor) strategy).gtlcore$getGroups();
        if (groups.size() != block.getSlices().length) return false;
        for (int index = 0; index < groups.size(); index++) {
            var group = (BasicSliceGroupAccessor) groups.get(index);
            if (group.gtlcore$getMin() != 1 || group.gtlcore$getMax() != 1 ||
                    group.gtlcore$getStart() != index || group.gtlcore$getEnd() != index + 1) return false;
        }
        return true;
    }

    @Unique private static boolean gtlcore$search(BlockPattern block, Level level, PatternState state, BlockPos center,
                                                  Direction front, Direction up, boolean flipped, PatternCheckScope scope) {
        int[][] ranges = new int[block.getSlices().length][2]; int[] repetitions = new int[ranges.length];
        for (int i = 0; i < ranges.length; i++) {
            ranges[i][0] = block.getSlices()[i].getMinRepeats(); ranges[i][1] = block.getSlices()[i].getMaxRepeats();
        }
        boolean matched = PatternLayoutSearch.match(ranges, 0, repetitions, new PatternLayoutSearch.Checks(
                () -> {
                    state.resetContext(true); state.getCache().clear(); state.clearErrors(); state.getContext().updateLevel(level);
                    block.getPredicates().values().forEach(MultiPredicate::resetLogic);
                },
                (slice, offset) -> {
                    state.getContext().pushSlice();
                    boolean result = block.checkSlice(center.mutable(), state, front, up, slice, offset, flipped);
                    if (!result) state.getContext().commitSliceErrors();
                    else state.getContext().clearErrors();
                    return result;
                },
                () -> {
                    for (var predicate : block.getPredicates().values()) if (!predicate.postGlobalTest(state.getContext())) {
                        state.getContext().commitSliceErrors(); return false;
                    }
                    return true;
                },
                () -> scope.unloaded,
                () -> { PreviewMatcherTiming.replay(); WorldPatternTiming.replay(state); },
                () -> { PreviewMatcherTiming.replayedSlice(); WorldPatternTiming.replayedSlice(state); }));
        if (matched) {
            for (int i = 0; i < repetitions.length; i++) block.getSlices()[i].setActualRepeats(repetitions[i]);
            var groups = ((BasicSliceStrategyAccessor) block.getSliceStrategy()).gtlcore$getGroups();
            for (var group : groups) ((BasicSliceGroupAccessor) group).gtlcore$setActual(1);
            state.clearErrors();
        } else if (!state.hasErrors()) {
            state.setError(PatternStringError.literal("No valid repetition layout"));
        }
        return matched;
    }

    @WrapMethod(method = "checkSlice")
    private boolean gtlcore$slice(BlockPos.MutableBlockPos center, PatternState state, Direction front, Direction up,
                                  int index, int offset, boolean flipped, Operation<Boolean> original) {
        PreviewMatcherTiming.slice(); WorldPatternTiming.slice(state);
        var level = state.getContext().getCurrentBlockInfo().getLevel();
        if (level instanceof ServerLevel) {
            var block = (BlockPattern) (Object) this; var origin = center.mutable(); block.getOffset().apply(origin, front, up, flipped);
            var dirs = block.getDirections();
            var scope = gtlcore$scope.get();
            for (int row = 0; row < block.getDimensions()[1]; row++) for (int column = 0; column < block.getDimensions()[2]; column++) {
                if (block.getPredicates().get(block.getSlices()[index].charAt(row, column)).isAny()) continue;
                var pos = origin.relative(dirs[0].getRelativeFacing(front, up, flipped), offset)
                        .relative(dirs[1].getRelativeFacing(front, up, flipped), row)
                        .relative(dirs[2].getRelativeFacing(front, up, flipped), column);
                long chunk = net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
                if (scope != null && scope.checkedChunks.contains(chunk)) continue;
                if (!level.hasChunkAt(pos)) {
                    if (scope != null) scope.unloaded = true;
                    state.setError(PatternStringError.literal("Structure chunk is not loaded at " + pos.toShortString()));
                    state.getContext().skipFlipCheck(); return false;
                }
                if (scope != null) scope.checkedChunks.add(chunk);
            }
        }
        return original.call(center, state, front, up, index, offset, flipped);
    }
}
