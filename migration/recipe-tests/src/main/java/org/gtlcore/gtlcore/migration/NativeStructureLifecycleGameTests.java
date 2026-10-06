package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.pattern.INewMultiblockWorldSavedData;
import org.gtlcore.gtlcore.api.gui.preview.NativePreviewShapes;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.multiblock.MultiblockWorldSavedData;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.api.block.property.GTBlockStateProperties;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeStructureLifecycleGameTests {
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeVisualUpdatesKeepFormationWhileHatchReplacementInvalidatesAndRepairAutomaticallyRebinds(GameTestHelper helper) {
        var definition = NativeTerminalFixtures.LIFECYCLE.get();
        var origin = helper.absolutePos(new BlockPos(4, 3, 4));
        var geometry = NativePreviewShapes.get(definition).getFirst().oriented(Direction.NORTH, Direction.UP, false);
        geometry.states().forEach((pos, state) -> helper.getLevel().setBlockAndUpdate(origin.offset(pos), state));
        var controller = (MultiblockControllerMachine) helper.getLevel().getBlockEntity(origin);
        controller.checkAndFormStructure(); helper.assertTrue(controller.isFormed(), "Native lifecycle fixture did not form");
        var live = controller.getDefaultPatternState();
        var coil = geometry.states().entrySet().stream().filter(entry -> entry.getValue().is(GTBlocks.COIL_CUPRONICKEL.get()))
                .map(entry -> origin.offset(entry.getKey())).findFirst().orElseThrow();
        var hatchPos = geometry.states().entrySet().stream().filter(entry -> entry.getValue().is(GTMachines.FLUID_EXPORT_HATCH[1].getBlock()))
                .map(entry -> origin.offset(entry.getKey())).findFirst().orElseThrow();
        var info = live.getCache().get(coil.asLong());
        var data = MultiblockWorldSavedData.getOrCreate(helper.getLevel());
        var snapshot = ((INewMultiblockWorldSavedData) data).getControllersInChunk(new ChunkPos(coil));
        helper.assertTrue(snapshot.contains(live), "Native chunk index omitted formed structure");
        try { snapshot.clear(); helper.fail("Chunk index exposed mutable owned state"); }
        catch (UnsupportedOperationException expected) {}
        helper.getLevel().setBlockAndUpdate(coil, helper.getLevel().getBlockState(coil).setValue(GTBlockStateProperties.ACTIVE, true));
        var hatch = (FluidHatchPartMachine) helper.getLevel().getBlockEntity(hatchPos); hatch.setPaintingColor(0x55AA88);
        helper.assertTrue(controller.isFormed() && live.getCache().get(coil.asLong()) == info,
                "Active/model-only update rebuilt the accepted native structure");
        helper.assertTrue(hatch.swapIO(), "Native export hatch could not change IO");
        var inputHatch = (FluidHatchPartMachine) helper.getLevel().getBlockEntity(hatchPos);
        helper.assertTrue(!controller.isFormed() && !live.isFormed() && live.hasErrors()
                        && controller.getParts().isEmpty() && !hatch.hasController(origin) && !inputHatch.hasController(origin),
                "Replacing a required output hatch retained formation or an invalid part binding");
        // Native GT also indexes failed checks: changes at the failed position must trigger automatic repair.
        helper.assertTrue(live.getCache().containsKey(hatchPos.asLong())
                        && ((INewMultiblockWorldSavedData) data).getControllersInChunk(new ChunkPos(hatchPos)).contains(live),
                "Invalid native structure lost its repair watch");
        helper.getLevel().setBlockAndUpdate(hatchPos, GTMachines.FLUID_EXPORT_HATCH[1].getBlock().defaultBlockState());
        var repairedHatch = (FluidHatchPartMachine) helper.getLevel().getBlockEntity(hatchPos);
        helper.assertTrue(controller.isFormed() && live.isFormed() && !live.hasErrors()
                        && controller.getParts().contains(repairedHatch) && repairedHatch.hasController(origin)
                        && !controller.getParts().contains(hatch) && !controller.getParts().contains(inputHatch)
                        && ((INewMultiblockWorldSavedData) data).getControllersInChunk(new ChunkPos(coil)).contains(live),
                "Native repaired structure did not automatically rediscover, bind and index its replacement hatch");
        helper.getLevel().setBlockAndUpdate(origin, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        helper.assertTrue(!repairedHatch.hasController(origin)
                        && data.mapping.values().stream().noneMatch(states -> states.contains(live))
                        && data.chunkPosMapping.values().stream().noneMatch(states -> states.contains(live)),
                "Removing the native controller retained its part bindings or structure index");
        helper.assertTrue(snapshot.contains(live) && data.chunkPosMapping.values().stream().noneMatch(java.util.Set::isEmpty),
                "Native chunk snapshot changed or removal leaked empty sets");
        helper.succeed();
    }
}
