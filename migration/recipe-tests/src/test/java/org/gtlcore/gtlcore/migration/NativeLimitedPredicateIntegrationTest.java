package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.pattern.NativeLimitedPredicateMatches;
import com.gregtechceu.gtceu.api.multiblock.MultiPredicate;
import com.gregtechceu.gtceu.api.multiblock.PredicateContext;
import com.gregtechceu.gtceu.api.multiblock.predicates.BasePredicate;
import com.gregtechceu.gtceu.api.multiblock.predicates.PredicateBuilder;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class NativeLimitedPredicateIntegrationTest {
    private static BasePredicate always(String name) { return new PredicateBuilder(name).predicate(ctx -> true).build(); }
    private static PredicateContext context(int x) { var context = new PredicateContext(null); context.updatePos(new BlockPos(x, 64, 0)); return context; }

    @Test void overlappingLimitsCountTheirOriginalIdentitiesAndCannotHideMaximumFailures() {
        var first = always("first"); first.setMinCount(2); first.setMaxCount(2); first.setMinSliceCount(2); first.setMaxSliceCount(2);
        var second = always("second"); second.setMinCount(2); second.setMaxCount(2); second.setMinSliceCount(2); second.setMaxSliceCount(2);
        var owner = MultiPredicate.or(new ArrayList<>(List.of(first, second)));
        var context = context(0);
        for (int x = 0; x < 2; x++) {
            context.updatePos(new BlockPos(x, 64, 0));
            assertTrue(owner.getPredicateAtPos(context).checkMaxCount(context));
        }
        assertEquals(2, context.getGlobalCount(first)); assertEquals(2, context.getGlobalCount(second));
        assertEquals(2, context.getSliceCount(first)); assertEquals(2, context.getSliceCount(second));
        assertTrue(owner.postGlobalTest(context)); assertTrue(owner.postSliceTest(context));
        assertFalse(owner.getPredicateAtPos(context).checkMaxCount(context));
        assertEquals(3, context.getGlobalCount(first)); assertEquals(3, context.getGlobalCount(second));
        assertEquals(PredicateContext.FailureReason.GLOBAL_MAX, context.getLastFailureReason());
    }

    @Test void limitedAlternativesTakePrecedenceAndUnmatchedTestsRunOnlyOnceBeforeFallback() {
        var calls = new AtomicInteger();
        var limited = new PredicateBuilder("limited").predicate(ctx -> { calls.incrementAndGet(); return ctx.pos().getX() == 0; }).build();
        limited.setMaxCount(1);
        var common = always("common");
        var owner = MultiPredicate.or(new ArrayList<>(List.of(common, limited)));
        var context = context(0);
        assertSame(limited, owner.getPredicateAtPos(context));
        assertTrue(limited.checkMaxCount(context));
        context.updatePos(new BlockPos(1, 64, 0));
        assertSame(common, owner.getPredicateAtPos(context));
        assertTrue(common.checkMaxCount(context));
        assertEquals(2, calls.get()); assertEquals(1, context.getGlobalCount(limited));
        assertEquals(1, context.getGlobalCount(common));
    }

    @Test void nativeSliceLimitsAndDisabledLayerCheckingKeepTheirOwnCountPolicy() {
        var first = always("first"); first.setMaxSliceCount(1);
        var second = always("second"); second.setMaxSliceCount(1);
        var owner = MultiPredicate.or(new ArrayList<>(List.of(first, second)));
        var context = context(0);
        assertTrue(owner.getPredicateAtPos(context).checkMaxCount(context));
        assertFalse(owner.getPredicateAtPos(context).checkMaxCount(context));
        assertEquals(2, context.getSliceCount(first)); assertEquals(2, context.getSliceCount(second));
        assertEquals(PredicateContext.FailureReason.SLICE_MAX, context.getLastFailureReason());
        context.reset(); context.updatePos(BlockPos.ZERO); context.setCheckLayer(false);
        assertTrue(owner.getPredicateAtPos(context).checkMaxCount(context));
        assertTrue(owner.getPredicateAtPos(context).checkMaxCount(context));
        assertEquals(2, context.getGlobalCount(first)); assertEquals(2, context.getGlobalCount(second));
        assertEquals(0, context.getSliceCount(first)); assertEquals(0, context.getSliceCount(second));
    }

    @Test void nativeAndXorAndMixedTreesRetainTheirSelectionAndExclusivity() {
        var first = always("first"); first.setMinCount(1);
        var second = always("second"); second.setMinCount(1);
        var and = MultiPredicate.and(new ArrayList<>(List.of(first, second)));
        var context = context(0);
        assertFalse(NativeLimitedPredicateMatches.appliesTo(and));
        assertSame(first, and.getPredicateAtPos(context));
        assertTrue(first.checkMaxCount(context)); assertFalse(and.postGlobalTest(context));
        var a = new PredicateBuilder("x=0").predicate(ctx -> ctx.pos().getX() == 0).build(); a.setMinCount(1);
        var b = new PredicateBuilder("x=1").predicate(ctx -> ctx.pos().getX() == 1).build(); b.setMinCount(1);
        var xor = MultiPredicate.xor(new ArrayList<>(List.of(a, b)));
        assertFalse(NativeLimitedPredicateMatches.appliesTo(xor));
        assertSame(a, xor.getPredicateAtPos(context));
        context.updatePos(new BlockPos(1, 64, 0));
        assertNull(xor.getPredicateAtPos(context)); assertFalse(context.isCheckFlipped());
        var mixed = new PredicateBuilder("never").predicate(ctx -> false).toMultiPredicate().or(and);
        assertFalse(NativeLimitedPredicateMatches.appliesTo(mixed));
        assertFalse(NativeLimitedPredicateMatches.appliesTo(and));
        assertSame(first, mixed.getPredicateAtPos(context));
    }
}
