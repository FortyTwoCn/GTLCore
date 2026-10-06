package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.machine.multiblock.generator.DysonSphereMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.ComputationProviderMachine;
import org.gtlcore.gtlcore.common.data.GTLRecipeTypes;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.*;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.*;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.multiblock.util.*;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import com.gregtechceu.gtceu.common.blockentity.OpticalPipeBlockEntity;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.machines.GTResearchMachines;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeDysonGameTests {
    private static Map<BlockPos, BlockInfo> preview(MachineEntry<MultiblockMachineDefinition> entry) {
        var blocks = new LinkedHashMap<BlockPos, BlockInfo>();
        AbstractStructureHelper.blockPattern(new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap(Map.of(0, 1)))
                .populate(blocks, entry.get().getStructurePatterns().get("main").get(), null, Direction.NORTH, Direction.UP, false);
        return blocks;
    }
    private static com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine form(
            GameTestHelper helper, MachineEntry<MultiblockMachineDefinition> entry, BlockPos origin) {
        BlockPos controller = null;
        for (var block : preview(entry).entrySet()) {
            var pos = origin.offset(block.getKey()); helper.setBlock(pos, block.getValue().getBlockState());
            if (block.getValue().getBlockState().is(entry.getBlock())) controller = pos;
        }
        var machine = (com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine) helper.getBlockEntity(Objects.requireNonNull(controller));
        machine.setFrontFacing(Direction.NORTH); machine.setWorkingEnabled(false);
        machine.checkAndFormStructure(); helper.assertTrue(machine.isFormed(), "Native Dyson fixture did not form: " + entry.getId());
        return machine;
    }
    private static BlockPos receiver(DysonSphereMachine machine) { return machine.getBlockPos().offset(4, 14, 0); }
    private static DysonSphereMachine build(GameTestHelper helper, boolean withReceiver) {
        var machine = (DysonSphereMachine) form(helper, NativeDysonFixtures.DYSON, new BlockPos(8, 3, 8));
        if (withReceiver) {
            helper.getLevel().setBlockAndUpdate(receiver(machine), NativeDysonFixtures.RECEIVER.getDefaultState());
            machine.invalidateStructure("main"); machine.checkAndFormStructure();
        }
        return machine;
    }
    private static NotifiableEnergyContainer inputPower(DysonSphereMachine machine) {
        return machine.getTraits(NotifiableEnergyContainer.class).stream().filter(t -> t.getHandlerIO() == IO.IN).findFirst().orElseThrow();
    }
    private static List<NotifiableEnergyContainer> outputPower(DysonSphereMachine machine) {
        return machine.getTraits(NotifiableEnergyContainer.class).stream().filter(t -> t.getHandlerIO() == IO.OUT).toList();
    }
    private static GTRecipe launch(String id, int duration) {
        return GTLRecipeTypes.DYSON_SPHERE_RECIPES.recipeBuilder(id).inputItems(Items.IRON_INGOT).EUt(32).duration(duration).build();
    }
    private static void state(GameTestHelper helper, DysonSphereMachine machine, int spheres, int damage) {
        var tag = machine.saveWithoutMetadata(helper.getLevel().registryAccess());
        tag.putInt("DysonSphereData", spheres); tag.putInt("DysonSpheredamageData", damage);
        machine.loadAdditional(tag, helper.getLevel().registryAccess());
    }
    private static void stockLaunch(DysonSphereMachine machine) {
        inputPower(machine).changeEnergy(1024);
        machine.getTrait(NotifiableItemStackHandler.class).setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
    }

    @GameTest(template = "gcym_empty", batch = "gtl_native_dyson", timeoutTicks = 120)
    public static void nativeReceiverSkyAndPowerChecksDoNotConsumeLaunchOrAdvanceUntilAcceptedTicks(GameTestHelper helper) {
        var machine = build(helper, false); stockLaunch(machine); var recipe = launch("native_dyson_launch", 4);
        helper.assertTrue(!machine.getRecipeLogic().checkMatchedRecipeAvailable(recipe) && machine.getTrait(NotifiableItemStackHandler.class).getStackInSlot(0).getCount() == 2,
                "Missing external receiver consumed a native launch input");
        helper.getLevel().setBlockAndUpdate(receiver(machine), NativeDysonFixtures.RECEIVER.getDefaultState());
        machine.invalidateStructure("main"); machine.checkAndFormStructure();
        var roof = receiver(machine).offset(6, 2, 6); helper.getLevel().setBlockAndUpdate(roof, Blocks.IRON_BLOCK.defaultBlockState());
        helper.startSequence().thenExecuteAfter(10, () -> {
            helper.assertTrue(!helper.getLevel().canSeeSky(roof.below()) && !machine.getRecipeLogic().checkMatchedRecipeAvailable(recipe)
                    && machine.getDysonSphereData() == 0 && inputPower(machine).getEnergyStored() == 1024,
                    "Blocked receiver sky consumed energy or launched a sphere after native lighting updated");
            helper.getLevel().setBlockAndUpdate(roof, Blocks.AIR.defaultBlockState());
        }).thenExecuteAfter(10, () -> {
            helper.assertTrue(machine.getRecipeLogic().checkMatchedRecipeAvailable(recipe), "Clear native receiver did not accept a launch");
            var logic = machine.getRecipeLogic(); logic.setSuspendAfterFinish(true);
            inputPower(machine).setEnergyStored(0); logic.serverTick();
            helper.assertTrue(logic.getProgress() == 0 && machine.getDysonSphereData() == 0, "A native power failure advanced launch state");
            inputPower(machine).changeEnergy(1024);
            logic.setWorkingEnabled(true); logic.setSuspendAfterFinish(true);
            for (int tick = 0; tick < 64; tick++) logic.serverTick();
            helper.assertTrue(machine.getDysonSphereData() == 1 && logic.getStatus() == RecipeLogic.Status.SUSPEND
                    && inputPower(machine).getEnergyStored() == 896 && machine.getTrait(NotifiableItemStackHandler.class).getStackInSlot(0).getCount() == 1,
                    "Accepted native launch did not pay four exact ticks and add one sphere: spheres=" + machine.getDysonSphereData()
                    + " progress=" + logic.getProgress() + "/" + logic.getDuration() + " status=" + logic.getStatus()
                    + " energy=" + inputPower(machine).getEnergyStored() + " items=" + machine.getTrait(NotifiableItemStackHandler.class).getStackInSlot(0));
            helper.getLevel().setDayTime(1000);
        }).thenExecuteAfter(15, () -> {
            helper.assertTrue(helper.getLevel().getDayTime() >= 18000 && helper.getLevel().getDayTime() <= 18010,
                    "Launching from zero spheres did not activate the real night subscription");
            var tag = machine.saveWithoutMetadata(helper.getLevel().registryAccess());
            var copy = (DysonSphereMachine) NativeDysonFixtures.DYSON.getBlockEntityType().create(machine.getBlockPos(), machine.getBlockState());
            copy.loadAdditional(tag, helper.getLevel().registryAccess());
            helper.assertTrue(copy.getDysonSphereData() == 1 && copy.getDysonSpheredamageData() == 0 && tag.getBoolean("upgradeTriggeredThisRecipe"),
                    "Native SaveField round trip lost the sphere count or per-cycle launch guard");
            machine.invalidateStructure("main"); machine.invalidateStructure("main");
            helper.assertTrue(machine.getDysonSphereData() == 0, "Repeated invalidation removed a sphere more than once");
            helper.getLevel().setDayTime(1000);
        }).thenExecuteAfter(15, () -> helper.assertTrue(helper.getLevel().getDayTime() < 18000,
                "Invalidated native Dyson retained its night tick subscription")).thenSucceed();
    }

    @GameTest(template = "gcym_empty", batch = "gtl_native_dyson", timeoutTicks = 120)
    public static void nativeLaunchCapRepairAndServerOwnedMenuPreserveStoredControllerState(GameTestHelper helper) {
        var machine = build(helper, true); state(helper, machine, 10000, 61); stockLaunch(machine);
        var logic = machine.getRecipeLogic();
        helper.assertTrue(logic.checkMatchedRecipeAvailable(launch("native_dyson_repair", 2)), "Native launch at the sphere cap did not allow repairs");
        logic.setSuspendAfterFinish(true); logic.serverTick(); logic.serverTick();
        helper.assertTrue(machine.getDysonSphereData() == 10000 && machine.getDysonSpheredamageData() == 0 && machine.getOutputSignal(Direction.NORTH) == 0,
                "Native repair changed sphere cap, efficiency damage or redstone");
        logic.resetRecipeLogic();
        helper.assertTrue(logic.checkMatchedRecipeAvailable(launch("native_dyson_cap", 2)), "Native capped launch was rejected");
        logic.setSuspendAfterFinish(true); logic.serverTick(); logic.serverTick();
        helper.assertTrue(machine.getDysonSphereData() == 10000, "Native launch exceeded 10000 spheres");
        var outputs = outputPower(machine);
        for (var output : outputs) output.resetBasicInfo(output.getEnergyCapacity(), 0, 0, 32, 2);
        helper.assertTrue(machine.getMaxVoltage() == GTValues.V[GTValues.MV], "Native equivalent tier ignored the combined power of two output containers");
        outputs.getFirst().resetBasicInfo(outputs.getFirst().getEnergyCapacity(), 0, 0, Long.MAX_VALUE, Long.MAX_VALUE);
        helper.assertTrue(machine.getMaxVoltage() == GTValues.V[GTValues.MAX], "Native saturated output power overflowed its equivalent tier");
        var sync = new brachy.modularui.value.sync.ModularSyncManager(false);
        var manager = new brachy.modularui.value.sync.PanelSyncManager(sync, true);
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(helper.getLevel()); player.setPos(machine.getBlockPos().getCenter());
        var settings = new brachy.modularui.screen.UISettings(brachy.modularui.api.RecipeViewerSettings.DUMMY);
        var panel = com.gregtechceu.gtceu.api.mui.factory.MachineUIFactory.INSTANCE.createPanel(new brachy.modularui.factory.PosGuiData(player, machine.getBlockPos()), manager, settings);
        brachy.modularui.widget.WidgetTree.collectSyncValues(manager, panel);
        var text = manager.findSyncHandler("dysonText", 0, brachy.modularui.value.sync.StringSyncValue.class); text.updateCacheFromSource(true);
        helper.assertTrue(text.getStringValue().contains("gtceu.machine.dyson_sphere.number") && text.getStringValue().contains("10000"),
                "Native Dyson menu lost its server-owned sphere display");
        helper.getLevel().setBlockAndUpdate(machine.getBlockPos(), Blocks.AIR.defaultBlockState());
        helper.succeed();
    }

    private static ComputationProviderMachine connectProvider(GameTestHelper helper, DysonSphereMachine machine) {
        var rx = machine.getParts().getFirst(); rx.setFrontFacing(Direction.NORTH);
        var txOffset = preview(NativeDysonFixtures.PROVIDER).entrySet().stream()
                .filter(e -> e.getValue().getBlockState().is(GTResearchMachines.COMPUTATION_HATCH_TRANSMITTER.getBlock())).findFirst().orElseThrow().getKey();
        var target = rx.getBlockPos().north(4).subtract(helper.absolutePos(BlockPos.ZERO));
        var provider = (ComputationProviderMachine) form(helper, NativeDysonFixtures.PROVIDER, target.subtract(txOffset));
        provider.setWorkingEnabled(true);
        var tx = provider.getParts().stream().filter(p -> p.getBlockState().is(GTResearchMachines.COMPUTATION_HATCH_TRANSMITTER.getBlock())).findFirst().orElseThrow();
        tx.setFrontFacing(Direction.SOUTH);
        for (int offset = 1; offset <= 3; offset++) helper.getLevel().setBlockAndUpdate(rx.getBlockPos().north(offset), GTBlocks.OPTICAL_PIPES[0].getDefaultState());
        for (int offset = 1; offset <= 3; offset++) {
            var pipe = (OpticalPipeBlockEntity) helper.getLevel().getBlockEntity(rx.getBlockPos().north(offset));
            pipe.setConnection(Direction.NORTH, true, false); pipe.setConnection(Direction.SOUTH, true, false);
        }
        var energy = provider.getTraitsFromParts(NotifiableEnergyContainer.class).getFirst(); energy.setEnergyStored(energy.getEnergyCapacity());
        provider.tick(); return provider;
    }

    @GameTest(template = "gcym_empty", batch = "gtl_native_dyson", timeoutTicks = 160)
    public static void nativeOpticalComputationAndBlockedGenerationOutputGateDamageAndExactEnergy(GameTestHelper helper) {
        var machine = build(helper, true); state(helper, machine, 10000, 61);
        var provider = connectProvider(helper, machine);
        var recipe = GTLRecipeTypes.DYSON_SPHERE_RECIPES.recipeBuilder("native_dyson_generation").EUt(-GTValues.V[GTValues.MAX]).duration(40).build();
        var modified = DysonSphereMachine.recipeModifier(machine, recipe).apply(recipe);
        long power = (long) (2L * GTValues.V[GTValues.MAX] * 10000 * 0.98);
        helper.assertTrue(modified != null && modified.getOutputEUt().getTotalEU() == power && recipe.getOutputEUt().getTotalEU() == GTValues.V[GTValues.MAX]
                && (Integer) modified.tickInputs.get(CWURecipeCapability.CAP).getFirst().content() == 10000 && !recipe.tickInputs.containsKey(CWURecipeCapability.CAP),
                "Native generation modifier mutated its source or lost EnergyStack/CWU amount");
        helper.assertTrue(machine.getMaxVoltage() == GTValues.V[GTValues.MAX], "Native output container aggregation changed its equivalent voltage tier");
        var powerSource = provider.getTraitsFromParts(NotifiableEnergyContainer.class).getFirst();
        helper.startSequence().thenWaitUntil(() -> {
            var pipe = (OpticalPipeBlockEntity) helper.getLevel().getBlockEntity(machine.getParts().getFirst().getBlockPos().north());
            var net = pipe.getOpticalPipeNet();
            int supply = machine.getTraitsFromParts(NotifiableComputationContainer.class).getFirst().requestCWUt(10000, true);
            helper.assertTrue(net != null && supply == 10000,
                    "Real native optical network did not route the powered computation provider: net=" + (net == null ? "null" : net.getAllNodes().keySet())
                    + " connections=" + pipe.getConnections() + " supply=" + supply + " provider=" + provider.isFormed() + "/" + provider.isWorkingEnabled()
                    + " direct=" + provider.requestCWUt(10000, true) + " receiver=" + machine.getParts().getFirst().getBlockPos()
                    + " parts=" + provider.getParts().stream().map(p -> p.getBlockPos() + "/" + p.getFrontFacing()).toList());
        }).thenExecute(() -> {
            long before = powerSource.getEnergyStored();
            int available = machine.getTraitsFromParts(NotifiableComputationContainer.class).getFirst().requestCWUt(Integer.MAX_VALUE, true);
            var cwuOnly = com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder.ofRaw().CWUt(10000).build();
            int expectedParallel = Math.min(1_000_000, available / 10000);
            helper.assertTrue(expectedParallel > 0 && com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic.getMaxByInput(
                    machine, cwuOnly, 1_000_000, List.of()) == expectedParallel && powerSource.getEnergyStored() == before,
                    "Native CWU parallel dispatch lost the real optical budget or spent provider energy during preview");
            helper.assertTrue(machine.getRecipeLogic().checkMatchedRecipeAvailable(recipe), "Powered native optical generation did not start");
            var outputs = outputPower(machine); for (var output : outputs) output.setEnergyStored(output.getEnergyCapacity());
            var logic = machine.getRecipeLogic(); logic.setProgress(38); logic.serverTick();
            helper.assertTrue(logic.getProgress() == 1 && logic.getStatus() == RecipeLogic.Status.WAITING
                    && machine.getDysonSpheredamageData() == 61 && powerSource.getEnergyStored() == before,
                    "Blocked output advanced damage or consumed optical provider energy: progress=" + logic.getProgress() + " status=" + logic.getStatus()
                    + " damage=" + machine.getDysonSpheredamageData() + " provider_energy=" + before + "->" + powerSource.getEnergyStored()
                    + " output=" + outputs.stream().map(p -> p.getEnergyStored() + "/" + p.getEnergyCapacity()).toList()
                    + " recipe_output=" + logic.getLastRecipe().getOutputEUt() + " match="
                    + com.gregtechceu.gtceu.api.recipe.RecipeHelper.matchTickRecipe(machine, logic.getLastRecipe()));
            for (var output : outputs) output.setEnergyStored(0);
            logic.setProgress(38); logic.setSuspendAfterFinish(true); logic.serverTick(); int damage = machine.getDysonSpheredamageData();
            helper.assertTrue(damage >= 61 && damage <= 62, "Native generation damage changed by more than one trigger");
            logic.serverTick();
            helper.assertTrue(machine.getDysonSpheredamageData() == damage && machine.getDysonSphereData() == 10000
                    && outputs.stream().mapToLong(NotifiableEnergyContainer::getEnergyStored).sum() == power * 2
                    && powerSource.getEnergyStored() == before - 2L * Integer.MAX_VALUE && logic.getStatus() == RecipeLogic.Status.SUSPEND,
                    "Accepted generation lost exact native EU/CWU transfer or its single damage trigger");
            var saved = machine.saveWithoutMetadata(helper.getLevel().registryAccess());
            var copy = (DysonSphereMachine) NativeDysonFixtures.DYSON.getBlockEntityType().create(machine.getBlockPos(), machine.getBlockState());
            copy.loadAdditional(saved, helper.getLevel().registryAccess());
            helper.assertTrue(copy.getDysonSphereData() == 10000 && copy.getDysonSpheredamageData() == damage && saved.getBoolean("damageTriggeredThisRecipe"),
                    "Native generation save/load lost its damage or per-cycle guard");
            machine.invalidateStructure("main"); machine.invalidateStructure("main");
            helper.assertTrue(machine.getDysonSphereData() == 9999, "Native repeated invalidation removed multiple Dyson spheres");
            helper.getLevel().setBlockAndUpdate(machine.getBlockPos(), Blocks.AIR.defaultBlockState());
        }).thenSucceed();
    }
}
