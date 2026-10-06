package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.data.GTLCovers;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.cover.ConveyorCover;
import com.gregtechceu.gtceu.common.cover.FluidRegulatorCover;
import com.gregtechceu.gtceu.common.cover.InfiniteWaterCover;
import com.gregtechceu.gtceu.common.cover.PumpCover;
import com.gregtechceu.gtceu.common.cover.RobotArmCover;
import com.gregtechceu.gtceu.common.data.GTCovers;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeCoverTransferGameTests {
    private static FluidHatchPartMachine hatch(GameTestHelper helper, BlockPos pos, boolean output) {
        var entry = (output ? GTMachines.FLUID_EXPORT_HATCH : GTMachines.FLUID_IMPORT_HATCH)[GTValues.HV];
        helper.setBlock(pos, entry.getBlock());
        var hatch = (FluidHatchPartMachine) helper.getBlockEntity(pos);
        hatch.setFrontFacing(Direction.NORTH); hatch.setWorkingEnabled(false);
        return hatch;
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 140)
    public static void nativeInfiniteWaterFillsBeyondSixteenBucketsResubscribesAfterReloadAndStopsOnRemoval(GameTestHelper helper) {
        var hatch = hatch(helper, new BlockPos(4, 4, 4), false);
        int capacity = hatch.tank.getTankCapacity(0);
        helper.assertTrue(capacity > 16_000, "Supply fixture must distinguish Core's unlimited water from the native sixteen-bucket amount");
        var item = GTItems.COVER_INFINITE_WATER.asStack();
        item.set(DataComponents.CUSTOM_NAME, Component.literal("Native saved infinite water cover"));
        helper.assertTrue(hatch.getCoverContainer().placeCoverOnSide(Direction.SOUTH, item, GTCovers.INFINITE_WATER.value(), null), "Native infinite water did not attach to the original GT fluid input hatch");
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(hatch.tank.getFluidInTank(0).getAmount() == capacity && hatch.tank.getFluidInTank(0).getFluid() == Fluids.WATER, "Native cover subscription did not fill the entire real tank with original Core water production");
            var saved = hatch.saveWithoutMetadata(helper.getLevel().registryAccess());
            var copy = (FluidHatchPartMachine) GTMachines.FLUID_IMPORT_HATCH[GTValues.HV].getBlockEntityType().create(hatch.getBlockPos(), hatch.getBlockState());
            helper.getLevel().removeBlockEntity(hatch.getBlockPos());
            copy.loadAdditional(saved, helper.getLevel().registryAccess()); helper.getLevel().setBlockEntity(copy);
            var restored = copy.getCoverContainer().getCoverAtSide(Direction.SOUTH);
            helper.assertTrue(restored instanceof InfiniteWaterCover && item.get(DataComponents.CUSTOM_NAME).equals(restored.getPickItem().get(DataComponents.CUSTOM_NAME)), "Native infinite water codec lost its definition or unrelated item components");
            copy.tank.setFluidInTank(0, FluidStack.EMPTY);
            helper.runAfterDelay(25, () -> {
                helper.assertTrue(copy.tank.getFluidInTank(0).getAmount() == capacity, "Native cover tick subscription did not resume on recreated block entity");
                helper.assertTrue(copy.getCoverContainer().removeCover(false, Direction.SOUTH, null), "Native cover removal failed");
                copy.tank.setFluidInTank(0, FluidStack.EMPTY);
                helper.runAfterDelay(25, () -> {
                    helper.assertTrue(copy.tank.isEmpty(), "Removed native infinite water retained its server tick subscription");
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeProductionCoverRegistryKeepsAllEightIdsAndInitializesSubclassRatesAtEveryTier(GameTestHelper helper) {
        Holder<CoverDefinition>[] definitions = new Holder[] {GTLCovers.ELECTRIC_PUMP_MAX, GTLCovers.FLUID_REGULATORS_ULV,
                GTLCovers.FLUID_REGULATOR_MAX, GTLCovers.CONVEYOR_MODULE_MAX, GTLCovers.ROBOT_ARM_MAX,
                GTLCovers.ROBOT_ARM_ULV, GTLCovers.MAX_WIRELESS_ENERGY_RECEIVE, GTLCovers.MAX_WIRELESS_ENERGY_RECEIVE_4A};
        String[] paths = {"pump.max", "fluid_regulators.ulv", "fluid_regulator.max", "conveyor.max", "robot_arm.max",
                "robot_arm.ulv", "wireless_energy_receive.max", "4a_wireless_energy_receive.max"};
        for (int i = 0; i < paths.length; i++) {
            var id = ResourceLocation.fromNamespaceAndPath("gtceu", paths[i]);
            helper.assertTrue(GTRegistries.COVERS.get(id) == definitions[i].value() && definitions[i].value().getId().equals(id), "Native production cover registration changed the original identifier: " + id);
        }
        var holder = hatch(helper, new BlockPos(4, 4, 4), false).getCoverContainer();
        int[] itemRates = {Integer.MAX_VALUE, 8, 32, 128, 512, 2048, 8192};
        int[] fluidRates = {Integer.MAX_VALUE, 64, 256, 1024, 4096, 16_384, 65_536};
        for (int tier = GTValues.ULV; tier <= GTValues.MAX; tier++) {
            int items = itemRates[Math.min(tier, itemRates.length - 1)];
            int fluids = fluidRates[Math.min(tier, fluidRates.length - 1)];
            var conveyor = new ConveyorCover(GTLCovers.CONVEYOR_MODULE_MAX.value(), holder, Direction.SOUTH, tier);
            var arm = new RobotArmCover(GTLCovers.ROBOT_ARM_MAX.value(), holder, Direction.SOUTH, tier);
            var pump = new PumpCover(GTLCovers.ELECTRIC_PUMP_MAX.value(), holder, Direction.SOUTH, tier);
            var regulator = new FluidRegulatorCover(GTLCovers.FLUID_REGULATOR_MAX.value(), holder, Direction.SOUTH, tier);
            helper.assertTrue(conveyor.maxItemTransferRate == items && arm.maxItemTransferRate == items && conveyor.getTransferRate() == items && arm.getTransferRate() == items, "Native conveyor/robot-arm constructor initialized an inconsistent Core rate at tier " + tier);
            helper.assertTrue(pump.maxFluidTransferRate == fluids && regulator.maxFluidTransferRate == fluids && pump.getTransferRate() == fluids && regulator.getTransferRate() == fluids, "Native pump/regulator constructor initialized an inconsistent Core rate at tier " + tier);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 120)
    public static void nativeUnlimitedFluidRegulatorTransfersComponentsAcrossRepeatedBudgetRefillsAndReload(GameTestHelper helper) {
        var source = hatch(helper, new BlockPos(4, 4, 4), true);
        var destination = hatch(helper, new BlockPos(5, 4, 4), false);
        var water = new FluidStack(Fluids.WATER, source.tank.getTankCapacity(0));
        water.set(DataComponents.CUSTOM_NAME, Component.literal("Native unlimited regulator fluid"));
        source.tank.setFluidInTank(0, water.copy());
        var item = GTItems.ELECTRIC_PUMP_LV.asStack(); item.set(DataComponents.CUSTOM_NAME, Component.literal("Native regulator cover fixture"));
        helper.assertTrue(source.getCoverContainer().placeCoverOnSide(Direction.EAST, item, GTLCovers.FLUID_REGULATORS_ULV.value(), null), "Native original-identifier ULV regulator could not attach");
        var cover = (FluidRegulatorCover) source.getCoverContainer().getCoverAtSide(Direction.EAST);
        helper.assertTrue(cover.getTransferRate() == Integer.MAX_VALUE, "ULV regulator lost its original unlimited transfer setting");
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(source.tank.isEmpty() && ItemStack.isSameItemSameComponents(cover.getPickItem(), item) && destination.tank.getFluidInTank(0).getAmount() == water.getAmount() && FluidStack.isSameFluidSameComponents(water, destination.tank.getFluidInTank(0)), "Native unlimited budget overflowed or real cover transport lost components");
            destination.tank.setFluidInTank(0, FluidStack.EMPTY); source.tank.setFluidInTank(0, water.copy());
            var saved = source.saveWithoutMetadata(helper.getLevel().registryAccess());
            var copy = (FluidHatchPartMachine) GTMachines.FLUID_EXPORT_HATCH[GTValues.HV].getBlockEntityType().create(source.getBlockPos(), source.getBlockState());
            helper.getLevel().removeBlockEntity(source.getBlockPos()); copy.loadAdditional(saved, helper.getLevel().registryAccess()); helper.getLevel().setBlockEntity(copy);
            var restored = (FluidRegulatorCover) copy.getCoverContainer().getCoverAtSide(Direction.EAST);
            helper.assertTrue(restored != null && restored.getTransferRate() == Integer.MAX_VALUE, "Native ULV regulator save fields lost their unlimited setting");
            helper.runAfterDelay(25, () -> {
                helper.assertTrue(copy.tank.isEmpty() && destination.tank.getFluidInTank(0).getAmount() == water.getAmount() && FluidStack.isSameFluidSameComponents(water, destination.tank.getFluidInTank(0)), "Reloaded ULV regulator did not refill its native budget or resume actual transfer");
                copy.getCoverContainer().removeCover(false, Direction.EAST, null);
                destination.tank.setFluidInTank(0, FluidStack.EMPTY); copy.tank.setFluidInTank(0, water.copy());
                helper.runAfterDelay(25, () -> {
                    helper.assertTrue(copy.tank.getFluidInTank(0).getAmount() == water.getAmount() && destination.tank.isEmpty(), "Removed regulator still transferred fluid"); helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeUnlimitedFluidRegulatorKeepsLongThroughputAcrossActualLargeHatchCapabilities(GameTestHelper helper) {
        helper.setBlock(new BlockPos(4, 4, 4), NativeCoverTransferFixtures.FLUID_OUTPUT.getBlock());
        helper.setBlock(new BlockPos(5, 4, 4), NativeCoverTransferFixtures.FLUID_INPUT.getBlock());
        var source = (FluidHatchPartMachine) helper.getBlockEntity(new BlockPos(4, 4, 4));
        var destination = (FluidHatchPartMachine) helper.getBlockEntity(new BlockPos(5, 4, 4));
        source.setFrontFacing(Direction.NORTH); destination.setFrontFacing(Direction.NORTH);
        source.setWorkingEnabled(false); destination.setWorkingEnabled(false);
        var water = new FluidStack(Fluids.WATER, Integer.MAX_VALUE);
        for (int tank = 0; tank < source.tank.getTanks(); tank++) {
            var componentFluid = water.copy();
            componentFluid.set(DataComponents.CUSTOM_NAME, Component.literal("Native long cover transfer " + tank));
            source.tank.setFluidInTank(tank, componentFluid);
        }
        long amount = (long) Integer.MAX_VALUE * source.tank.getTanks();
        helper.assertTrue(amount > (long) Integer.MAX_VALUE * 4, "Large hatch fixture does not distinguish original long budget from int-clamped throughput");
        helper.assertTrue(source.getCoverContainer().placeCoverOnSide(Direction.EAST, GTItems.ELECTRIC_PUMP_LV.asStack(), GTLCovers.FLUID_REGULATORS_ULV.value(), null), "Native ULV regulator could not attach to the production Core large hatch");
        helper.runAfterDelay(10, () -> {
            long accepted = 0;
            for (int tank = 0; tank < destination.tank.getTanks(); tank++) {
                var fluid = destination.tank.getFluidInTank(tank);
                var expectedFluid = water.copy();
                expectedFluid.set(DataComponents.CUSTOM_NAME, Component.literal("Native long cover transfer " + tank));
                helper.assertTrue(FluidStack.isSameFluidSameComponents(expectedFluid, fluid), "Unlimited cover merged or replaced physical fluid components");
                accepted += fluid.getAmount();
            }
            helper.assertTrue(source.tank.isEmpty() && accepted == amount, "Native int capability calls truncated Core's original long cover budget: " + accepted + " / " + amount);
        });
        helper.runAfterDelay(30, () -> {
            long accepted = 0;
            for (int tank = 0; tank < destination.tank.getTanks(); tank++) accepted += destination.tank.getFluidInTank(tank).getAmount();
            helper.assertTrue(source.tank.isEmpty() && accepted == amount, "Unlimited cover duplicated fluid when its long budget refilled"); helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeMaxConveyorAndUnlimitedRobotArmMoveRealComponentStacksAndRestoreSettings(GameTestHelper helper) {
        var entries = new Holder[] {GTLCovers.CONVEYOR_MODULE_MAX, GTLCovers.ROBOT_ARM_ULV};
        for (int i = 0; i < 2; i++) {
            var sourcePos = new BlockPos(4, 4, 4 + i * 3);
            helper.setBlock(sourcePos, GTMachines.ITEM_EXPORT_BUS[GTValues.LV].getBlock());
            helper.setBlock(sourcePos.east(), GTMachines.ITEM_IMPORT_BUS[GTValues.LV].getBlock());
            var source = (ItemBusPartMachine) helper.getBlockEntity(sourcePos);
            var destination = (ItemBusPartMachine) helper.getBlockEntity(sourcePos.east());
            source.setFrontFacing(Direction.NORTH); destination.setFrontFacing(Direction.NORTH);
            source.setWorkingEnabled(false); destination.setWorkingEnabled(false);
            var iron = new ItemStack(Items.IRON_INGOT, 32); iron.set(DataComponents.CUSTOM_NAME, Component.literal("Native cover transfer " + i));
            source.getInventory().setStackInSlot(0, iron.copy());
            var item = GTItems.CONVEYOR_MODULE_LV.asStack();
            helper.assertTrue(source.getCoverContainer().placeCoverOnSide(Direction.EAST, item, (CoverDefinition) entries[i].value(), null), "Native Core conveyor/robot arm did not attach");
            var cover = (ConveyorCover) source.getCoverContainer().getCoverAtSide(Direction.EAST);
            final int index = i;
            helper.runAfterDelay(10, () -> {
                helper.assertTrue(source.getInventory().isEmpty() && ItemStack.matches(iron, destination.getInventory().getStackInSlot(0)), "Native Core cover failed actual component-aware transport at index " + index);
                cover.setTransferRate(index == 0 ? 501 : 123); cover.setWorkingEnabled(false);
                var saved = source.saveWithoutMetadata(helper.getLevel().registryAccess());
                var copy = (ItemBusPartMachine) GTMachines.ITEM_EXPORT_BUS[GTValues.LV].getBlockEntityType().create(source.getBlockPos(), source.getBlockState());
                helper.getLevel().removeBlockEntity(source.getBlockPos()); copy.loadAdditional(saved, helper.getLevel().registryAccess()); helper.getLevel().setBlockEntity(copy);
                var restored = (ConveyorCover) copy.getCoverContainer().getCoverAtSide(Direction.EAST);
                helper.assertTrue(restored != null && restored.getTransferRate() == (index == 0 ? 501 : 123) && !restored.isWorkingEnabled(), "Native cover lost rate or working state across block entity recreation");
            });
        }
        helper.runAfterDelay(15, helper::succeed);
    }
}
