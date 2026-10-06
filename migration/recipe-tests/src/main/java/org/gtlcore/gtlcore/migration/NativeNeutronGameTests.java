package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.data.GTLRecipeTypes;
import org.gtlcore.gtlcore.common.machine.multiblock.noenergy.NeutronActivatorMachine;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Production controller with fixture registration; the full Core registration is validated separately. */
@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public class NativeNeutronGameTests {
    private static NeutronActivatorMachine build(GameTestHelper helper) {
        for (int x = 2; x <= 4; x++) for (int y = 1; y <= 3; y++) helper.setBlock(new BlockPos(x, y, 3), Blocks.IRON_BLOCK);
        helper.setBlock(new BlockPos(3, 2, 3), NativeMachineFixtures.NEUTRON.getBlock());
        var machine = (NeutronActivatorMachine) helper.getBlockEntity(new BlockPos(3, 2, 3));
        machine.setFrontFacing(Direction.NORTH); machine.checkAndFormStructure();
        helper.assertTrue(machine.isFormed() && machine.getEfficiencyFactor() == Math.pow(.95, 4), "Native neutron pipe counter did not form");
        return machine;
    }

    private static void charge(GameTestHelper helper, NeutronActivatorMachine machine, int amount) {
        var saved = machine.saveWithoutMetadata(helper.getLevel().registryAccess()); saved.putInt("eV", amount);
        machine.loadAdditional(saved, helper.getLevel().registryAccess());
        helper.assertTrue(machine.getEV() == amount, "Neutron SaveField did not restore its charge");
    }

    private static NotifiableItemStackHandler inventory(NeutronActivatorMachine machine, IO io) {
        return machine.getTraits(NotifiableItemStackHandler.class).stream().filter(handler -> handler.getHandlerIO() == io).findFirst().orElseThrow();
    }

    private static GTRecipe recipe() {
        var recipe = GTLRecipeTypes.NEUTRON_ACTIVATOR_RECIPES.recipeBuilder("native_neutron_physical_cycle")
                .inputItems(Items.IRON_INGOT).outputItems(Items.IRON_NUGGET, 9).duration(5).build();
        recipe.data.putInt("ev_min", 0); recipe.data.putInt("ev_max", 1200); recipe.data.putInt("evt", 1);
        return recipe;
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void neutronFormationSaveReloadAndReformation(GameTestHelper helper) {
        var machine = build(helper); charge(helper, machine, 15_000_123);
        var saved = machine.saveWithoutMetadata(helper.getLevel().registryAccess());
        helper.setBlock(new BlockPos(3, 2, 3), Blocks.AIR);
        helper.setBlock(new BlockPos(3, 2, 3), NativeMachineFixtures.NEUTRON.getBlock());
        var restored = (NeutronActivatorMachine) helper.getBlockEntity(new BlockPos(3, 2, 3));
        restored.loadAdditional(saved, helper.getLevel().registryAccess()); restored.checkAndFormStructure();
        helper.assertTrue(restored.getEV() == 15_000_123 && restored.isFormed() && restored.getEfficiencyFactor() == Math.pow(.95, 4),
                "Neutron block replacement lost charge or native matched pipe state");
        helper.setBlock(new BlockPos(2, 1, 3), Blocks.AIR); restored.checkAndFormStructure();
        helper.assertTrue(!restored.isFormed() && restored.getEfficiencyFactor() == 1, "Neutron invalidation retained the pipe speed");
        helper.setBlock(new BlockPos(2, 1, 3), Blocks.IRON_BLOCK); restored.checkAndFormStructure();
        helper.assertTrue(restored.isFormed() && restored.getEfficiencyFactor() == Math.pow(.95, 4), "Neutron repair did not reform");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void neutronEnergyWaitPreservesInputProgressAndResumesOneCompleteCycle(GameTestHelper helper) {
        var machine = build(helper);
        helper.runAfterDelay(2, () -> {
            charge(helper, machine, 1500);
            var input = inventory(machine, IO.IN); var output = inventory(machine, IO.OUT);
            input.storage.setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
            var source = recipe(); var logic = machine.getRecipeLogic();
            helper.assertTrue(logic.checkMatchedRecipeAvailable(source), "Neutron setup failed: " + logic.getBestFailureReason());
            logic.serverTick();
            helper.assertTrue(logic.getProgress() == 1 && machine.getEV() == 500 && input.getStackInSlot(0).isEmpty(),
                    "Accepted neutron tick failed to consume exactly one input and 1000 eV");
            for (int tick = 0; tick < 6; tick++) logic.serverTick();
            helper.assertTrue(logic.getProgress() == 1 && machine.getEV() == 500 && output.getStackInSlot(0).isEmpty(),
                    "Insufficient neutron energy reset/advanced progress, consumed energy or emitted output");
            charge(helper, machine, 10_000);
            helper.assertTrue(logic.getProgress() == 1 && input.getStackInSlot(0).isEmpty(), "Neutron recharge lost pending work");
            logic.setSuspendAfterFinish(true);
            // Native waiting retries have their own cadence; successful recipe ticks are counted by eV consumption.
            for (int tick = 0; tick < 40 && !logic.isSuspend(); tick++) logic.serverTick();
            helper.assertTrue(logic.isSuspend() && output.getStackInSlot(0).getCount() == 9 && machine.getEV() == 7000,
                    "Neutron resume did not finish exactly one four-tick recipe: status=" + logic.getStatus() +
                    " progress=" + logic.getProgress() + " duration=" + logic.getDuration() + " eV=" + machine.getEV() +
                    " output=" + output.getStackInSlot(0).getCount());
            helper.assertTrue(source.duration == 5 && source.inputs.size() == 1, "Neutron modifier mutated its source");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void neutronRejectedWorkingCallbackDoesNotDrainCharge(GameTestHelper helper) {
        var machine = build(helper);
        helper.runAfterDelay(2, () -> {
            charge(helper, machine, 1_000_000);
            inventory(machine, IO.IN).storage.setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
            var logic = machine.getRecipeLogic();
            helper.assertTrue(logic.checkMatchedRecipeAvailable(recipe()), "Rejected-working fixture failed to start");
            NativeMachineFixtures.NEUTRON_BLOCKED.add(machine);
            try {
                logic.serverTick();
                helper.assertTrue(machine.getEV() == 1_000_000 && logic.getProgress() == 0 && inventory(machine, IO.OUT).getStackInSlot(0).isEmpty(),
                        "Rejected native working callback drained charge, advanced progress or emitted output: eV=" + machine.getEV() +
                        " progress=" + logic.getProgress() + " callback=" + machine.getDefinition().getOnWorking().test(machine));
            } finally { NativeMachineFixtures.NEUTRON_BLOCKED.remove(machine); }
            helper.succeed();
        });
    }
}
