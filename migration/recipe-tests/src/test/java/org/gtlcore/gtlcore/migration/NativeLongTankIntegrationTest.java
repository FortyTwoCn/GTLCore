package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.machine.trait.NativeLongFluidTank;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.StorageMachine;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class NativeLongTankIntegrationTest {
    private static NativeLongFluidTank tank(IO io) {
        var definition = GTMultiMachines.VACUUM_FREEZER;
        var owner = new StorageMachine(new BlockEntityCreationInfo(definition.getBlockEntityType(), BlockPos.ZERO,
                definition.getBlock().defaultBlockState()), 64);
        return owner.attachTrait(new NativeLongFluidTank(2, Long.MAX_VALUE / 2, io));
    }

    @Test void exactCapacityComponentsAndPersistenceSurviveNativeIntViews() {
        var input = tank(IO.IN);
        long count = 9_007_199_254_740_993L;
        var water = new FluidStack(Fluids.WATER, 1);
        water.set(DataComponents.CUSTOM_NAME, Component.literal("purified"));
        input.longTank(0).setFluidAmount(water, count);
        assertEquals(Integer.MAX_VALUE, input.getFluidInTank(0).getAmount());
        assertEquals(count, input.gtlcore$getStoredAmount(0));
        assertEquals(0, input.longTank(0).fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE));
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var saved = input.getSyncDataHolder().serializeNBT(registries);
        var restored = tank(IO.IN);
        restored.getSyncDataHolder().deserializeNBT(registries, saved);
        assertEquals(count, restored.gtlcore$getStoredAmount(0));
        assertEquals(water.get(DataComponents.CUSTOM_NAME), restored.getFluidInTank(0).get(DataComponents.CUSTOM_NAME));
        assertEquals(1000, restored.longTank(0).drain(1000, FluidAction.SIMULATE).getAmount());
        assertEquals(count, restored.gtlcore$getStoredAmount(0));
        assertEquals(1000, restored.longTank(0).drain(1000, FluidAction.EXECUTE).getAmount());
        assertEquals(count - 1000, restored.gtlcore$getStoredAmount(0));
    }

    @Test void recipeSimulationAccountsForEarlierIngredientsAndExecutionNotifiesOnce() {
        var input = tank(IO.IN);
        long amount = 9_007_199_254_740_993L;
        input.longTank(0).setFluidAmount(new FluidStack(Fluids.WATER, 1), amount);
        var calls = new AtomicInteger();
        input.addChangedListener(calls::incrementAndGet);
        var left = new ArrayList<>(List.of(LongFluidIngredient.create(Fluids.WATER, amount - 7),
                SizedFluidIngredient.of(Fluids.WATER, 10)));
        input.handleRecipeInner(IO.IN, null, left, true);
        assertEquals(1, left.size());
        assertEquals(3, LongFluidIngredient.amount(left.getFirst()));
        assertEquals(amount, input.gtlcore$getStoredAmount(0));
        assertEquals(0, calls.get());
        left = new ArrayList<>(List.of(LongFluidIngredient.create(Fluids.WATER, amount - 7),
                SizedFluidIngredient.of(Fluids.WATER, 7)));
        assertTrue(input.handleRecipeInner(IO.IN, null, left, false).isEmpty());
        assertEquals(0, input.gtlcore$getStoredAmount(0));
        assertTrue(input.isEmpty());
        assertEquals(1, calls.get());
    }

    @Test void outputHonorsPerTankCapacityWithoutIntClampingOrLongOverflow() {
        var output = tank(IO.OUT);
        long capacity = output.longTank(0).getLongCapacity();
        var left = new ArrayList<>(List.of(LongFluidIngredient.create(Fluids.WATER, capacity + 123)));
        output.handleRecipeInner(IO.OUT, null, left, true);
        assertEquals(123, LongFluidIngredient.amount(left.getFirst()));
        assertTrue(output.isEmpty());
        left = new ArrayList<>(List.of(LongFluidIngredient.create(Fluids.WATER, capacity + 123)));
        output.handleRecipeInner(IO.OUT, null, left, false);
        assertEquals(capacity, output.gtlcore$getStoredAmount(0));
        assertEquals(0, output.gtlcore$getStoredAmount(1));
        assertEquals(123, LongFluidIngredient.amount(left.getFirst()));
        assertEquals(0, output.longTank(0).fillLong(new FluidStack(Fluids.WATER, 1), Long.MAX_VALUE, FluidAction.EXECUTE));
        assertThrows(IllegalArgumentException.class, () -> output.longTank(0).setFluidAmount(new FluidStack(Fluids.WATER, 1), -1));
    }
}
