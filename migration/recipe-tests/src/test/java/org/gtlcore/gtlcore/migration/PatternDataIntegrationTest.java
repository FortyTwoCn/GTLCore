package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.pattern.GTLPredicates;
import org.gtlcore.gtlcore.api.pattern.PatternMatchData;

import com.gregtechceu.gtceu.api.multiblock.PredicateContext;
import com.gregtechceu.gtceu.api.multiblock.error.PatternStringError;
import com.gregtechceu.gtceu.api.multiblock.pattern.PatternState;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/** Uses the actual native predicates and transformed context lifecycle. */
class PatternDataIntegrationTest {
    private static final class Context extends PredicateContext {
        private BlockState block;
        private BlockPos pos;
        Context() { super(null); }
        void block(Block block, int x) { this.block = block.defaultBlockState(); this.pos = new BlockPos(x, 64, 0); }
        @Override public BlockState state() { return block; }
        @Override public BlockPos pos() { return pos; }
    }

    private static Int2ObjectLinkedOpenHashMap<Supplier<?>> tiers() {
        var map = new Int2ObjectLinkedOpenHashMap<Supplier<?>>();
        map.put(1, () -> Blocks.IRON_BLOCK);
        map.put(2, () -> Blocks.GOLD_BLOCK);
        return map;
    }

    @Test
    void tierCasingsRequireUniformTierAndRejectOtherBlocks() {
        var context = new Context();
        var predicate = GTLPredicates.tierCasings(tiers(), "CATier");
        context.block(Blocks.IRON_BLOCK, 0);
        assertNotNull(predicate.getPredicateAtPos(context));
        context.block(Blocks.IRON_BLOCK, 1);
        assertNotNull(predicate.getPredicateAtPos(context));
        context.block(Blocks.GOLD_BLOCK, 2);
        assertNull(predicate.getPredicateAtPos(context));
        assertEquals(1, PatternMatchData.of(context).tier("CATier").orElseThrow());
        context.block(Blocks.STONE, 3);
        assertNull(predicate.getPredicateAtPos(context));
    }

    @Test
    void rejectedTierHasNativePatternError() {
        var state = new PatternState();
        var context = state.getContext();
        GTLPredicates.tierCasings(tiers(), "CATier").predicates().getFirst().onError(context);
        context.commitSliceErrors();
        assertInstanceOf(PatternStringError.class, state.getErrors().getFirst());
        assertEquals("gtceu.multiblock.pattern.error.tier",
                ((net.minecraft.network.chat.contents.TranslatableContents)
                        ((PatternStringError) state.getErrors().getFirst()).component.getContents()).getKey());
    }

    @Test
    void countingTracksPositionsAndIgnoresFailedPredicates() {
        var context = new Context();
        var predicate = GTLPredicates.countBlock("Unit", Blocks.IRON_BLOCK);
        context.block(Blocks.IRON_BLOCK, 0);
        assertNotNull(predicate.getPredicateAtPos(context));
        assertNotNull(predicate.getPredicateAtPos(context));
        context.block(Blocks.IRON_BLOCK, 1);
        assertNotNull(predicate.getPredicateAtPos(context));
        context.block(Blocks.STONE, 2);
        assertNull(predicate.getPredicateAtPos(context));
        assertEquals(2, PatternMatchData.of(context).blockCount("Unit"));
    }

    @Test
    void failedRepeatSliceRollsBackOnlyItsCounts() {
        var context = new Context();
        var predicate = GTLPredicates.countBlock("SpeedPipe", Blocks.IRON_BLOCK);
        context.pushSlice();
        context.block(Blocks.IRON_BLOCK, 0);
        assertNotNull(predicate.getPredicateAtPos(context));
        context.clearErrors();
        context.pushSlice();
        context.block(Blocks.IRON_BLOCK, 1);
        assertNotNull(predicate.getPredicateAtPos(context));
        context.block(Blocks.IRON_BLOCK, 2);
        assertNotNull(predicate.getPredicateAtPos(context));
        context.block(Blocks.STONE, 3);
        assertNull(predicate.getPredicateAtPos(context));
        context.popSlice();
        assertEquals(1, PatternMatchData.of(context).blockCount("SpeedPipe"));
    }

    @Test
    void aTierEstablishedInAFailedSliceDoesNotLeakIntoTheNextAttempt() {
        var context = new Context();
        var predicate = GTLPredicates.tierCasings(tiers(), "SCTier");
        context.pushSlice();
        context.block(Blocks.IRON_BLOCK, 0);
        assertNotNull(predicate.getPredicateAtPos(context));
        context.popSlice();
        assertTrue(PatternMatchData.of(context).tier("SCTier").isEmpty());
        context.pushSlice();
        context.block(Blocks.GOLD_BLOCK, 0);
        assertNotNull(predicate.getPredicateAtPos(context));
        assertEquals(2, PatternMatchData.of(context).tier("SCTier").orElseThrow());
    }

    @Test
    void resettingTheNativeStateClearsDataBeforeAFlipOrRecheck() {
        var state = new PatternState();
        var data = PatternMatchData.of(state.getContext());
        data.matchTier("CATier", 2);
        data.countBlock("FuelAssembly", new BlockPos(0, 64, 0));
        state.resetContext(true);
        assertTrue(data.tier("CATier").isEmpty());
        assertEquals(0, data.blockCount("FuelAssembly"));
    }

    @Test
    void cachedResultsRemainAvailableUntilTheNextFullCheck() {
        var state = new PatternState();
        var context = state.getContext();
        var data = PatternMatchData.of(context);
        context.pushSlice();
        data.matchTier("CATier", 2);
        data.countBlock("Cooler", new BlockPos(0, 64, 0));
        context.clearErrors();
        context.commitSliceErrors();
        state.setState(PatternState.CheckState.VALID_CACHED);
        assertEquals(2, data.tier("CATier").orElseThrow());
        assertEquals(1, data.blockCount("Cooler"));
    }

    @Test
    void machinesAndNamedDataDoNotShareState() {
        var first = new PatternState();
        var second = new PatternState();
        var data = PatternMatchData.of(first.getContext());
        assertTrue(data.matchTier("CATier", 1));
        assertTrue(data.matchTier("SCTier", 2));
        data.countBlock("FuelAssembly", BlockPos.ZERO);
        data.countBlock("Cooler", BlockPos.ZERO);
        assertEquals(1, data.blockCount("FuelAssembly"));
        assertEquals(1, data.blockCount("Cooler"));
        assertTrue(PatternMatchData.of(second.getContext()).tier("CATier").isEmpty());
        assertEquals(0, PatternMatchData.of(second.getContext()).blockCount("Cooler"));
    }

    @Test
    void candidateListKeepsAllCasingChoices() {
        var predicate = GTLPredicates.tierCasings(tiers(), "CATier");
        assertEquals(java.util.List.of(Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK),
                predicate.getCandidates().getFirst().stream().map(info -> info.getBlockState().getBlock()).toList());
    }

    @Test
    void coilChosenInAFailedRepeatDoesNotPreventTheNextCoilChoice() {
        var context = new Context();
        var data = PatternMatchData.of(context);
        var copper = com.gregtechceu.gtceu.common.block.CoilBlock.CoilType.CUPRONICKEL;
        var steel = com.gregtechceu.gtceu.common.block.CoilBlock.CoilType.KANTHAL;
        context.pushSlice();
        assertTrue(data.matchCoil(copper));
        assertFalse(data.matchCoil(steel));
        context.popSlice();
        assertNull(data.coil());
        context.pushSlice();
        assertTrue(data.matchCoil(steel));
        context.pushSlice();
        assertTrue(data.matchCoil(steel));
        assertFalse(data.matchCoil(copper));
        context.popSlice();
        assertSame(steel, data.coil());
        context.reset();
        assertNull(data.coil());
    }

    @Test
    void nativeAddonCoilPredicatesKeepTemperatureLimitsAndUniformityAcrossRollback() {
        var context = new Context();
        var copper = com.gregtechceu.gtceu.common.block.CoilBlock.CoilType.CUPRONICKEL;
        var steel = com.gregtechceu.gtceu.common.block.CoilBlock.CoilType.KANTHAL;
        var coils = com.gregtechceu.gtceu.api.GTCEuAPI.HEATING_COILS;
        var exact = com.gtladd.gtladditions.common.machine.GTLAddPredicates.INSTANCE.heatingCoils(copper.getCoilTemperature(), true);
        context.block(coils.get(steel).get(), 0);
        assertNull(exact.getPredicateAtPos(context));
        context.block(coils.get(copper).get(), 0);
        context.pushSlice();
        assertNotNull(exact.getPredicateAtPos(context));
        context.popSlice();
        var minimum = com.gtladd.gtladditions.common.machine.GTLAddPredicates.INSTANCE.heatingCoils(copper.getCoilTemperature(), false);
        context.block(coils.get(steel).get(), 1);
        assertNotNull(minimum.getPredicateAtPos(context));
        context.block(coils.get(copper).get(), 2);
        assertNull(minimum.getPredicateAtPos(context));
        assertSame(steel, PatternMatchData.of(context).coil());
    }

    @Test
    void nativeAddonSlabPredicateRejectsSolidBlocksAndWrongSlabHalves() {
        var context = new Context();
        var slab = Blocks.QUARTZ_SLAB;
        var predicate = com.gtladd.gtladditions.common.machine.GTLAddPredicates.INSTANCE.slabBlock(
                net.minecraft.world.level.block.state.properties.SlabType.BOTTOM, slab);
        context.block(slab, 0);
        assertNotNull(predicate.getPredicateAtPos(context));
        context.block(Blocks.QUARTZ_BLOCK, 1);
        assertNull(predicate.getPredicateAtPos(context));
        var candidate = predicate.getCandidates().getFirst().getFirst().getBlockState();
        assertEquals(net.minecraft.world.level.block.state.properties.SlabType.BOTTOM,
                candidate.getValue(net.minecraft.world.level.block.SlabBlock.TYPE));
    }
}
