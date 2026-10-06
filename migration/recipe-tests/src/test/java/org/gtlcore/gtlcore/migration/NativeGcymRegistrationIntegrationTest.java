package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.data.GTLMultiblockDisplays;
import org.gtlcore.gtlcore.common.data.GTLRecipeTypes;
import org.gtlcore.gtlcore.common.data.machines.GCyMMachines;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.CoilWorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.multiblock.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.multiblock.util.AbstractStructureHelper;
import com.gregtechceu.gtceu.api.multiblock.util.BlockInfo;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifierList;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.machines.GCYMMachines;
import com.gregtechceu.gtceu.common.data.machines.GTAEMachines;
import com.gregtechceu.gtceu.common.data.machines.GTResearchMachines;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ParallelHatchPartMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;

import brachy.modularui.value.sync.BooleanSyncValue;
import brachy.modularui.value.sync.IntSyncValue;
import brachy.modularui.value.sync.ModularSyncManager;
import brachy.modularui.value.sync.PanelSyncManager;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIntPair;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;

/** Actual native entries, preview generation and menu sync; no stand-in registry or machine definitions. */
class NativeGcymRegistrationIntegrationTest {
    @Test void allSharedIdsUseOneDeferredEntryAndNativeResearchAndAeInitializationStillRuns() throws ReflectiveOperationException {
        int shared = 0;
        for (var field : GCyMMachines.class.getDeclaredFields()) {
            if (field.getType() != MachineEntry.class || field.getName().equals("MEGA_BLAST_FURNACE")) continue;
            var entry = (MachineEntry<?>) field.get(null);
            assertSame(entry, GCYMMachines.class.getField(field.getName()).get(null));
            assertSame(entry.get(), GTRegistries.MACHINES.get(entry.getId()));
            shared++;
        }
        assertEquals(23, shared);
        assertEquals("gtceu:mega_blast_furnace", GCyMMachines.MEGA_BLAST_FURNACE.getId().toString());
        assertNotSame(GCyMMachines.MEGA_BLAST_FURNACE.get(), GCYMMachines.ROTARY_HEARTH_FURNACE.get());
        assertNotNull(GTResearchMachines.RESEARCH_STATION.get());
        assertNotNull(GTAEMachines.ME_PATTERN_BUFFER.get());
    }

    @Test void everyCoreGcymDefinitionHasACompleteNativePreview() throws ReflectiveOperationException {
        int count = 0;
        for (var field : GCyMMachines.class.getDeclaredFields()) {
            if (field.getType() != MachineEntry.class) continue;
            @SuppressWarnings("unchecked") var entry = (MachineEntry<MultiblockMachineDefinition>) field.get(null);
            var pattern = (BlockPattern) entry.get().getStructurePatterns().get("main").get();
            var repeats = new Int2IntOpenHashMap();
            for (int index = 0; index < pattern.getSlices().length; index++) repeats.put(index, pattern.getSlices()[index].getMinRepeats());
            var blocks = new LinkedHashMap<BlockPos, BlockInfo>();
            AbstractStructureHelper.blockPattern(repeats).populate(blocks, pattern, null, Direction.NORTH, Direction.UP, false);
            assertEquals(1, blocks.values().stream().filter(info -> info.getBlockState().is(entry.getBlock())).count(), entry.getId().toString());
            assertFalse(blocks.values().stream().anyMatch(info -> info.getBlockState().isAir()), entry.getId().toString());
            assertTrue(entry.get().isRenderWorldPreview()); assertTrue(entry.get().isRenderXEIPreview());
            count++;
        }
        assertEquals(24, count);
    }

    @Test void parallelHatchesKeepHighTiersSharedUseAndOriginalLimitsWithNativeClamping() {
        assertSame(GCYMMachines.PARALLEL_HATCH, GCyMMachines.PARALLEL_HATCH);
        for (int tier = GTValues.IV; tier <= GTValues.MAX; tier++) {
            var entry = GCyMMachines.PARALLEL_HATCH[tier];
            var hatch = (ParallelHatchPartMachine) entry.getBlockEntityType().create(BlockPos.ZERO, entry.getBlock().defaultBlockState());
            int maximum = (int) Math.pow(4, tier - 2);
            assertEquals(maximum, hatch.getCurrentParallel());
            hatch.setCurrentParallel(Integer.MAX_VALUE); assertEquals(maximum, hatch.getCurrentParallel());
            hatch.setCurrentParallel(0); assertEquals(1, hatch.getCurrentParallel());
            assertTrue(hatch.canShared(null, "main"));
        }
    }

    @Test void gcymPreviewHonorsExplicitInputCountsAndEnergyChoiceWithoutChangingFormationOrOtherMachines() {
        var pattern = (BlockPattern) GCyMMachines.LARGE_MACERATION_TOWER.get().getStructurePatterns().get("main").get();
        var repeats = new Int2IntOpenHashMap();
        for (int index = 0; index < pattern.getSlices().length; index++) repeats.put(index, pattern.getSlices()[index].getMinRepeats());
        var preview = AbstractStructureHelper.blockPattern(repeats);
        var predicate = pattern.getPredicates().get('X');
        var input = predicate.expand().stream().filter(base -> base.getCandidates().stream()
                .anyMatch(info -> PartAbility.IMPORT_ITEMS.isApplicable(info.getBlockState().getBlock()))).findFirst().orElseThrow();
        int originalMin = input.getMinCount(), originalMax = input.getMaxCount();
        var choice = IntIntPair.of(2, 2);
        preview.getMinMaxPreferences().put(predicate, input, choice);
        var lvEnergy = BlockInfo.fromBlockState(GTMachines.ENERGY_INPUT_HATCH[GTValues.LV].getBlock().defaultBlockState());
        var energy = predicate.expand().stream().filter(base -> base.getCandidates().stream()
                .anyMatch(info -> info.getBlockState().is(lvEnergy.getBlockState().getBlock()))).findFirst().orElseThrow();
        preview.getBlockPreferences().put(predicate, energy, lvEnergy);
        var blocks = new LinkedHashMap<BlockPos, BlockInfo>();
        preview.populate(blocks, pattern, null, Direction.NORTH, Direction.UP, false);
        assertSame(choice, preview.getMinMaxPreferences().get(predicate, input));
        assertEquals(2, blocks.values().stream().filter(info -> PartAbility.IMPORT_ITEMS.isApplicable(info.getBlockState().getBlock())).count());
        assertEquals(1, blocks.values().stream().filter(info -> info.getBlockState().is(lvEnergy.getBlockState().getBlock())).count());
        assertEquals(originalMin, input.getMinCount()); assertEquals(originalMax, input.getMaxCount());

        var nativePattern = (BlockPattern) GTMultiMachines.ELECTRIC_BLAST_FURNACE.get().getStructurePatterns().get("main").get();
        var nativeRepeats = new Int2IntOpenHashMap();
        for (int index = 0; index < nativePattern.getSlices().length; index++) nativeRepeats.put(index, nativePattern.getSlices()[index].getMinRepeats());
        var nativePreview = AbstractStructureHelper.blockPattern(nativeRepeats);
        nativePreview.populate(new LinkedHashMap<>(), nativePattern, null, Direction.NORTH, Direction.UP, false);
        assertTrue(nativePreview.getMinMaxPreferences().isEmpty(), "Core defaults escaped into an unrelated native GT machine");
    }

    @Test void arcSmelterLightningDurationAppliesToTheDeferredRecipeTypeWithoutMutatingItsSource() {
        var entry = GCyMMachines.LARGE_ARC_SMELTER;
        var machine = (WorkableElectricMultiblockMachine) entry.getBlockEntityType().create(BlockPos.ZERO, entry.getBlock().defaultBlockState());
        var modifiers = (RecipeModifierList) entry.get().getRecipeModifier();
        var lightning = GTLRecipeTypes.LIGHTNING_PROCESSOR_RECIPES.recipeBuilder("native_gcym_lightning")
                .inputItems(Items.IRON_INGOT).duration(10).EUt(32).build();
        var modified = modifiers.getModifiers()[0].applyModifier(machine, lightning);
        assertNotNull(modified); assertEquals(40, modified.duration); assertEquals(10, lightning.duration);
        var arc = GTRecipeTypes.ARC_FURNACE_RECIPES.recipeBuilder("native_gcym_arc").duration(10).EUt(32).build();
        assertEquals(10, modifiers.getModifiers()[0].applyModifier(machine, arc).duration);
        assertTrue(java.util.Arrays.asList(entry.get().getRecipeTypes()).contains(GTLRecipeTypes.LIGHTNING_PROCESSOR_RECIPES.get()));
    }

    @Test void coilTemperatureWidgetsUseNativeSyncValuesAndReuseTheMenusFormedState() {
        var entry = GCyMMachines.BLAST_ALLOY_SMELTER;
        var machine = (CoilWorkableElectricMultiblockMachine) entry.getBlockEntityType().create(BlockPos.ZERO, entry.getBlock().defaultBlockState());
        var sync = new PanelSyncManager(new ModularSyncManager(false), true);
        var formed = sync.getOrCreateSyncHandler("isFormed", BooleanSyncValue.class, () -> new BooleanSyncValue(machine::isFormed));
        assertEquals(1, GTLMultiblockDisplays.MAX_TEMPERATURE.apply(machine, sync).size());
        assertEquals(1, GTLMultiblockDisplays.COIL_PARALLEL.apply(machine, sync).size());
        assertSame(formed, sync.getOrCreateSyncHandler("isFormed", BooleanSyncValue.class, () -> new BooleanSyncValue(() -> true)));
        assertFalse(formed.getBoolValue());
        var temperature = sync.getOrCreateSyncHandler("gtlCoilTemperature", IntSyncValue.class, () -> { throw new AssertionError("Missing temperature sync"); });
        assertEquals(machine.getCoilType().getCoilTemperature(), temperature.getIntValue());
    }
}
