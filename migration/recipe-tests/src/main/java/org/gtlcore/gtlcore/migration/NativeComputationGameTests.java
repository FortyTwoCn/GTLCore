package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.machine.multiblock.electric.ComputationProviderMachine;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.*;
import com.gregtechceu.gtceu.api.multiblock.util.*;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeComputationGameTests {
    private static ComputationProviderMachine form(GameTestHelper helper, MachineEntry<MultiblockMachineDefinition> entry) {
        var blocks = new LinkedHashMap<BlockPos, BlockInfo>();
        AbstractStructureHelper.blockPattern(new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap(java.util.Map.of(0, 1))).populate(blocks, entry.get().getStructurePatterns().get("main").get(), null, Direction.NORTH, Direction.UP, false);
        BlockPos controller = null;
        for (var block : blocks.entrySet()) {
            var pos = new BlockPos(4, 3, 4).offset(block.getKey()); helper.setBlock(pos, block.getValue().getBlockState());
            if (block.getValue().getBlockState().is(entry.getBlock())) controller = pos;
        }
        var machine = (ComputationProviderMachine) helper.getBlockEntity(Objects.requireNonNull(controller));
        machine.setFrontFacing(Direction.NORTH); machine.checkAndFormStructure();
        helper.assertTrue(machine.isFormed() && machine.getParts().size() == 2, "Computation provider did not discover real native energy and input hatches");
        return machine;
    }
    private static NotifiableEnergyContainer energy(ComputationProviderMachine machine) { return machine.getTraitsFromParts(NotifiableEnergyContainer.class).getFirst(); }
    private static NotifiableItemStackHandler input(ComputationProviderMachine machine) { return machine.getTraitsFromParts(NotifiableItemStackHandler.class).getFirst(); }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void finiteProviderReservesCacheChecksMainframesAndRestoresNativeSaveFields(GameTestHelper helper) {
        var machine = form(helper, NativeComputationFixtures.FINITE);
        input(machine).setStackInSlot(0, NativeComputationFixtures.OPTICAL_MAINFRAME.asStack(8));
        helper.assertTrue(machine.getMaxCWUt() == 1024 && machine.canBridge(), "Native direct computation overloads rejected their own traversal");
        machine.totalCWU = 40;
        helper.assertTrue(machine.requestCWUt(30, true) == 30 && machine.totalCWU == 40 && machine.allocatedCWUt == 0, "Simulation consumed or reserved finite computation");
        helper.assertTrue(machine.requestCWUt(30, false) == 30 && machine.requestCWUt(30, false) == 10 && machine.requestCWUt(1, false) == 0,
                "Multiple requests reused already-reserved CWU");
        machine.tick(); helper.assertTrue(machine.totalCWU == 0 && machine.allocatedCWUt == 0, "Finite tick did not consume its exact allocation");
        var power = energy(machine); power.changeEnergy(power.getEnergyCapacity()); long before = power.getEnergyStored();
        helper.assertTrue(machine.requestCWUt(1024, true) == 1024 && power.getEnergyStored() == before && machine.totalCWU == 0, "Powered simulation changed energy or cache");
        helper.assertTrue(machine.requestCWUt(100, false) == 100 && power.getEnergyStored() < before && input(machine).getStackInSlot(0).getCount() == 8, "Finite generation did not consume energy while keeping its mainframes");
        machine.tick(); helper.assertTrue(machine.totalCWU == 924, "Finite generation lost the remaining computation buffer");
        var saved = machine.saveWithoutMetadata(helper.getLevel().registryAccess()); var pos = machine.getBlockPos(); var state = machine.getBlockState();
        machine.invalidateStructure("main"); helper.getLevel().removeBlockEntity(pos);
        var restored = (ComputationProviderMachine) NativeComputationFixtures.FINITE.getBlockEntityType().create(pos, state);
        helper.getLevel().setBlockEntity(restored); restored.loadAdditional(saved, helper.getLevel().registryAccess()); restored.checkAndFormStructure();
        helper.assertTrue(restored.isFormed() && restored.totalCWU == 924 && restored.getMaxCWUt() == 1024, "Native entity recreation lost finite CWU or hatch discovery");
        input(restored).setStackInSlot(0, ItemStack.EMPTY);
        helper.assertTrue(restored.getMaxCWUt() == 0 && restored.requestCWUt(1, false) == 0, "Removed mainframes left cached computation capacity");
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void infiniteProviderPaysPerRequestAndResumesAfterNativePowerPause(GameTestHelper helper) {
        var machine = form(helper, NativeComputationFixtures.INFINITE); var power = energy(machine);
        helper.assertTrue(machine.getMaxCWUt() == Integer.MAX_VALUE && machine.requestCWUt(5, true) == 0, "Unpowered infinite computation was available");
        power.changeEnergy(power.getEnergyCapacity()); long before = power.getEnergyStored();
        helper.assertTrue(before >= Integer.MAX_VALUE && machine.requestCWUt(5, true) == 5 && power.getEnergyStored() == before, "Infinite simulation consumed EU");
        int supplied = machine.requestCWUt(5, false);
        helper.assertTrue(supplied == 5 && power.getEnergyStored() == before - Integer.MAX_VALUE, "Infinite request supplied=" + supplied + " energy_before=" + before + " energy_after=" + power.getEnergyStored());
        var seen = new HashSet<com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider>();
        machine.requestCWUt(1, true, seen); helper.assertTrue(machine.requestCWUt(1, false, seen) == 0 && power.getEnergyStored() == before - Integer.MAX_VALUE, "Repeated optical traversal consumed computation");
        machine.setWorkingEnabled(false); machine.tick(); helper.assertTrue(machine.requestCWUt(1, false) == 0, "Paused provider continued allocating");
        machine.setWorkingEnabled(true); machine.tick(); helper.assertTrue(machine.requestCWUt(1, false) == 1, "Native power-button resume did not restore computation");
        machine.invalidateStructure("main"); helper.assertTrue(machine.requestCWUt(1, false) == 0 && machine.allocatedCWUt == 0, "Invalid structure retained live computation");
        helper.succeed();
    }
}
