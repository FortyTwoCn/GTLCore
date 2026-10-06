package org.gtlcore.gtlcore.migration;

import com.gtladd.gtladditions.api.registry.NativeQueuedMachines;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.multiblock.pattern.BlockPattern;
import com.gregtechceu.gtceu.common.data.GTDimensionMarkers;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeQueuedRegistrationTest {
    @Test void registeredNativeFactoryAttachesExactlyOneFreshOwnedConfiguredRecipeTrait() {
        var entry = NativeQueuedMachineFixtures.MACHINE;
        var first = (NativeQueuedMachineFixtures.QueuedController) entry.getBlockEntityType()
                .create(BlockPos.ZERO, entry.getBlock().defaultBlockState());
        var second = (NativeQueuedMachineFixtures.QueuedController) entry.getBlockEntityType()
                .create(BlockPos.ZERO, entry.getBlock().defaultBlockState());
        assertNotNull(first); assertNotNull(second);
        assertInstanceOf(NativeQueuedMachineFixtures.QueuedLogic.class, first.getRecipeLogic());
        assertSame(first, first.getRecipeLogic().getMachine());
        assertNotSame(first.getRecipeLogic(), second.getRecipeLogic());
        assertEquals(1, first.getTraitHolder().getTraitsByInterface(com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic.class).size());
    }

    @Test void nativeSharedSymbolLimitsAndControllerSurviveTheDeferredPatternPatch() {
        var definition = NativeQueuedMachineFixtures.MACHINE.get();
        var pattern = (BlockPattern) definition.getStructurePatterns().get(MultiblockControllerMachine.DEFAULT_STRUCTURE).get();
        var shared = pattern.getPredicates().get('T');
        assertSame(shared, pattern.getPredicates().get('U'));
        var iron = shared.predicates().stream().filter(p -> p.getCandidates().stream()
                .anyMatch(c -> c.getBlockState().is(Blocks.IRON_BLOCK))).findFirst().orElseThrow();
        var gold = shared.predicates().stream().filter(p -> p.getCandidates().stream()
                .anyMatch(c -> c.getBlockState().is(Blocks.GOLD_BLOCK))).findFirst().orElseThrow();
        assertEquals(1, iron.getMinCount()); assertEquals(3, iron.getMaxCount());
        assertEquals(1, gold.getMaxCount());
        assertTrue(pattern.getPredicates().get('S').isController());
        assertTrue(pattern.getPredicates().get('A').isAny());
        assertTrue(pattern.getPredicates().get(' ').isAny());
    }

    @Test void boundNativeDefinitionsRejectLateFactoryOrPatternEdits() {
        assertThrows(IllegalStateException.class, () -> NativeQueuedMachines.builder(NativeQueuedMachineFixtures.MACHINE));
        assertThrows(IllegalStateException.class, () -> NativeQueuedMachines.replaceFactory(
                NativeQueuedMachineFixtures.MACHINE, NativeQueuedMachineFixtures.QueuedController::new));
    }

    @Test void nativeDimensionMarkersUseRealMinecraftDimensionRegistryIds() {
        assertEquals(ResourceLocation.parse("minecraft:overworld"), GTDimensionMarkers.OVERWORLD.unwrapKey().orElseThrow().location());
        assertEquals(ResourceLocation.parse("minecraft:the_nether"), GTDimensionMarkers.NETHER.unwrapKey().orElseThrow().location());
        assertEquals(ResourceLocation.parse("minecraft:the_end"), GTDimensionMarkers.END.unwrapKey().orElseThrow().location());
        assertSame(GTDimensionMarkers.NETHER_MARKER.asItem(), GTDimensionMarkers.NETHER.value().getIcon().getItem());
    }
}
