package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.gui.preview.NativePreviewGeometry;
import org.gtlcore.gtlcore.api.gui.preview.NativePreviewShapes;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection;
import net.minecraft.world.level.block.Blocks;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class NativePreviewGeometryIntegrationTest {
    @Test void verticalVisualControllerFacingsKeepTheirNativeUpwardsAxisOrthogonal() {
        var definition = GTMultiMachines.ELECTRIC_BLAST_FURNACE.get();
        var property = definition.getRotationState().property;
        var upwards = com.gregtechceu.gtceu.api.block.property.GTBlockStateProperties.UPWARDS_FACING;
        var source = NativePreviewGeometry.visualBuilder().aisle("SC")
                .where('S', definition, Direction.DOWN).where('C', Blocks.GLASS).build(definition);
        assertEquals(Direction.NORTH, source.states().get(source.controllerPos()).getValue(upwards));
        for (var front : Direction.values()) for (var up : Direction.values()) {
            if (front.getAxis() == up.getAxis()) continue;
            var host = source.oriented(front, up, false).states().get(BlockPos.ZERO);
            assertEquals(front, host.getValue(property));
            assertEquals(up, host.getValue(upwards));
            assertNotEquals(host.getValue(property).getAxis(), host.getValue(upwards).getAxis());
        }
    }

    @Test void realNativeVariablePatternPagesAreIndependentSparseStateSnapshots() {
        var definition = GTMultiMachines.DISTILLATION_TOWER.get();
        var pages = NativePreviewShapes.get(definition);
        assertTrue(pages.size() > 1);
        var first = pages.getFirst(); var last = pages.getLast();
        assertTrue(last.states().size() > first.states().size());
        assertTrue(first.states().get(first.controllerPos()).is(definition.getBlock()));
        assertEquals(first.states().size(), first.predicates().size());
        assertTrue(first.states().values().stream().noneMatch(state -> state.isAir()));
        assertThrows(UnsupportedOperationException.class, () -> first.states().clear());
        assertSame(first, pages.getFirst());
        NativePreviewShapes.invalidate(definition);
        var refreshed = NativePreviewShapes.get(definition).getFirst();
        assertNotSame(first, refreshed); assertEquals(first.states(), refreshed.states());
    }

    @Test void nativeHelpersRetainPredicatePositionsAcrossAllSixOrthogonalControllerFacings() {
        var definition = GTMultiMachines.ELECTRIC_BLAST_FURNACE.get(); var pattern = definition.getStructurePatterns().get("main").get();
        int positions = -1;
        for (var front : Direction.values()) {
            var up = front.getAxis() == Direction.Axis.Y ? Direction.NORTH : Direction.UP;
            var geometry = NativePreviewGeometry.fromPattern(definition, pattern, new Int2IntOpenHashMap(), List.of(), front, up, false);
            if (positions < 0) positions = geometry.states().size();
            assertEquals(positions, geometry.states().size());
            assertEquals(positions, geometry.predicates().size());
            assertTrue(geometry.states().get(geometry.controllerPos()).is(definition.getBlock()));
        }
    }

    @Test void explicitVisualVariantsKeepNativeHatchFacingAndDoNotShareTheirPalette() {
        var definition = GTMultiMachines.ELECTRIC_BLAST_FURNACE.get();
        var original = NativePreviewGeometry.visualBuilder().aisle("SC", " I")
                .where('S', definition, Direction.NORTH).where('C', Blocks.GLASS)
                .where('I', GTMachines.ITEM_IMPORT_BUS[GTValues.LV], Direction.SOUTH);
        var changed = original.shallowCopy().where('C', Blocks.IRON_BLOCK);
        var first = original.build(definition); var second = changed.build(definition);
        assertEquals(3, first.states().size());
        assertEquals(1, first.states().values().stream().filter(state -> state.is(Blocks.GLASS)).count());
        assertEquals(1, second.states().values().stream().filter(state -> state.is(Blocks.IRON_BLOCK)).count());
        var bus = first.states().values().stream().filter(state -> state.is(GTMachines.ITEM_IMPORT_BUS[GTValues.LV].getBlock())).findFirst().orElseThrow();
        assertEquals(Direction.SOUTH, bus.getValue(GTMachines.ITEM_IMPORT_BUS[GTValues.LV].get().getRotationState().property));
    }

    @Test void worldVisualOrientationPreservesControllerAnchorNativeAxesHatchFacingAndMirroring() {
        var definition = GTMultiMachines.ELECTRIC_BLAST_FURNACE.get();
        var property = definition.getRotationState().property;
        var anchor = new BlockPos(4, 2, 7);
        var source = NativePreviewGeometry.fromStates(Map.of(
                anchor, definition.getBlock().defaultBlockState().setValue(property, Direction.NORTH),
                anchor.east(), Blocks.IRON_BLOCK.defaultBlockState(),
                anchor.above(), Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X),
                anchor.south(), GTMachines.ITEM_IMPORT_BUS[GTValues.LV].getBlock().defaultBlockState()), anchor);
        for (var front : Direction.values()) for (var up : Direction.values()) {
            if (front.getAxis() == up.getAxis()) continue;
            for (boolean flipped : new boolean[] { false, true }) {
                var geometry = source.oriented(front, up, flipped);
                assertEquals(BlockPos.ZERO, geometry.controllerPos());
                assertEquals(4, geometry.states().size());
                assertEquals(front, geometry.states().get(BlockPos.ZERO).getValue(property));
                var right = RelativeDirection.RIGHT.getRelativeFacing(front, up, flipped);
                assertTrue(geometry.states().get(BlockPos.ZERO.relative(right)).is(Blocks.IRON_BLOCK));
                var log = geometry.states().get(BlockPos.ZERO.relative(up));
                assertTrue(log.is(Blocks.OAK_LOG)); assertEquals(right.getAxis(), log.getValue(BlockStateProperties.AXIS));
                assertTrue(geometry.states().get(BlockPos.ZERO.relative(front.getOpposite())).is(GTMachines.ITEM_IMPORT_BUS[GTValues.LV].getBlock()));
            }
        }
        assertTrue(source.states().get(anchor.east()).is(Blocks.IRON_BLOCK));
        assertThrows(IllegalArgumentException.class, () -> source.oriented(Direction.NORTH, Direction.SOUTH, false));
    }

    @Test void filteredWorldLayerSnapshotsCopyMutableKeysAndKeepAnOutOfLayerControllerAnchor() {
        var anchor = new BlockPos(3, 2, 1);
        var position = new BlockPos.MutableBlockPos(7, 4, 9);
        var blocks = new java.util.LinkedHashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        blocks.put(position, Blocks.GLASS.defaultBlockState()); blocks.put(BlockPos.ZERO, Blocks.AIR.defaultBlockState());
        var geometry = NativePreviewGeometry.fromStates(blocks, anchor);
        position.set(50, 50, 50); blocks.clear();
        assertEquals(Map.of(new BlockPos(7, 4, 9), Blocks.GLASS.defaultBlockState()), geometry.states());
        assertEquals(anchor, geometry.controllerPos());
        assertTrue(geometry.predicates().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> geometry.states().clear());
    }

    @Test void customRepeatedVisualShellsRetainNativeCandidatePredicatesAtEveryMaterialPosition() {
        var definition = GTMultiMachines.DISTILLATION_TOWER.get();
        var nativeShape = NativePreviewShapes.get(definition).getLast();
        var palette = new java.util.LinkedHashMap<net.minecraft.world.level.block.state.BlockState, Character>();
        nativeShape.states().values().forEach(state -> palette.computeIfAbsent(state, ignored -> (char) ('A' + palette.size())));
        var builder = NativePreviewGeometry.visualBuilder();
        palette.forEach((state, key) -> builder.where(key, state.hasBlockEntity() ? state : Blocks.GLASS.defaultBlockState()));
        int width = nativeShape.states().keySet().stream().mapToInt(BlockPos::getX).max().orElseThrow() + 1;
        int height = nativeShape.states().keySet().stream().mapToInt(BlockPos::getY).max().orElseThrow() + 1;
        int depth = nativeShape.states().keySet().stream().mapToInt(BlockPos::getZ).max().orElseThrow() + 1;
        for (int x = 0; x < width; x++) {
            var rows = new String[height];
            for (int y = 0; y < height; y++) {
                var row = new StringBuilder();
                for (int z = 0; z < depth; z++) {
                    var state = nativeShape.states().get(new BlockPos(x, y, z)); row.append(state == null ? ' ' : palette.get(state));
                }
                rows[y] = row.toString();
            }
            builder.aisle(rows);
        }
        var visual = builder.build(definition);
        assertEquals(nativeShape.controllerPos(), visual.controllerPos());
        assertEquals(nativeShape.states().size(), visual.predicates().size());
        assertTrue(visual.states().values().stream().anyMatch(state -> state.is(Blocks.GLASS)));
        assertTrue(visual.predicates().values().stream().flatMap(predicate -> predicate.expand().stream())
                .flatMap(predicate -> predicate.getCandidates().stream()).anyMatch(info -> !info.getBlockState().is(Blocks.GLASS)));
    }
}
