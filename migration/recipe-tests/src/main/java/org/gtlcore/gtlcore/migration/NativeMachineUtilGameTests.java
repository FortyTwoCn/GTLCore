package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.utils.*;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.StorageMachine;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.*;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.part.EnergyHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.trait.ProgrammableCircuitSlotTrait;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;
import java.util.concurrent.*;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeMachineUtilGameTests {
    private static StorageMachine build(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 2, 3), GTMachines.ENERGY_INPUT_HATCH[2].getBlock());
        helper.setBlock(new BlockPos(3, 2, 3), NativeMachineFixtures.AUXILIARY_IO.getBlock());
        helper.setBlock(new BlockPos(4, 2, 3), GTMachines.ENERGY_INPUT_HATCH[2].getBlock());
        var machine = (StorageMachine) helper.getBlockEntity(new BlockPos(3, 2, 3));
        machine.setFrontFacing(Direction.NORTH); machine.setWorkingEnabled(false); machine.checkAndFormStructure();
        helper.assertTrue(machine.isFormed(), "Auxiliary IO controller failed to form");
        var core = (IRecipeCapabilityMachine) machine; core.setDistinct(true); core.upDate(); return machine;
    }
    private static ItemStack item(Item item, int count, String name) {
        var stack = new ItemStack(item, count); stack.set(DataComponents.CUSTOM_NAME, Component.literal(name)); return stack;
    }
    private static FluidStack water(int amount, String name) {
        var stack = new FluidStack(Fluids.WATER, amount); stack.set(DataComponents.CUSTOM_NAME, Component.literal(name)); return stack;
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeAuxiliaryIOChecksWithoutConsumptionAndKeepsComponentInputsAndBlockedOutputs(GameTestHelper helper) {
        var machine = build(helper);
        var in = machine.getTraits(NotifiableItemStackHandler.class).stream().filter(t -> t.getHandlerIO() == IO.IN).findFirst().orElseThrow();
        var out = machine.getTraits(NotifiableItemStackHandler.class).stream().filter(t -> t.getHandlerIO() == IO.OUT).findFirst().orElseThrow();
        in.storage.setStackInSlot(0, item(Items.IRON_INGOT, 5, "auxiliary")); in.storage.setStackInSlot(1, new ItemStack(Items.IRON_INGOT, 10));
        helper.assertTrue(MachineIO.notConsumableItem(machine, item(Items.IRON_INGOT, 4, "auxiliary")) && in.getStackInSlot(0).getCount() == 5, "Auxiliary item predicate consumed inputs");
        helper.assertTrue(!MachineIO.inputItem(machine, item(Items.IRON_INGOT, 6, "auxiliary")) && in.getStackInSlot(0).getCount() == 5 && in.getStackInSlot(1).getCount() == 10, "Rejected auxiliary input consumed stock or ignored components");
        helper.assertTrue(MachineIO.inputItem(machine, item(Items.IRON_INGOT, 4, "auxiliary")) && in.getStackInSlot(0).getCount() == 1 && in.getStackInSlot(1).getCount() == 10, "Auxiliary item input consumed the wrong component key");
        helper.assertTrue(MachineIO.outputItem(machine, item(Items.GOLD_INGOT, 64, "output")) && !MachineIO.outputItem(machine, item(Items.GOLD_INGOT, 1, "output")) && out.getStackInSlot(0).getCount() == 64, "Blocked auxiliary item output was partially applied");
        var fluidIn = machine.getTraits(NotifiableFluidTank.class).stream().filter(t -> t.getHandlerIO() == IO.IN).findFirst().orElseThrow().getStorages()[0];
        var fluidOut = machine.getTraits(NotifiableFluidTank.class).stream().filter(t -> t.getHandlerIO() == IO.OUT).findFirst().orElseThrow().getStorages()[0];
        fluidIn.setFluid(water(800, "auxiliary"));
        helper.assertTrue(!MachineIO.inputFluid(machine, water(1, "other")) && !MachineIO.inputFluid(machine, water(900, "auxiliary")) && fluidIn.getFluidAmount() == 800, "Rejected auxiliary fluid input changed stock or ignored components");
        helper.assertTrue(MachineIO.inputFluid(machine, water(300, "auxiliary")) && fluidIn.getFluidAmount() == 500, "Auxiliary component fluid input did not consume its exact amount");
        helper.assertTrue(MachineIO.outputFluid(machine, water(900, "output")) && !MachineIO.outputFluid(machine, water(200, "output")) && fluidOut.getFluidAmount() == 900, "Blocked auxiliary fluid output lost a partial remainder");
        var circuit = machine.getTrait(ProgrammableCircuitSlotTrait.class); circuit.setCurrentCircuit(0);
        helper.assertTrue(MachineIO.notConsumableCircuit(machine, 0) && !MachineIO.notConsumableCircuit(machine, 1) && circuit.getCurrentCircuit() == 0, "Auxiliary circuit predicate changed or ignored circuit zero"); helper.succeed();
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeAuxiliaryEnergyRejectsInsufficientAndInvalidBudgetsAndServerWorkersReadServerRecipes(GameTestHelper helper) {
        var machine = build(helper); var hatch = (EnergyHatchPartMachine) helper.getBlockEntity(new BlockPos(2, 2, 3)); hatch.energyContainer.addEnergy(64);
        helper.assertTrue(!MachineIO.inputEU(machine, 128) && machine.getEnergyContainer().getEnergyStored() == 64, "Rejected auxiliary EU request consumed partial energy");
        helper.assertTrue(MachineIO.inputEU(machine, 32) && machine.getEnergyContainer().getEnergyStored() == 32, "Auxiliary EU request was not consumed once");
        helper.assertTrue(!MachineIO.inputEU(machine, -32) && MachineIO.inputEU(machine, 0) && machine.getEnergyContainer().getEnergyStored() == 32, "Invalid or zero auxiliary EU budget changed energy");
        helper.assertTrue(!MachineIO.inputEU(machine, 8_589_934_592L) && machine.getEnergyContainer().getEnergyStored() == 32, "Auxiliary energy budget was truncated to int");
        try (var executor = Executors.newSingleThreadExecutor()) {
            try { helper.assertTrue(executor.submit(Registries::getRecipeManager).get(2, TimeUnit.SECONDS) == helper.getLevel().getRecipeManager(), "Dedicated server worker used a client recipe manager"); }
            catch (Exception exception) { throw new IllegalStateException("Native server recipe manager lookup failed", exception); }
        }
        helper.succeed();
    }
}
