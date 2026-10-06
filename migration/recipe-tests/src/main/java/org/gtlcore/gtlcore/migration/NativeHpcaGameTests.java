package org.gtlcore.gtlcore.migration;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.api.multiblock.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.multiblock.util.*;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.machines.GTResearchMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.research.HPCAMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.MaintenanceHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.trait.hpca.HPCAComputationProviderTrait;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.*;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeHpcaGameTests {
    private static void power(HPCAMachine machine) {
        for (var part : machine.getParts()) {
            var energy = part.getTrait(NotifiableEnergyContainer.class);
            if (energy != null) energy.changeEnergy(energy.getEnergyCapacity());
            if (part instanceof MaintenanceHatchPartMachine maintenance)
                maintenance.setMaintenanceProblems(MaintenanceHatchPartMachine.NO_PROBLEMS);
        }
        machine.tick();
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativePoweredHpcaControllerUsesCoreCapacityAndKeepsDamageAfterPartReload(GameTestHelper helper) {
        var entry = GTResearchMachines.HIGH_PERFORMANCE_COMPUTING_ARRAY;
        var pattern = (BlockPattern) entry.get().getStructurePatterns().get("main").get();
        var repeats = new Int2IntOpenHashMap();
        for (int i = 0; i < pattern.getSlices().length; i++) repeats.put(i, pattern.getSlices()[i].getMinRepeats());
        var preview = AbstractStructureHelper.blockPattern(repeats);
        var computation = BlockInfo.fromBlockState(GTResearchMachines.HPCA_COMPUTATION_COMPONENT.getBlock().defaultBlockState());
        var energy = BlockInfo.fromBlockState(GTMachines.ENERGY_INPUT_HATCH[GTValues.ZPM].getBlock().defaultBlockState());
        for (var predicate : pattern.getPredicates().values()) for (var base : predicate.expand()) {
            if (base.getCandidates().stream().anyMatch(c -> c.getBlockState().is(GTResearchMachines.HPCA_EMPTY_COMPONENT.getBlock())))
                preview.getBlockPreferences().put(predicate, base, computation);
            if (base.getCandidates().stream().anyMatch(c -> c.getBlockState().is(GTMachines.ENERGY_INPUT_HATCH[GTValues.LV].getBlock())))
                preview.getBlockPreferences().put(predicate, base, energy);
        }
        var blocks = new LinkedHashMap<BlockPos, BlockInfo>(); preview.populate(blocks, pattern, null, Direction.NORTH, Direction.UP, false);
        var origin = new BlockPos(4, 4, 4); BlockPos controllerPos = null;
        for (var block : blocks.entrySet()) {
            var pos = origin.offset(block.getKey()); helper.setBlock(pos, block.getValue().getBlockState());
            if (block.getValue().getBlockState().is(entry.getBlock())) controllerPos = pos;
        }
        helper.assertTrue(controllerPos != null, "Native HPCA preview lost its controller");
        var machine = (HPCAMachine) helper.getBlockEntity(controllerPos); machine.setFrontFacing(Direction.NORTH); machine.checkAndFormStructure();
        helper.assertTrue(machine.isFormed() && machine.getTraitsFromParts(HPCAComputationProviderTrait.class).size() == 9, "Native HPCA did not form with nine computation parts");
        power(machine);
        helper.assertTrue(machine.getMaxCWUt(new HashSet<>()) == 72 && machine.requestCWUt(100, true, new HashSet<>()) == 72,
                "Powered native controller did not use Core's 8 CWU/t computation components");
        var trait = machine.getTraitsFromParts(HPCAComputationProviderTrait.class).getFirst(); var part = trait.getMachine(); trait.setDamaged(true);
        helper.assertTrue(machine.getMaxCWUt(new HashSet<>()) == 64, "Damaged native component continued providing computation");
        var saved = part.saveWithoutMetadata(helper.getLevel().registryAccess()); var pos = part.getBlockPos(); var state = part.getBlockState();
        machine.invalidateStructure("main"); helper.getLevel().removeBlockEntity(pos);
        var restored = GTResearchMachines.HPCA_COMPUTATION_COMPONENT.getBlockEntityType().create(pos, state);
        helper.getLevel().setBlockEntity(restored); restored.loadAdditional(saved, helper.getLevel().registryAccess());
        machine.checkAndFormStructure(); power(machine);
        helper.assertTrue(machine.isFormed() && machine.getParts().contains(restored) && restored.getTrait(HPCAComputationProviderTrait.class).isDamaged() && machine.getMaxCWUt(new HashSet<>()) == 64,
                "Reloading a native HPCA part lost its damage or controller computation state");
        restored.getTrait(HPCAComputationProviderTrait.class).setDamaged(false);
        helper.assertTrue(machine.getMaxCWUt(new HashSet<>()) == 72, "Repairing a native HPCA component did not restore its capacity");
        helper.succeed();
    }
}
