package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.machine.ISuspendableMachine;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMENativeOutputBuffer;
import org.gtlcore.gtlcore.common.data.GTLBedrockFluids;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.AdvancedInfiniteDrillMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.TemperatureSensorPartMachine;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.*;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.multiblock.util.*;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.api.sync_system.ClassSyncData;
import com.gregtechceu.gtceu.common.block.CoilBlock;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;
import appeng.api.stacks.AEFluidKey;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.*;

@GameTestHolder("gtlcore_recipe_test") @PrefixGameTestTemplate(false)
public final class NativeAdvancedFluidRigGameTests {
    public static AdvancedInfiniteDrillMachine form(GameTestHelper helper) {
        var shape = new LinkedHashMap<BlockPos, BlockInfo>(); var definition = NativeAdvancedFluidRigFixtures.DRILL;
        AbstractStructureHelper.blockPattern(new Int2IntOpenHashMap(Map.of(0, 1))).populate(shape,
                definition.get().getStructurePatterns().get("main").get(), null, Direction.NORTH, Direction.UP, false);
        BlockPos controller = null;
        for (var block : shape.entrySet()) {
            var pos = new BlockPos(4, 12, 4).offset(block.getKey()); var state = block.getValue().getBlockState();
            if (state.getBlock() instanceof CoilBlock) state = NativeAdvancedFluidRigFixtures.COIL.getDefaultState();
            helper.setBlock(pos, state); if (state.is(definition.getBlock())) controller = pos;
        }
        var machine = (AdvancedInfiniteDrillMachine) helper.getBlockEntity(Objects.requireNonNull(controller));
        machine.checkAndFormStructure(); machine.setWorkingEnabled(false);
        helper.assertTrue(machine.isFormed() && machine.maxHeat == 8000 && machine.getParts().size() == 5,
                "Native advanced drill did not bind its original thermal material, two inputs, output, laser and sensor");
        machine.setMachineStorageItem(ChemicalHelper.get(TagPrefix.toolHeadDrill, GTMaterials.Neutronium)); return machine;
    }
    public static void heat(AdvancedInfiniteDrillMachine machine, int heat, int process) {
        var fields = ClassSyncData.getClassData(machine.getClass()).getServerSaveFields();
        fields.get("currentHeat").handle.set(machine, heat); fields.get("process").handle.set(machine, process);
    }
    public static Map<Fluid, Long> deposits(AdvancedInfiniteDrillMachine machine, int yield, boolean mixed) {
        var data = BedrockFluidVeinSavedData.getOrCreate((net.minecraft.server.level.ServerLevel) machine.getLevel());
        var registry = machine.getLevel().registryAccess().registryOrThrow(GTRegistries.Keys.BEDROCK_FLUID);
        int x = SectionPos.blockToSectionCoord(machine.getBlockPos().getX()), z = SectionPos.blockToSectionCoord(machine.getBlockPos().getZ());
        Map<Fluid, Long> expected = new HashMap<>();
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            var holder = registry.getHolderOrThrow(mixed && dx < 0 ? GTLBedrockFluids.VOID_OIL : GTLBedrockFluids.VOID_ALT_WATER);
            data.veinFluids.put(new ChunkPos(x + dx, z + dz), new FluidVeinWorldEntry(holder, yield, 100000));
            expected.merge(holder.value().getStoredFluid(), yield * 10L, Math::addExact);
        }
        return expected;
    }
    private static IMENativeOutputBuffer output(AdvancedInfiniteDrillMachine machine) {
        return machine.getParts().stream().filter(IMENativeOutputBuffer.class::isInstance).map(IMENativeOutputBuffer.class::cast).findFirst().orElseThrow();
    }
    private static List<FluidHatchPartMachine> inputs(AdvancedInfiniteDrillMachine machine) {
        var inputs = machine.getParts().stream().filter(FluidHatchPartMachine.class::isInstance).map(FluidHatchPartMachine.class::cast)
                .filter(hatch -> hatch.tank.getHandlerIO() == com.gregtechceu.gtceu.api.capability.recipe.IO.IN).toList();
        if (inputs.size() != 2) throw new AssertionError("Native advanced fixture must bind exactly two actual input tanks");
        return inputs;
    }
    private static void ticks(RecipeLogic logic, int count) { for (int i = 0; i < count; i++) logic.serverTick(); }
    private static void fillEnergy(AdvancedInfiniteDrillMachine machine) { machine.getEnergyContainer().changeEnergy(Long.MAX_VALUE); }
    private static void thermalTick(AdvancedInfiniteDrillMachine machine) {
        try { var method = AdvancedInfiniteDrillMachine.class.getDeclaredMethod("heatUpdate"); method.setAccessible(true); method.invoke(machine); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    @GameTest(template = "gcym_empty", batch = "gtl_native_advanced_fluid", timeoutTicks = 160)
    public static void nativeAdvancedRigRequiresTheWholeCycleEnergyAndProducesBothLongFluidsExactlyOnce(GameTestHelper helper) {
        var machine = form(helper); heat(machine, 8000, 0); var base = deposits(machine, 1001, true);
        long eut = (2097152L + 250250L) * 25000L, cost = eut * 20;
        machine.getEnergyContainer().changeEnergy(cost - 1); machine.setWorkingEnabled(true);
        machine.getRecipeLogic().findAndHandleRecipe();
        helper.assertTrue(machine.getRecipeLogic().isIdle() && machine.getRecipeLogic().getLastRecipe() == null
                && machine.getEnergyContainer().getEnergyStored() == cost - 1 && machine.getRecipeLogic().getVeinFluids().equals(base),
                "Advanced native query lost its 5x5 long reserves, accepted partial-cycle energy or consumed energy during search");
        machine.getEnergyContainer().changeEnergy(1); machine.getRecipeLogic().findAndHandleRecipe();
        var logic = machine.getRecipeLogic(); helper.assertTrue(logic.isWorking() && logic.getLastRecipe().getInputEUt().getTotalEU() == eut,
                "Fully powered native advanced rig failed to start or changed original rate-scaled EU");
        logic.setSuspendAfterFinish(true); ((ISuspendableMachine) machine).gtlcore$setSuspendAfterFinish(true); ticks(logic, 20);
        var buffer = output(machine).gtlcore$getOutputBuffer();
        base.forEach((fluid, quantity) -> helper.assertTrue(buffer.storage.getLong(AEFluidKey.of(fluid)) == quantity * 25000L,
                "Native advanced rig truncated, lost or duplicated one of its two long fluids"));
        helper.assertTrue(logic.isSuspend() && machine.getEnergyContainer().getEnergyStored() == 0 && !machine.isEmpty(),
                "Native advanced rig restarted, consumed an incorrect full cost or used up its drill head");
        ticks(logic, 5); helper.assertTrue(buffer.storage.values().longStream().sum() == 6_256_250_000L,
                "Paused native advanced rig produced a second cycle"); helper.succeed();
    }

    @GameTest(template = "gcym_empty", batch = "gtl_native_advanced_fluid", timeoutTicks = 160)
    public static void nativeAdvancedRigRetainsBothFinishPausesAndItsCompletedOutputWhenTheOwnedLedgerIsFull(GameTestHelper helper) {
        var machine = form(helper); heat(machine, 8000, 0); var base = deposits(machine, 101, false); fillEnergy(machine);
        machine.setWorkingEnabled(true); var logic = machine.getRecipeLogic(); logic.findAndHandleRecipe();
        helper.assertTrue(logic.isWorking(), "Advanced native blocked-output fixture did not start");
        var fluid = base.keySet().iterator().next(); var key = AEFluidKey.of(fluid); var buffer = output(machine).gtlcore$getOutputBuffer();
        buffer.storage.put(key, Long.MAX_VALUE); logic.setSuspendAfterFinish(true); ((ISuspendableMachine) machine).gtlcore$setSuspendAfterFinish(true);
        ticks(logic, 20); long energy = machine.getEnergyContainer().getEnergyStored();
        helper.assertTrue(logic.isWaiting() && logic.getProgress() == 20 && logic.isSuspendAfterFinish() && ((ISuspendableMachine) machine).gtlcore$isSuspendAfterFinish()
                && buffer.storage.getLong(key) == Long.MAX_VALUE, "Blocked advanced rig lost pending production, changed owned quantities or consumed a pause");
        ticks(logic, 5); helper.assertTrue(machine.getEnergyContainer().getEnergyStored() == energy, "Advanced output waiting paid extra ticks");
        buffer.storage.removeLong(key); logic.serverTick();
        helper.assertTrue(logic.isSuspend() && buffer.storage.getLong(key) == 631_250_000L && machine.getEnergyContainer().getEnergyStored() == energy,
                "Unblocked advanced rig failed to deliver and pause exactly once"); helper.succeed();
    }
    @GameTest(template = "gcym_empty", batch = "gtl_native_advanced_fluid", timeoutTicks = 160)
    public static void nativeAdvancedRigRestoresHeatHeadModesAndAnInFlightLongRecipeWithoutRepeatingPaidTicks(GameTestHelper helper) {
        var machine = form(helper); heat(machine, 8000, 17); machine.setFastMode(true);
        var head = machine.getMachineStorageItem().copy(); head.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Owned drill head"));
        machine.setMachineStorageItem(head); var base = deposits(machine, 1001, true); fillEnergy(machine);
        long energy = machine.getEnergyContainer().getEnergyStored(); machine.setWorkingEnabled(true); var logic = machine.getRecipeLogic(); logic.findAndHandleRecipe();
        helper.assertTrue(logic.isWorking(), "In-flight advanced fixture did not start");
        long eut = logic.getLastRecipe().getInputEUt().getTotalEU(); logic.setSuspendAfterFinish(true); ((ISuspendableMachine) machine).gtlcore$setSuspendAfterFinish(true); ticks(logic, 7);
        var saved = machine.saveWithoutMetadata(helper.getLevel().registryAccess()); var pos = machine.getBlockPos(); var state = machine.getBlockState();
        machine.invalidateStructure("main"); helper.getLevel().removeBlockEntity(pos);
        var copy = (AdvancedInfiniteDrillMachine) NativeAdvancedFluidRigFixtures.DRILL.getBlockEntityType().create(pos, state);
        copy.loadAdditional(saved, helper.getLevel().registryAccess()); helper.getLevel().setBlockEntity(copy); copy.checkAndFormStructure();
        helper.assertTrue(copy.isFormed() && copy.getCurrentHeat() == 8000 && copy.getMeltingProgress() == 17 && copy.isFastMode()
                && ItemStack.isSameItemSameComponents(copy.getMachineStorageItem(), head) && copy.getRecipeLogic().getProgress() == 7
                && copy.getRecipeLogic().isSuspendAfterFinish() && ((ISuspendableMachine) copy).gtlcore$isSuspendAfterFinish()
                && copy.getRecipeLogic().getVeinFluids().equals(base), "Native advanced save/reload lost thermal fields, component head, native/Core pause, reserves or paid progress");
        ticks(copy.getRecipeLogic(), 13);
        base.forEach((fluid, quantity) -> helper.assertTrue(output(copy).gtlcore$getOutputBuffer().storage.getLong(AEFluidKey.of(fluid)) == quantity * 25000L,
                "Reloaded advanced rig lost or duplicated its pending long output"));
        helper.assertTrue(copy.getRecipeLogic().isSuspend() && copy.getEnergyContainer().getEnergyStored() == energy - 20 * eut,
                "Reloaded advanced rig paid previously completed ticks again"); helper.succeed();
    }

    @GameTest(template = "gcym_empty", batch = "gtl_native_advanced_fluid", timeoutTicks = 160)
    public static void nativeAdvancedRigUsesOriginalCoolantPriorityQuantityAndThermalEffect(GameTestHelper helper) {
        var machine = form(helper); heat(machine, 4200, 0); deposits(machine, 100, false); fillEnergy(machine);
        machine.setWorkingEnabled(true); machine.getRecipeLogic().findAndHandleRecipe(); machine.setFastMode(true);
        helper.assertTrue(machine.getRecipeLogic().isWorking(), "Native coolant fixture did not start");
        var hatches = inputs(machine);
        var coolants = List.of(GTMaterials.DistilledWater.getFluid(20000), GTMaterials.Oxygen.getFluid(com.gregtechceu.gtceu.api.fluids.store.FluidStorageKeys.LIQUID, 20000),
                GTMaterials.Helium.getFluid(com.gregtechceu.gtceu.api.fluids.store.FluidStorageKeys.LIQUID, 20000), GTMaterials.Nitrogen.getFluid(20000));
        int[] effects = {1, 2, 4, 8};
        for (int i = 0; i < coolants.size(); i++) {
            heat(machine, 4200, 1); hatches.getFirst().tank.setFluidInTank(0, coolants.get(i).copy()); thermalTick(machine);
            helper.assertTrue(machine.getCurrentHeat() == 4200 - effects[i] && hatches.getFirst().tank.isEmpty() && machine.getMeltingProgress() == 0,
                    "Native coolant changed original quantity, temperature effect or overheating recovery: " + i + ", heat=" + machine.getCurrentHeat()
                            + ", process=" + machine.getMeltingProgress() + ", fluid=" + hatches.getFirst().tank.getFluidInTank(0));
        }
        heat(machine, 4200, 1); hatches.getFirst().tank.setFluidInTank(0, coolants.get(0).copy()); hatches.getLast().tank.setFluidInTank(0, coolants.get(3).copy());
        thermalTick(machine); helper.assertTrue(machine.getCurrentHeat() == 4199 && hatches.getFirst().tank.isEmpty()
                && hatches.getLast().tank.getFluidInTank(0).getAmount() == 20000, "Native advanced rig consumed multiple coolants or reversed original priority: heat="
                + machine.getCurrentHeat() + ", water=" + hatches.getFirst().tank.getFluidInTank(0) + ", nitrogen=" + hatches.getLast().tank.getFluidInTank(0));
        machine.setWorkingEnabled(false); helper.succeed();
    }
    @GameTest(template = "gcym_empty", batch = "gtl_native_advanced_fluid", timeoutTicks = 120)
    public static void nativeThermalSubscriptionConsumesExactBlazeUpdatesRedstoneAndStopsUntilStructureRepair(GameTestHelper helper) {
        var machine = form(helper); var hatch = inputs(machine).getFirst();
        var sensor = machine.getParts().stream().filter(TemperatureSensorPartMachine.class::isInstance).map(TemperatureSensorPartMachine.class::cast).findFirst().orElseThrow();
        sensor.setMax(300); hatch.tank.setFluidInTank(0, GTMaterials.Blaze.getFluid(2000));
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(machine.getCurrentHeat() == 301 && hatch.tank.getFluidInTank(0).getAmount() == 2000 - (long) Math.pow(300, 1.3)
                    && sensor.getOutputSignal(sensor.getFrontFacing().getOpposite()) == 15,
                    "Actual native heat subscription lost exact blaze consumption, original slow cadence or redstone output: heat="
                            + machine.getCurrentHeat() + ", fluid=" + hatch.tank.getFluidInTank(0) + ", sensor="
                            + sensor.getOutputSignal(sensor.getFrontFacing().getOpposite()) + ", formed=" + machine.isFormed());
            machine.invalidateStructure("main"); hatch.tank.setFluidInTank(0, GTMaterials.Blaze.getFluid(2000));
        });
        helper.runAtTickTime(24, () -> {
            helper.assertTrue(machine.getCurrentHeat() == 301 && hatch.tank.getFluidInTank(0).getAmount() == 2000,
                    "Invalid native structure retained its thermal subscription"); machine.checkAndFormStructure();
        });
        helper.runAtTickTime(36, () -> {
            helper.assertTrue(machine.isFormed() && machine.getCurrentHeat() == 302 && hatch.tank.getFluidInTank(0).getAmount() == 2000 - (long) Math.pow(301, 1.3),
                    "Repaired native structure failed to restore thermal subscription exactly once"); helper.succeed();
        });
    }

    @GameTest(template = "gcym_empty", batch = "gtl_native_advanced_fluid", timeoutTicks = 400)
    public static void nativeFastOverheatMeltsTheActualDrillOnlyAfterThreeHundredWorldTicksAndResetsSensor(GameTestHelper helper) {
        var machine = form(helper); heat(machine, 8001, 0); machine.setFastMode(true);
        var sensor = machine.getParts().stream().filter(TemperatureSensorPartMachine.class::isInstance).map(TemperatureSensorPartMachine.class::cast).findFirst().orElseThrow(); sensor.setMax(500);
        helper.runAtTickTime(295, () -> helper.assertTrue(!machine.isEmpty() && machine.getMeltingProgress() >= 290 && machine.getMeltingProgress() < 300
                && sensor.getOutputSignal(sensor.getFrontFacing().getOpposite()) == 15, "Native fast-mode melt occurred before its original 300-tick deadline"));
        helper.runAtTickTime(305, () -> {
            helper.assertTrue(machine.isEmpty() && machine.getCurrentHeat() == 300 && machine.getMeltingProgress() == 0
                    && sensor.getOutputSignal(sensor.getFrontFacing().getOpposite()) == 0 && machine.getRecipeLogic().getLastRecipe() == null,
                    "Actual native overheat failed to melt/reset the drill head, heat, progress, recipe or redstone"); helper.succeed();
        });
    }
    @GameTest(template = "gcym_empty", batch = "gtl_native_advanced_fluid", timeoutTicks = 160)
    public static void nativeAdvancedRigRejectsColdMissingHeadsAndUnsupportedCoilsThenRecoversItsOriginalHeadRates(GameTestHelper helper) {
        var machine = form(helper); deposits(machine, 100, false); fillEnergy(machine); heat(machine, 1999, 0); machine.setWorkingEnabled(true);
        machine.getRecipeLogic().findAndHandleRecipe(); helper.assertTrue(machine.getRecipeLogic().isIdle(), "Cold advanced rig admitted a recipe");
        heat(machine, 8000, 0); machine.setMachineStorageItem(ItemStack.EMPTY); machine.getRecipeLogic().findAndHandleRecipe();
        helper.assertTrue(machine.getRecipeLogic().isIdle() && machine.getRate() == 0, "Headless advanced rig admitted production");
        var vibranium = ChemicalHelper.get(TagPrefix.toolHeadDrill, NativeAdvancedFluidRigFixtures.VIBRANIUM);
        helper.assertTrue(!vibranium.isEmpty() && machine.machineStorage.isItemValid(0, vibranium), "Native advanced slot rejected the original vibranium head");
        machine.setMachineStorageItem(vibranium); helper.assertTrue(machine.getDrillHeadRate() == 100000 && machine.getRate() == 50000, "Native vibranium head changed original base/thermal rate");
        var coils = machine.getPatternState("main").getCache().long2ObjectEntrySet().stream().filter(e -> e.getValue().getBlockState().getBlock() instanceof CoilBlock)
                .map(e -> BlockPos.of(e.getLongKey())).toList(); machine.invalidateStructure("main");
        var ordinary = com.gregtechceu.gtceu.api.GTCEuAPI.HEATING_COILS.get(CoilBlock.CoilType.CUPRONICKEL).get().defaultBlockState();
        coils.forEach(pos -> helper.getLevel().setBlockAndUpdate(pos, ordinary)); machine.checkAndFormStructure();
        helper.assertTrue(machine.isFormed() && machine.maxHeat == -1 && !machine.canRunnable(), "Native advanced rig treated an unsupported ordinary coil as a valid Core thermal coil");
        machine.invalidateStructure("main"); coils.forEach(pos -> helper.getLevel().setBlockAndUpdate(pos, NativeAdvancedFluidRigFixtures.COIL.getDefaultState())); machine.checkAndFormStructure();
        machine.setWorkingEnabled(true); machine.getRecipeLogic().findAndHandleRecipe();
        helper.assertTrue(machine.isFormed() && machine.maxHeat == 8000 && machine.getRecipeLogic().isWorking(), "Supported coil replacement failed to restore native advanced production");
        machine.setWorkingEnabled(false); helper.succeed();
    }

    @GameTest(template = "gcym_empty", batch = "gtl_native_advanced_fluid", timeoutTicks = 160)
    public static void nativeAdvancedFastMenuUsesAuthoritativePacketsReadonlyDisplayAndActualSavedFields(GameTestHelper helper) {
        var host = form(helper); heat(host, 3500, 12);
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(helper.getLevel()); player.setPos(host.getBlockPos().getCenter());
        var settings = new brachy.modularui.screen.UISettings(brachy.modularui.api.RecipeViewerSettings.DUMMY);
        var sync = new brachy.modularui.value.sync.ModularSyncManager(false); var manager = new brachy.modularui.value.sync.PanelSyncManager(sync, true);
        var data = new brachy.modularui.factory.PosGuiData(player, host.getBlockPos());
        var panel = com.gregtechceu.gtceu.api.mui.factory.MachineUIFactory.INSTANCE.createPanel(data, manager, settings);
        brachy.modularui.widget.WidgetTree.collectSyncValues(manager, panel);
        var menu = new brachy.modularui.screen.ModularContainerMenu(99); menu.construct(player, sync, settings, panel.getName(), data); player.containerMenu = menu;
        var packet = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            var fast = manager.findSyncHandler("advancedFluidFast", brachy.modularui.value.sync.BooleanSyncValue.class);
            packet.writeBoolean(true); manager.receiveWidgetUpdate(fast.getKey(), false, 0, packet);
            helper.assertTrue(host.isFastMode() && fast.isAllowC2S(), "Native advanced fast control did not apply its real server packet");
            var display = manager.findSyncHandler("advancedFluidDrillText", brachy.modularui.value.sync.StringSyncValue.class); display.updateCacheFromSource(true);
            helper.assertTrue(!display.isAllowC2S() && display.getStringValue().contains("3500"), "Native thermal display lost authoritative heat or allowed client state edits");
            var saved = host.saveWithoutMetadata(helper.getLevel().registryAccess());
            var copy = (AdvancedInfiniteDrillMachine) NativeAdvancedFluidRigFixtures.DRILL.getBlockEntityType().create(host.getBlockPos(), host.getBlockState());
            copy.loadAdditional(saved, helper.getLevel().registryAccess()); helper.assertTrue(copy.isFastMode() && copy.getCurrentHeat() == 3500 && copy.getMeltingProgress() == 12,
                    "Native menu-edited fast mode or thermal fields did not persist");
        } finally { packet.release(); player.closeContainer(); }
        helper.succeed();
    }

    @GameTest(template = "gcym_empty", batch = "gtl_native_advanced_fluid", timeoutTicks = 160)
    public static void nativeCoreLevelTenToolKeepsRealDropEligibilityAndAllLowerToolsRejectItsRequiredBlock(GameTestHelper helper) {
        var tags = com.gregtechceu.gtceu.data.recipe.CustomTags.INCORRECT_TOOL_TIERS; var state = NativeAdvancedFluidRigFixtures.TIER_TEN_BLOCK.getDefaultState();
        for (int tier = 0; tier < 10; tier++) helper.assertTrue(state.is(tags[tier]), "Native dynamic drop tags failed to reject a lower harvest tier: " + tier);
        helper.assertTrue(!state.is(tags[10]) && state.is(net.minecraft.tags.BlockTags.INCORRECT_FOR_GOLD_TOOL), "Core level-ten or vanilla gold drop restriction changed");
        var tool = com.gregtechceu.gtceu.api.item.tool.ToolHelper.getAndSetToolData(com.gregtechceu.gtceu.api.item.tool.GTToolType.DRILL_LV,
                NativeAdvancedFluidRigFixtures.VIBRANIUM, 100, 10, 1, 1);
        helper.assertTrue(!tool.isEmpty() && tool.isCorrectToolForDrops(state) && !new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE).isCorrectToolForDrops(state),
                "Native actual tool components lost level-ten drop eligibility or allowed a diamond pickaxe"); helper.succeed();
    }
}
