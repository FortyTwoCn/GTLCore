package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.machine.trait.FissionReactorThermal;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeFissionThermalTest {
    @Test void nativeCountedPositionsKeepUndirectedAdjacencyAndExcludeOutsideBlocks() {
        var origin = BlockPos.ZERO;
        assertEquals(3, FissionReactorThermal.adjacentPairs(new long[] { origin.asLong(), origin.east().asLong(), origin.above().asLong(), origin.south().asLong(), origin.asLong(), origin.west(4).asLong() }));
        assertEquals(0, FissionReactorThermal.adjacentPairs(new long[0]));
        assertEquals(32, FissionReactorThermal.coolerCapacity(6, 7));
    }
    @Test void originalThermalRequirementsUseExactLongIntermediatesAndBoundedHeat() {
        assertEquals(12, FissionReactorThermal.requiredCooling(30, 2, 300));
        assertEquals(392_000_000_000L, FissionReactorThermal.requiredCooling(1_500_000_000, 392, 1000));
        assertEquals(Long.MAX_VALUE, FissionReactorThermal.requiredCooling(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(0, FissionReactorThermal.requiredCooling(0, 2, 1500));
        assertEquals(Integer.MAX_VALUE, FissionReactorThermal.heated(1500, Integer.MAX_VALUE, 392));
        assertEquals(360, FissionReactorThermal.heated(300, 30, 2));
    }
    @Test void originalCoolantRatiosStayExactAndOverflowIsExplicit() {
        assertEquals(9600, FissionReactorThermal.waterAmount(12));
        assertEquals(240, FissionReactorThermal.sodiumPotassiumAmount(12));
        assertEquals(1536000, FissionReactorThermal.steamAmount(12, 373));
        assertEquals(1536000, FissionReactorThermal.steamAmount(12, 826));
        assertEquals((long) (12 * 800 * (160 / Math.pow(1.4, 1))), FissionReactorThermal.steamAmount(12, 372));
        assertEquals(0, FissionReactorThermal.steamAmount(12, 298));
        assertEquals(9_007_199_254_784_000L, FissionReactorThermal.steamAmount(70_368_744_178L, 373));
        assertThrows(ArithmeticException.class, () -> FissionReactorThermal.steamAmount(Long.MAX_VALUE, 373));
    }
    @Test void nativeRedstoneCannotEmitOutOfRangeSignalsOrDivideByZero() {
        assertEquals(15, FissionReactorThermal.signal(298)); assertEquals(15, FissionReactorThermal.signal(1500));
        assertEquals(14, FissionReactorThermal.signal(1501)); assertEquals(0, FissionReactorThermal.signal(Integer.MAX_VALUE));
        assertEquals(15, FissionReactorThermal.signal(0));
    }
}
