package org.gtlcore.gtlcore.migration;

import com.gtladd.gtladditions.common.machine.*;
import com.gtladd.gtladditions.common.machine.hatch.*;
import com.gtladd.gtladditions.utils.CloudNetworkManager;
import com.gtladd.gtladditions.api.gui.widget.CloudComputationMonitorOverviewPage;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.*;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.*;
import com.gregtechceu.gtceu.api.multiblock.util.*;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.item.GTDataComponents;
import com.gregtechceu.gtceu.common.data.machines.GTResearchMachines;
import com.gregtechceu.gtceu.utils.ResearchManager;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.ComputationProviderMachine;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.*;
import io.netty.buffer.Unpooled;
import java.util.*;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeCloudGameTests {
    private static WorkableElectricMultiblockMachine form(GameTestHelper helper, MachineEntry<MultiblockMachineDefinition> entry, BlockPos origin) {
        var blocks = new LinkedHashMap<BlockPos, BlockInfo>();
        AbstractStructureHelper.blockPattern(new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap(Map.of(0, 1)))
                .populate(blocks, entry.get().getStructurePatterns().get("main").get(), null, Direction.NORTH, Direction.UP, false);
        BlockPos controller = null;
        for (var block : blocks.entrySet()) {
            var pos = origin.offset(block.getKey()); helper.setBlock(pos, block.getValue().getBlockState());
            if (block.getValue().getBlockState().is(entry.getBlock())) controller = pos;
        }
        var machine = (WorkableElectricMultiblockMachine) helper.getBlockEntity(Objects.requireNonNull(controller));
        machine.setFrontFacing(Direction.NORTH); machine.checkAndFormStructure();
        helper.assertTrue(machine.isFormed(), "Native cloud fixture failed to form");
        return machine;
    }

    @GameTest(template = "gcym_empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void cloudResearchStorageRetainsComponentsBindingPowerAndNativeSaveFields(GameTestHelper helper) {
        helper.setBlock(new BlockPos(4, 3, 4), NativeCloudFixtures.DATA.getBlock().defaultBlockState());
        helper.setBlock(new BlockPos(8, 3, 4), NativeCloudFixtures.DATA_HATCH.getBlock().defaultBlockState());
        var machine = (CloudOpticalDataMachine) helper.getBlockEntity(new BlockPos(4, 3, 4));
        var hatch = (CloudOpticalDataHatchMachine) helper.getBlockEntity(new BlockPos(8, 3, 4));
        var team = UUID.randomUUID(); machine.setOwnerUUID(team); hatch.setOwnerUUID(team); machine.onLoad();
        var cloud = CloudNetworkManager.INSTANCE;
        helper.assertTrue(team.equals(machine.getUUID()) && team.equals(hatch.getUUID()) && cloud.getLoadedDataMachineCount(team) == 1,
                "Native placement binding or cloud registration was lost");
        var recipe = GTRecipeTypes.DUMMY_RECIPES.recipeBuilder("native_cloud_component_research").duration(10).build();
        var data = GTItems.TOOL_DATA_STICK.asStack();
        data.set(GTDataComponents.RESEARCH_ITEM, new com.gregtechceu.gtceu.utils.ResearchManager.ResearchItem("native_cloud_component_research", GTRecipeTypes.DUMMY_RECIPES.get()));
        GTRecipeTypes.DUMMY_RECIPES.get().addDataStickEntry("native_cloud_component_research", recipe);
        helper.assertTrue(!machine.tryStoreResearchData(new ItemStack(net.minecraft.world.item.Items.IRON_INGOT)) && machine.tryStoreResearchData(data),
                "Cloud storage accepted non-research items or rejected native research components");
        helper.assertTrue(machine.getDataCount() == 2 && hatch.isRecipeAvailable(recipe), "Cloud research lookup lost the native recipe type/id component");
        machine.energyContainer.changeEnergy(machine.energyContainer.getEnergyCapacity());
        try {
            var tick = CloudOpticalDataMachine.class.getDeclaredMethod("updateEnergy"); tick.setAccessible(true);
            long before = machine.energyContainer.getEnergyStored(); tick.invoke(machine);
            helper.assertTrue(before - machine.energyContainer.getEnergyStored() == machine.getEnergyDemand(), "Cloud data energy charge was not exact");
            machine.energyContainer.changeEnergy(-machine.energyContainer.getEnergyStored()); tick.invoke(machine);
            helper.assertTrue(!hatch.isRecipeAvailable(recipe), "An unpowered cloud data machine continued providing research");
            machine.energyContainer.changeEnergy(machine.energyContainer.getEnergyCapacity()); tick.invoke(machine);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        var saved = machine.saveWithoutMetadata(helper.getLevel().registryAccess()); var pos = machine.getBlockPos(); var state = machine.getBlockState();
        machine.onUnload(); helper.getLevel().removeBlockEntity(pos);
        var restored = (CloudOpticalDataMachine) NativeCloudFixtures.DATA.getBlockEntityType().create(pos, state);
        helper.getLevel().setBlockEntity(restored); restored.loadAdditional(saved, helper.getLevel().registryAccess()); restored.onLoad();
        helper.assertTrue(team.equals(restored.getUUID()) && restored.getDataCount() == 2 && restored.providesRecipe(recipe) && cloud.getLoadedDataMachineCount(team) == 1,
                "Native save/reload lost team binding, stored data, or provider registration");
        var creative = restored.getAllTraits().stream().filter(t -> t instanceof NotifiableItemStackHandler h && h.getSlots() == 1)
                .map(t -> (NotifiableItemStackHandler) t).findFirst().orElseThrow();
        creative.setStackInSlot(0, GTResearchMachines.CREATIVE_DATA_ACCESS_HATCH.asStack());
        var unknown = GTRecipeTypes.DUMMY_RECIPES.recipeBuilder("native_cloud_unknown_research").duration(10).build();
        helper.assertTrue(restored.providesRecipe(unknown), "Creative cloud data did not provide arbitrary research");
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        hatch.onDataStickShiftUse(player, data); helper.assertTrue(hatch.getUUID() == null && !hatch.isRecipeAvailable(recipe), "Native shift-use failed to unbind research");
        hatch.onDataStickUse(player, data); helper.assertTrue(player.getUUID().equals(hatch.getUUID()), "Native data-stick use failed to bind research");
        restored.onUnload(); GTRecipeTypes.DUMMY_RECIPES.get().removeDataStickEntry("native_cloud_component_research", recipe);
        helper.succeed();
    }

    @GameTest(template = "gcym_empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void cloudComputationSimulationSharesNativeBudgetAndSynchronizesRealRows(GameTestHelper helper) {
        var provider = (ComputationProviderMachine) form(helper, NativeCloudFixtures.PROVIDER, new BlockPos(4, 3, 4));
        var consumer = form(helper, NativeCloudFixtures.CONSUMER, new BlockPos(4, 3, 9));
        var tx = (CloudOpticalComputationHatchMachine) provider.getParts().stream().filter(p -> p instanceof CloudOpticalComputationHatchMachine).findFirst().orElseThrow();
        var rx = (CloudOpticalComputationHatchMachine) consumer.getParts().getFirst();
        var team = UUID.randomUUID(); tx.setOwnerUUID(team); rx.setOwnerUUID(team); tx.onLoad(); rx.onLoad();
        provider.getTraitsFromParts(NotifiableItemStackHandler.class).getFirst().setStackInSlot(0, NativeComputationFixtures.OPTICAL_MAINFRAME.asStack(8));
        var energy = provider.getTraitsFromParts(NotifiableEnergyContainer.class).getFirst(); energy.changeEnergy(energy.getEnergyCapacity());
        provider.totalCWU = 40;
        var cloud = CloudNetworkManager.INSTANCE;
        helper.assertTrue(cloud.getProviderCount(team) == 1 && cloud.requestCWU(team, 30, true) == 30 && provider.allocatedCWUt == 0 && provider.totalCWU == 40,
                "Cloud traversal rejected a native provider or consumed computation during simulation");
        var trait = rx.getComputationContainer();
        var recipe = GTRecipeTypes.DUMMY_RECIPES.recipeBuilder("native_cloud_cwu_requirement").duration(100).build();
        helper.assertTrue(trait.handleRecipeInner(IO.IN, recipe, new ArrayList<>(List.of(7, 13)), true).isEmpty() && provider.allocatedCWUt == 0,
                "Cloud recipe simulation allocated native CWU");
        helper.assertTrue(trait.handleRecipeInner(IO.IN, recipe, new ArrayList<>(List.of(7, 13)), false).isEmpty() && provider.allocatedCWUt == 20,
                "Cloud native recipe input did not reserve the exact combined budget");
        var huge = new ArrayList<>(List.of(Integer.MAX_VALUE, Integer.MAX_VALUE));
        helper.assertTrue(trait.handleRecipeInner(IO.IN, recipe, huge, false).equals(huge) && provider.allocatedCWUt == 20,
                "Cloud input aggregation overflow accepted an impossible computation demand");
        provider.tick(); provider.totalCWU = 100; energy.changeEnergy(-energy.getEnergyStored());
        recipe.data.putBoolean("duration_is_total_cwu", true); consumer.getRecipeLogic().setProgress(3);
        helper.assertTrue(trait.handleRecipeInner(IO.IN, recipe, new ArrayList<>(List.of(10)), false).isEmpty() && trait.getLastResearchCwu() == 100 && consumer.getRecipeLogic().getProgress() == 102,
                "Native total-CWU research lost acceleration or billed more than its available native budget");
        var rows = CloudComputationMonitorOverviewPage.rows(cloud.getComputationTopologySnapshot(team), true, true);
        helper.assertTrue(rows.size() == 1 && rows.getFirst().kind() == 0 && rows.getFirst().machines().getFirst().getPos().equals(provider.getBlockPos()),
                "Native cloud overview lost provider topology");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            var row = rows.getFirst(); CloudComputationMonitorOverviewPage.ROW_CODEC.encode(buffer, row);
            helper.assertTrue(CloudComputationMonitorOverviewPage.ROW_CODEC.areEqual(row, CloudComputationMonitorOverviewPage.ROW_CODEC.decode(buffer)) && !buffer.isReadable(),
                    "Registry-aware cloud row serialization lost machine item components, positions or exact CWU");
        } finally { buffer.release(); }
        tx.setUUID(UUID.randomUUID()); helper.assertTrue(cloud.getProviderCount(team) == 0 && trait.requestCWUt(1, true, new HashSet<>()) == 0,
                "Native team rebinding left stale cloud topology");
        tx.onUnload(); rx.onUnload(); helper.succeed();
    }
}
