package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.integration.ftb.NativeProspectorCoordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NativeProspectorCoordinatesTest {
    @Test void negativeWorldCoordinatesUseTheOpeningChunkAndPixelFloor() {
        var openingChunk = new ChunkPos(-2, -3);
        assertEquals(new BlockPos(-64, 0, -80), NativeProspectorCoordinates.point(openingChunk, 3, 0.9, 0.1).orElseThrow());
        assertEquals(new BlockPos(-1, 0, -17), NativeProspectorCoordinates.point(openingChunk, 3, 63.99, 63.99).orElseThrow());
        assertEquals(new BlockPos(15, 0, -1), NativeProspectorCoordinates.point(openingChunk, 3, 79.99, 79.99).orElseThrow());
        assertTrue(NativeProspectorCoordinates.point(openingChunk, 3, 80, 0).isEmpty());
    }
    @Test void mapBordersNonFiniteInputAndUnrepresentableWorldPositionsAreRejected() {
        var anchor = new ChunkPos(0, 0);
        for (double bad : new double[]{-0.1, 16, Double.NaN, Double.POSITIVE_INFINITY})
            assertTrue(NativeProspectorCoordinates.point(anchor, 1, bad, 0).isEmpty());
        assertTrue(NativeProspectorCoordinates.point(anchor, 0, 0, 0).isEmpty());
        assertTrue(NativeProspectorCoordinates.point(new ChunkPos(Integer.MAX_VALUE, 0), 1, 0, 0).isEmpty());
    }
    @Test void waypointNumberingToleratesUserNamesAndOverflowWithoutDeletingThem() {
        var names = List.of("prospect_point1", "prospect_point3", "prospect_pointcustom", "prospect_point999999999999999999999", "my_prospect_point4");
        assertEquals("prospect_point4", NativeProspectorCoordinates.nextWaypointName(names));
        assertFalse(NativeProspectorCoordinates.isGeneratedWaypoint(names.get(2)));
        assertFalse(NativeProspectorCoordinates.isGeneratedWaypoint(names.get(3)));
        assertFalse(NativeProspectorCoordinates.isGeneratedWaypoint(names.get(4)));
        assertEquals("prospect_point2", NativeProspectorCoordinates.nextWaypointName(List.of("prospect_point1", "prospect_point9223372036854775807")));
    }
}
