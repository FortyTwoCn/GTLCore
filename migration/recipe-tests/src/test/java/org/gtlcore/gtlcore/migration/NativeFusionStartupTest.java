package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.machine.trait.IFusionHeatTransaction;
import org.gtlcore.gtlcore.api.recipe.NativeFusionStartup;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.api.recipe.ActionResult;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.FusionReactorMachine;
import com.gregtechceu.gtceu.api.GTValues;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class NativeFusionStartupTest {
    private static NotifiableEnergyContainer energy(long capacity, long stored) {
        var energy = new NotifiableEnergyContainer(capacity, 0, 0, 0, 0); energy.setEnergyStored(stored); return energy;
    }
    private static GTRecipe recipe(long required) {
        return GTRecipeTypes.FUSION_RECIPES.recipeBuilder("native_pure_fusion_modifier").duration(20).EUt(32)
                .addData("eu_to_start", required).build();
    }

    @Test void actualNativeFusionModifierCanBeEvaluatedRepeatedlyWithoutChargingHeatOrMutatingItsRecipe() {
        var entry = GTMultiMachines.FUSION_REACTOR[GTValues.LuV];
        var machine = (FusionReactorMachine) entry.getBlockEntityType().create(BlockPos.ZERO, entry.getBlock().defaultBlockState());
        var fusion = (IFusionHeatTransaction) machine;
        fusion.gtlcore$getFusionEnergy().resetBasicInfo(1000, 0, 0, 0, 0);
        fusion.gtlcore$getFusionEnergy().setEnergyStored(300);
        var source = recipe(200);
        for (int attempt = 0; attempt < 20; attempt++) assertNotNull(FusionReactorMachine.recipeModifier(machine, source).apply(source));
        assertEquals(0, fusion.gtlcore$getFusionHeat()); assertEquals(300, fusion.gtlcore$getFusionEnergy().getEnergyStored());
        assertEquals(20, source.duration); assertEquals(32, source.getInputEUt().getTotalEU());
        assertNull(FusionReactorMachine.recipeModifier(machine, recipe(1001)).apply(recipe(1001)));
        assertNull(FusionReactorMachine.recipeModifier(machine, recipe(301)).apply(recipe(301)));
        assertNull(FusionReactorMachine.recipeModifier(machine, recipe(-1)).apply(recipe(-1)));
        assertEquals(300, fusion.gtlcore$getFusionEnergy().getEnergyStored());
    }

    @Test void nativeInputFailureAndExceptionsRefundTheHeatReservationBeforeCommittingHeat() {
        var energy = energy(1000, 300); var committed = new AtomicLong(); var calls = new AtomicInteger();
        var failure = ActionResult.fail(Component.literal("missing physical input"), null, IO.IN);
        assertSame(failure, NativeFusionStartup.run(energy, 50, 200, committed::set, () -> { calls.incrementAndGet(); return failure; }));
        assertEquals(300, energy.getEnergyStored()); assertEquals(0, committed.get()); assertEquals(1, calls.get());
        assertThrows(IllegalStateException.class, () -> NativeFusionStartup.run(energy, 50, 200, committed::set,
                () -> { throw new IllegalStateException("physical input exception"); }));
        assertEquals(300, energy.getEnergyStored()); assertEquals(0, committed.get());
    }

    @Test void successfulNativeIoCommitsOnlyTheHeatDifferenceAndAlreadyPaidHeatIsFree() {
        var energy = energy(1000, 300); var heat = new AtomicLong(50);
        assertTrue(NativeFusionStartup.run(energy, heat.get(), 200, heat::set, () -> ActionResult.SUCCESS).isSuccess());
        assertEquals(150, energy.getEnergyStored()); assertEquals(200, heat.get());
        for (int tick = 0; tick < 20; tick++) assertTrue(NativeFusionStartup.run(energy, heat.get(), 200, heat::set, () -> ActionResult.SUCCESS).isSuccess());
        assertEquals(150, energy.getEnergyStored()); assertEquals(200, heat.get());
    }

    @Test void nativeMaximumEnergyAndMalformedStartupDataAreBoundedWithoutExecutingUnpaidInputs() {
        var energy = energy(Long.MAX_VALUE, Long.MAX_VALUE); var heat = new AtomicLong();
        assertTrue(NativeFusionStartup.run(energy, 0, Long.MAX_VALUE, heat::set, () -> ActionResult.SUCCESS).isSuccess());
        assertEquals(0, energy.getEnergyStored()); assertEquals(Long.MAX_VALUE, heat.get());
        assertEquals(Long.MAX_VALUE, NativeFusionStartup.missingHeat(Long.MIN_VALUE, Long.MAX_VALUE));
        var calls = new AtomicInteger();
        assertFalse(NativeFusionStartup.run(energy, 0, 1, heat::set, () -> { calls.incrementAndGet(); return ActionResult.SUCCESS; }).isSuccess());
        assertFalse(NativeFusionStartup.run(energy, 0, -1, heat::set, () -> { calls.incrementAndGet(); return ActionResult.SUCCESS; }).isSuccess());
        assertEquals(0, calls.get());
        var malformed = recipe(1); malformed.data.putString("eu_to_start", "1");
        assertEquals(-1, NativeFusionStartup.requiredHeat(malformed));
    }
}
