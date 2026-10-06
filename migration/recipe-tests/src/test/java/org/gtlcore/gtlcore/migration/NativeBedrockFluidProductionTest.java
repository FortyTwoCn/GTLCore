package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.machine.trait.BedrockFluidProduction;
import com.gregtechceu.gtceu.api.data.worldgen.BiomeWeightModifier;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.BedrockFluidDefinition;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.BedrockFluidVeinSavedData;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.FluidVeinWorldEntry;
import net.minecraft.core.Holder;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class NativeBedrockFluidProductionTest {
    private static FluidVeinWorldEntry vein(int yield, int remaining, int depleted) {
        var definition = new BedrockFluidDefinition(1, yield, yield, 1, 100, depleted,
                Fluids.WATER, BiomeWeightModifier.EMPTY, Set.of());
        return new FluidVeinWorldEntry(Holder.direct(definition), yield, remaining);
    }

    @Test void nativeVeinProductionKeepsExactQuantitiesBeyondIntAndAvoidsYieldTimesOperationsOverflow() {
        var vein = vein(10_000_001, BedrockFluidVeinSavedData.MAXIMUM_VEIN_OPERATIONS, 5);
        assertEquals(2_560_000_256L, BedrockFluidProduction.amount(vein, 256, false));
        assertEquals(3_840_000_384L, BedrockFluidProduction.amount(vein, 256, true));
        var largest = vein(Integer.MAX_VALUE, BedrockFluidVeinSavedData.MAXIMUM_VEIN_OPERATIONS, 5);
        assertEquals(824_633_720_448L, BedrockFluidProduction.amount(largest, 256, true));
    }

    @Test void depletedYieldFloorsAndOriginalIntegerRoundingPrecedeRigAndOverclockMultipliers() {
        assertEquals(5_120L, BedrockFluidProduction.amount(vein(175, 0, 20), 256, false));
        assertEquals(5_120L, BedrockFluidProduction.amount(vein(175, 1_000, 20), 256, false));
        assertEquals(130_816L, BedrockFluidProduction.amount(vein(1001, 51_099, 20), 256, false));
        assertEquals(4L, BedrockFluidProduction.amount(vein(3, 100_000, 0), 1, true));
    }

    @Test void missingNativeDepositsProduceNoPhantomRecipes() {
        assertEquals(0, BedrockFluidProduction.amount(null, 256, false));
        assertNull(BedrockFluidProduction.recipe(null, 256, false, 2));
        var empty = new FluidVeinWorldEntry(null, 10_000_001, 100_000);
        assertEquals(0, BedrockFluidProduction.amount(empty, 256, true));
        assertNull(BedrockFluidProduction.recipe(empty, 256, true, 2));
    }
}
