package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.machine.multiblock.SimulationMachine;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeSimulationGameTests {
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeBlueprintSlotFiltersPersistsAndCreatesTheSelectedVirtualController(GameTestHelper helper) {
        var pos = new BlockPos(4, 3, 4); var entry = NativeMachineFixtures.SIMULATION; helper.setBlock(pos, entry.getBlock());
        var machine = (SimulationMachine) helper.getBlockEntity(pos); machine.setFrontFacing(Direction.EAST);
        helper.assertTrue(machine.machineStorage.insertItem(0, Items.DIRT.getDefaultInstance(), false).getCount() == 1 && machine.isEmpty(), "Simulator accepted an ordinary item");
        var blueprint = GTMultiMachines.ELECTRIC_BLAST_FURNACE.asStack(2);
        helper.assertTrue(machine.machineStorage.insertItem(0, blueprint, false).getCount() == 1 && machine.getMachineStorageItem().getCount() == 1, "Simulator lost its single-controller slot limit");
        var virtual = machine.getRealController();
        helper.assertTrue(virtual.getDefinition() == GTMultiMachines.ELECTRIC_BLAST_FURNACE.get() && virtual.getFrontFacing() == Direction.EAST && virtual.getLevel() == helper.getLevel(), "Simulator did not create an oriented native controller");
        helper.assertTrue(virtual.getBlockPos().equals(machine.getBlockPos()) && helper.getBlockEntity(pos) == machine, "Simulator replaced the real world's entity with its virtual controller");
        machine.checkAndFormStructure(); helper.assertTrue(!machine.isFormed(), "Simulator formed the selected blueprint");
        var tag = machine.saveWithoutMetadata(helper.getLevel().registryAccess()); var state = helper.getBlockState(pos);
        helper.getLevel().removeBlockEntity(helper.absolutePos(pos)); var restored = entry.getBlockEntityType().create(helper.absolutePos(pos), state);
        helper.getLevel().setBlockEntity(restored); restored.loadAdditional(tag, helper.getLevel().registryAccess());
        var copy = (SimulationMachine) restored; helper.assertTrue(copy.getRealController().getDefinition() == GTMultiMachines.ELECTRIC_BLAST_FURNACE.get() && !copy.isFormed(), "Simulator blueprint was not restored from native SaveField data");
        copy.setMachineStorageItem(net.minecraft.world.item.ItemStack.EMPTY); helper.assertTrue(copy.getRealController() == copy, "Empty simulator retained its previous virtual controller");
        helper.succeed();
    }
}
