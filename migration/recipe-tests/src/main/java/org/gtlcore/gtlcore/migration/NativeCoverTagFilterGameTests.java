package org.gtlcore.gtlcore.migration;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.cover.filter.TagFilter;
import com.gregtechceu.gtceu.common.cover.ItemFilterCover;
import com.gregtechceu.gtceu.common.cover.FluidFilterCover;
import com.gregtechceu.gtceu.common.data.GTCovers;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeCoverTagFilterGameTests {
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeGTItemTagCoverFiltersRealCapabilitiesAndRestoresItsComponentExpression(GameTestHelper helper) {
        var entry = GTMachines.ITEM_IMPORT_BUS[GTValues.LV];
        var position = new BlockPos(4, 4, 4); helper.setBlock(position, entry.getBlock());
        var bus = (ItemBusPartMachine) helper.getBlockEntity(position); bus.setFrontFacing(Direction.NORTH);
        var attached = GTItems.TAG_FILTER.asStack(); attached.set(DataComponents.CUSTOM_NAME, Component.literal("Native saved item filter"));
        helper.assertTrue(bus.getCoverContainer().placeCoverOnSide(Direction.SOUTH, attached, GTCovers.ITEM_FILTER.value(), null), "Native tag item filter could not attach to the original GT import bus");
        var cover = (ItemFilterCover) bus.getCoverContainer().getCoverAtSide(Direction.SOUTH);
        var filter = (TagFilter<?, ?>) cover.getItemFilter(); filter.setFilterString("ingots/* & !ingots/gold");
        var copper = new ItemStack(Items.COPPER_INGOT, 3); copper.set(DataComponents.CUSTOM_NAME, Component.literal("Native filtered copper"));
        var handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, bus.getBlockPos(), Direction.SOUTH);
        helper.assertTrue(handler != null && handler.insertItem(0, new ItemStack(Items.GOLD_INGOT), false).is(Items.GOLD_INGOT) && bus.getInventory().isEmpty(), "Native cover admitted a rejected tag or mutated its real bus");
        helper.assertTrue(handler.insertItem(0, copper, true).isEmpty() && bus.getInventory().isEmpty(), "Native tag cover simulation consumed physical input");
        helper.assertTrue(handler.insertItem(0, copper, false).isEmpty() && ItemStack.matches(bus.getInventory().getStackInSlot(0), copper), "Native item cover lost accepted input counts or components");
        filter.setFilterString("ingots/gold");
        helper.assertTrue(!cover.getItemFilter().test(copper) && cover.getItemFilter().test(new ItemStack(Items.GOLD_INGOT)), "Native cover kept a stale result after its expression changed");
        filter.setFilterString("ingots/* & !ingots/gold");
        var saved = bus.saveWithoutMetadata(helper.getLevel().registryAccess());
        var copy = (ItemBusPartMachine) entry.getBlockEntityType().create(bus.getBlockPos(), bus.getBlockState());
        helper.getLevel().removeBlockEntity(bus.getBlockPos()); copy.loadAdditional(saved, helper.getLevel().registryAccess()); helper.getLevel().setBlockEntity(copy);
        var restored = (ItemFilterCover) copy.getCoverContainer().getCoverAtSide(Direction.SOUTH);
        helper.assertTrue(restored != null && restored.getItemFilter().test(copper) && !restored.getItemFilter().test(new ItemStack(Items.GOLD_INGOT)) && ((TagFilter<?, ?>) restored.getItemFilter()).getFilterString().equals("ingots/* & !ingots/gold"), "Native item tag-cover component codec lost its expression or matching behavior");
        helper.assertTrue(restored.getPickItem().get(DataComponents.CUSTOM_NAME).equals(attached.get(DataComponents.CUSTOM_NAME)) && ItemStack.matches(copy.getInventory().getStackInSlot(0), copper), "Native item tag-cover reload lost unrelated components or physical inventory");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeGTFluidTagCoverFiltersRealComponentFluidsAndRestoresItsExpression(GameTestHelper helper) {
        var entry = GTMachines.FLUID_IMPORT_HATCH[GTValues.LV];
        var position = new BlockPos(4, 4, 4); helper.setBlock(position, entry.getBlock());
        var hatch = (FluidHatchPartMachine) helper.getBlockEntity(position); hatch.setFrontFacing(Direction.NORTH);
        var attached = GTItems.TAG_FLUID_FILTER.asStack(); attached.set(DataComponents.CUSTOM_NAME, Component.literal("Native saved fluid filter"));
        helper.assertTrue(hatch.getCoverContainer().placeCoverOnSide(Direction.SOUTH, attached, GTCovers.FLUID_FILTER.value(), null), "Native tag fluid filter could not attach to the original GT input hatch");
        var cover = (FluidFilterCover) hatch.getCoverContainer().getCoverAtSide(Direction.SOUTH);
        var filter = (TagFilter<?, ?>) cover.getFluidFilter(); filter.setFilterString("water & !lava");
        var water = new FluidStack(Fluids.WATER, 700); water.set(DataComponents.CUSTOM_NAME, Component.literal("Native filtered water"));
        var lava = new FluidStack(Fluids.LAVA, 700);
        var handler = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, hatch.getBlockPos(), Direction.SOUTH);
        helper.assertTrue(handler != null && handler.fill(lava, IFluidHandler.FluidAction.EXECUTE) == 0 && hatch.tank.isEmpty(), "Native fluid tag cover admitted a rejected fluid or mutated its actual tank");
        helper.assertTrue(handler.fill(water, IFluidHandler.FluidAction.SIMULATE) == 700 && hatch.tank.isEmpty(), "Native fluid tag cover simulation consumed physical fluid");
        helper.assertTrue(handler.fill(water, IFluidHandler.FluidAction.EXECUTE) == 700 && hatch.tank.getFluidInTank(0).getAmount() == 700 && FluidStack.isSameFluidSameComponents(hatch.tank.getFluidInTank(0), water), "Native fluid tag cover lost accepted quantities or components");
        filter.setFilterString("lava");
        helper.assertTrue(!cover.getFluidFilter().test(water) && cover.getFluidFilter().test(lava), "Native fluid cover kept a stale result after its expression changed");
        filter.setFilterString("water & !lava");
        var saved = hatch.saveWithoutMetadata(helper.getLevel().registryAccess());
        var copy = (FluidHatchPartMachine) entry.getBlockEntityType().create(hatch.getBlockPos(), hatch.getBlockState());
        helper.getLevel().removeBlockEntity(hatch.getBlockPos()); copy.loadAdditional(saved, helper.getLevel().registryAccess()); helper.getLevel().setBlockEntity(copy);
        var restored = (FluidFilterCover) copy.getCoverContainer().getCoverAtSide(Direction.SOUTH);
        helper.assertTrue(restored != null && restored.getFluidFilter().test(water) && !restored.getFluidFilter().test(lava) && ((TagFilter<?, ?>) restored.getFluidFilter()).getFilterString().equals("water & !lava"), "Native fluid tag-cover component codec lost its expression or matching behavior");
        helper.assertTrue(restored.getPickItem().get(DataComponents.CUSTOM_NAME).equals(attached.get(DataComponents.CUSTOM_NAME)) && copy.tank.getFluidInTank(0).getAmount() == 700 && FluidStack.isSameFluidSameComponents(copy.tank.getFluidInTank(0), water), "Native fluid tag-cover reload lost unrelated components or physical fluid");
        helper.succeed();
    }
}
