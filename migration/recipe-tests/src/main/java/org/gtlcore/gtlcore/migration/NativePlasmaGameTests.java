package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativePlasmaGameTests {
    private static final BlockPos POS = new BlockPos(3, 2, 3);

    private static WorkableElectricMultiblockMachine generator(GameTestHelper helper, boolean advanced) {
        var entry = advanced ? NativeMachineFixtures.ADVANCED_PLASMA_GENERATOR : NativeMachineFixtures.PLASMA_GENERATOR;
        helper.setBlock(new BlockPos(2, 2, 3), GTMachines.ENERGY_OUTPUT_HATCH[GTValues.HV].getBlock());
        helper.setBlock(new BlockPos(4, 2, 3), GTMachines.ENERGY_OUTPUT_HATCH[GTValues.HV].getBlock());
        helper.setBlock(POS, entry.getBlock());
        var machine = (WorkableElectricMultiblockMachine) helper.getBlockEntity(POS);
        machine.setFrontFacing(Direction.NORTH); machine.checkAndFormStructure();
        helper.assertTrue(machine.isFormed(), "Plasma fixture did not form with real native dynamo hatches");
        return machine;
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void plasmaBoostRemainsInInventoryUntilSetupAndSurvivesControllerReplacement(GameTestHelper helper) {
        var machine = generator(helper, false);
        var tanks = machine.getTraits(NotifiableFluidTank.class);
        var inputs = tanks.get(0).getStorages(); var output = tanks.get(1).getStorages()[0];
        inputs[0].setFluid(GTMaterials.Hydrogen.getFluid(32)); inputs[1].setFluid(new FluidStack(Fluids.WATER, 2));
        output.setFluid(GTMaterials.Steam.getFluid(5000));
        var source = GTRecipeTypes.PLASMA_GENERATOR_FUELS.recipeBuilder("native_plasma_cycle")
                .inputFluids(GTMaterials.Hydrogen.getFluid(1)).outputFluids(GTMaterials.Steam.getFluid(100)).duration(16).EUt(-32).build();
        for (int preview = 0; preview < 3; preview++) {
            helper.assertTrue(machine.fullModifyRecipe(source) == null, "Full output accepted plasma fuel");
            helper.assertTrue(inputs[0].getFluidAmount() == 32 && inputs[1].getFluidAmount() == 2,
                    "A rejected recipe preview consumed fuel or plasma");
        }
        output.setFluid(FluidStack.EMPTY);
        helper.assertTrue(machine.getRecipeLogic().checkMatchedRecipeAvailable(source), "Feasible plasma generator did not start");
        helper.assertTrue(inputs[0].getFluidAmount() == 16 && inputs[1].getFluidAmount() == 1 &&
                        IGTRecipe.of(machine.getRecipeLogic().getLastRecipe()).getRealParallels() == 16,
                "Native setup did not consume one plasma per accepted run");
        machine.getRecipeLogic().serverTick();
        helper.assertTrue(machine.getRecipeLogic().getProgress() == 1, "Native plasma generation did not tick");
        helper.assertTrue(inputs[1].getFluidAmount() == 1, "The first working tick consumed setup plasma again");
        int duration = machine.getRecipeLogic().getDuration();
        long energyPerTick = machine.getRecipeLogic().getLastRecipe().getOutputEUt().getTotalEU();
        helper.assertTrue((long) duration * energyPerTick == 16 * 16 * 32, "Native OC changed total plasma generation");
        long generated = machine.getEnergyContainer().getEnergyStored();
        var registries = helper.getLevel().registryAccess();
        var saved = machine.saveWithoutMetadata(registries);
        var replacement = (WorkableElectricMultiblockMachine) NativeMachineFixtures.PLASMA_GENERATOR.getBlockEntityType()
                .create(machine.getBlockPos(), machine.getBlockState());
        var world = helper.getLevel(); var absolute = machine.getBlockPos();
        world.removeBlockEntity(absolute); replacement.loadAdditional(saved, registries); world.setBlockEntity(replacement);
        helper.assertTrue(replacement.getTraits(NotifiableFluidTank.class).getFirst().getStorages()[1].getFluidAmount() == 1,
                "Native SaveField restoration changed the remaining plasma");
        replacement.setFrontFacing(Direction.NORTH); replacement.checkAndFormStructure();
        helper.assertTrue(replacement.isFormed() && replacement.getRecipeLogic().getProgress() == 1,
                "Pending plasma recipe was lost when recreating the controller");
        helper.assertTrue(replacement.getTraits(NotifiableFluidTank.class).getFirst().getStorages()[1].getFluidAmount() == 1,
                "Native reformation changed the remaining plasma");
        var logic = replacement.getRecipeLogic(); logic.setSuspendAfterFinish(true);
        for (int tick = 1; tick < duration; tick++) logic.serverTick();
        var restored = replacement.getTraits(NotifiableFluidTank.class);
        helper.assertTrue(logic.isSuspend() && restored.get(0).getStorages()[1].getFluidAmount() == 1 &&
                        restored.get(1).getStorages()[0].getFluidAmount() == 1600 &&
                        replacement.getEnergyContainer().getEnergyStored() == generated + (duration - 1) * energyPerTick,
                "Pending plasma recipe changed after reload: status=" + logic.getStatus() + " progress=" + logic.getProgress() +
                        " plasma=" + restored.get(0).getStorages()[1].getFluidAmount() + " output=" + restored.get(1).getStorages()[0].getFluidAmount() +
                        " energy=" + replacement.getEnergyContainer().getEnergyStored() + " expected=" + (generated + (duration - 1) * energyPerTick) +
                        " reason=" + logic.getBestFailureReason());
        helper.assertTrue(source.getInputContents(com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability.CAP).size() == 1 &&
                source.getOutputEUt().getTotalEU() == 32, "The registered source recipe was changed");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void advancedPlasmaConsumesEachBoosterOnceAndDoesNotRepeatItPerTick(GameTestHelper helper) {
        var machine = generator(helper, true);
        var inputs = machine.getTraits(NotifiableFluidTank.class).getFirst().getStorages();
        inputs[0].setFluid(GTMaterials.Hydrogen.getFluid(16)); inputs[1].setFluid(new FluidStack(Fluids.WATER, 2));
        inputs[2].setFluid(new FluidStack(Fluids.LAVA, 3));
        var source = GTRecipeTypes.PLASMA_GENERATOR_FUELS.recipeBuilder("native_advanced_plasma_cycle")
                .inputFluids(GTMaterials.Hydrogen.getFluid(1)).duration(16).EUt(-32).build();
        helper.assertTrue(machine.getRecipeLogic().checkMatchedRecipeAvailable(source), "Advanced plasma generator did not start");
        helper.assertTrue(inputs[0].getFluidAmount() == 0 && inputs[1].getFluidAmount() == 1 && inputs[2].getFluidAmount() == 2,
                "Advanced setup did not retain the original combined booster policy");
        var logic = machine.getRecipeLogic(); logic.setSuspendAfterFinish(true);
        for (int tick = 0; tick < 16; tick++) logic.serverTick();
        helper.assertTrue(logic.isSuspend() && inputs[1].getFluidAmount() == 1 && inputs[2].getFluidAmount() == 2 &&
                machine.getEnergyContainer().getEnergyStored() == 16 * 512,
                "Native working ticks consumed setup-only boosters again or emitted incorrect energy");
        helper.succeed();
    }
}
