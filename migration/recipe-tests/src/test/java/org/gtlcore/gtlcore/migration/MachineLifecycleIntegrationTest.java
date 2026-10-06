package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.machine.multiblock.NoEnergyMultiblockMachine;
import org.gtlcore.gtlcore.api.pattern.PatternMatchData;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.AdvancedAssemblyLineMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.StorageMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.TierCasingMachine;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.multiblock.pattern.PatternState;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Native block entities and recipe traits; world ticking and GUI interaction have separate acceptance tests. */
class MachineLifecycleIntegrationTest {
    private static BlockEntityCreationInfo info() {
        var definition = GTMultiMachines.VACUUM_FREEZER;
        return new BlockEntityCreationInfo(definition.getBlockEntityType(), BlockPos.ZERO,
                definition.getBlock().defaultBlockState());
    }

    private static void form(WorkableMultiblockMachine machine) {
        machine.getDefaultPatternState().setState(PatternState.CheckState.VALID_UNCACHED);
        machine.formStructure(WorkableMultiblockMachine.DEFAULT_STRUCTURE);
        assertTrue(machine.isFormed());
    }

    @Test
    void casingTierFollowsNativeFormationAndInvalidation() {
        var machine = new TierCasingMachine(info(), "CATier");
        PatternMatchData.of(machine.getDefaultPatternState().getContext()).matchTier("CATier", 6);
        form(machine);
        assertEquals(6, machine.getCasingTier());
        machine.invalidateStructure("main");
        assertFalse(machine.isFormed());
        assertEquals(0, machine.getCasingTier());
        machine.getDefaultPatternState().resetContext(true);
        PatternMatchData.of(machine.getDefaultPatternState().getContext()).matchTier("CATier", 2);
        form(machine);
        assertEquals(2, machine.getCasingTier());
    }

    @Test
    void insufficientCasingTierRejectsTheRecipeAndRecordsANativeFailureReason() {
        var machine = new TierCasingMachine(info(), "CATier");
        PatternMatchData.of(machine.getDefaultPatternState().getContext()).matchTier("CATier", 2);
        form(machine);
        GTRecipe recipe = GTRecipeTypes.VACUUM_RECIPES.recipeBuilder("gtl_tier_test").duration(100).build();
        recipe.data.putInt("CATier", 3);
        assertFalse(machine.beforeWorking(recipe));
        assertNotNull(machine.getRecipeLogic().getBestFailureReason());
        assertEquals("gtceu.recipe.fail.no.enough.recipe.tier",
                ((net.minecraft.network.chat.contents.TranslatableContents)
                        machine.getRecipeLogic().getBestFailureReason().getContents()).getKey());
        recipe.data.putInt("CATier", 2);
        assertTrue(machine.beforeWorking(recipe));
    }

    @Test
    void assemblySpeedUsesTheCurrentMatchedUnitsAndResetsAfterDisassembly() {
        var machine = new AdvancedAssemblyLineMachine(info());
        var data = PatternMatchData.of(machine.getDefaultPatternState().getContext());
        for (int i = 0; i < 28; i++) data.countBlock("Unit", new BlockPos(i, 64, 0));
        form(machine);
        assertEquals(72, machine.getSpeed());
        var original = GTRecipeTypes.ASSEMBLY_LINE_RECIPES.recipeBuilder("gtl_assembly_speed_test")
                .inputItems(Items.IRON_INGOT).duration(100).build();
        var modified = AdvancedAssemblyLineMachine.recipeModifier(machine, original).apply(original);
        assertNotNull(modified);
        assertEquals(72, modified.duration);
        assertEquals(100, original.duration);
        machine.invalidateStructure("main");
        assertEquals(0, machine.getSpeed());
    }

    @Test
    void assemblyDurationHasAMinimumOfOneTick() {
        var machine = new AdvancedAssemblyLineMachine(info());
        var data = PatternMatchData.of(machine.getDefaultPatternState().getContext());
        for (int i = 0; i < 100; i++) data.countBlock("Unit", new BlockPos(i, 64, 0));
        form(machine);
        var recipe = GTRecipeTypes.ASSEMBLY_LINE_RECIPES.recipeBuilder("gtl_assembly_minimum_test")
                .duration(100).build();
        assertEquals(1, AdvancedAssemblyLineMachine.recipeModifier(machine, recipe).apply(recipe).duration);
    }

    @Test
    void storageSlotRetainsNativeLimitsAndIsNotARecipeInput() {
        var machine = new StorageMachine(info(), 1);
        assertEquals(IO.NONE, machine.machineStorage.getHandlerIO());
        assertEquals(1, machine.machineStorage.getSlotLimit(0));
        var leftover = machine.machineStorage.insertItem(0, new ItemStack(Items.IRON_INGOT, 4), false);
        assertEquals(3, leftover.getCount());
        assertEquals(1, machine.getMachineStorageItem().getCount());
        form(machine);
        assertTrue(machine.getCapabilitiesFlat(IO.IN, ItemRecipeCapability.CAP).isEmpty());
        assertTrue(machine.getCapabilitiesFlat(IO.OUT, ItemRecipeCapability.CAP).isEmpty());
    }

    @Test
    void explicitRecipeTraitsAreAttachedToTheirNativeOwner() {
        var tierLogic = new RecipeLogic();
        var tier = new TierCasingMachine(info(), "CATier", tierLogic);
        assertSame(tierLogic, tier.getRecipeLogic());
        assertSame(tier, tierLogic.getMachine());
        var nonElectricLogic = new RecipeLogic();
        var nonElectric = new NoEnergyMultiblockMachine(info(), nonElectricLogic);
        assertSame(nonElectricLogic, nonElectric.getRecipeLogic());
        assertSame(nonElectric, nonElectricLogic.getMachine());
    }

    @Test
    void storageItemsAndComponentsRoundTripThroughNativeBlockEntitySaving() {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var source = new StorageMachine(info(), 64);
        var stack = new ItemStack(Items.DIAMOND, 37);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("native storage test"));
        source.setMachineStorageItem(stack);
        var tag = source.saveWithoutMetadata(registries);
        var restored = new StorageMachine(info(), 64);
        restored.loadAdditional(tag, registries);
        assertEquals(37, restored.getMachineStorageItem().getCount());
        assertTrue(ItemStack.isSameItemSameComponents(stack, restored.getMachineStorageItem()));
        assertEquals(64, restored.machineStorage.getSlotLimit(0));
        assertEquals(IO.NONE, restored.machineStorage.getHandlerIO());
    }

    @Test
    void casingTierSynchronizesThroughTheNativeInitialBlockEntityPacket() {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var source = new TierCasingMachine(info(), "CATier");
        PatternMatchData.of(source.getDefaultPatternState().getContext()).matchTier("CATier", 5);
        form(source);
        var clientCopy = new TierCasingMachine(info(), "CATier");
        clientCopy.handleUpdateTag(source.getUpdateTag(registries), registries);
        assertTrue(clientCopy.isFormed());
        assertEquals(5, clientCopy.getCasingTier());
    }
}
