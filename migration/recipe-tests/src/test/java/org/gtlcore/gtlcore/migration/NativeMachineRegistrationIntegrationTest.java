package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.machine.multiblock.generator.GTLLargeCombustionEngineMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.generator.GeneratorArrayMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.generator.MegaTurbineMachine;
import org.gtlcore.gtlcore.common.data.machines.GTLGeneratorMachines;
import org.gtlcore.gtlcore.common.data.GTLRecipeTypes;
import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.SimpleGeneratorMachine;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;
import com.gregtechceu.gtceu.common.machine.multiblock.part.DualHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gregtechceu.gtceu.common.machine.storage.BufferMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static com.gregtechceu.gtceu.api.GTValues.*;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies GT's real registered IDs, native factories and storage; no replacement definition fixtures. */
class NativeMachineRegistrationIntegrationTest {
    private static MetaMachine create(MachineEntry<? extends MachineDefinition> entry) {
        return entry.getBlockEntityType().create(BlockPos.ZERO, entry.getBlock().defaultBlockState());
    }

    @Test void nativeDeferredEntriesIncludeOriginalLowAndHighTierParts() {
        int maximum = GTCEuAPI.isHighTier() ? MAX : UHV;
        for (int tier = LV; tier <= maximum; tier++) {
            for (var entries : new MachineEntry[][] { GTMachines.ITEM_IMPORT_BUS, GTMachines.ITEM_EXPORT_BUS,
                    GTMachines.ENERGY_INPUT_HATCH_4A, GTMachines.ENERGY_OUTPUT_HATCH_4A,
                    GTMachines.ENERGY_INPUT_HATCH_16A, GTMachines.ENERGY_OUTPUT_HATCH_16A,
                    GTMachines.DUAL_IMPORT_HATCH, GTMachines.DUAL_EXPORT_HATCH, GTMachines.BUFFER }) {
                assertNotNull(entries[tier], "Missing native tier " + tier);
                assertEquals(tier, entries[tier].getTier());
                assertEquals("gtceu", entries[tier].getId().getNamespace());
            }
        }
        assertNotNull(GTMachines.ITEM_IMPORT_BUS[ULV]); assertNotNull(GTMachines.ITEM_EXPORT_BUS[ULV]);
        assertNotNull(GTMachines.LASER_INPUT_HATCH_256[IV]); assertNotNull(GTMachines.LASER_OUTPUT_HATCH_4096[IV]);
        for (int tier = HV; tier <= UEV; tier++) {
            assertNotNull(GTMachines.ROTOR_HOLDER[tier]);
            assertTrue(GTMachines.ROTOR_HOLDER[tier].get().defaultRenderState().hasProperty(GTMachineModelProperties.HAS_ROTOR));
        }
        assertNull(GTMachines.ROTOR_HOLDER[UIV]);
    }

    @Test void busCapacityAndCircuitConfigurationUseNativeAttachedTraits() {
        int tier = GTCEuAPI.isHighTier() ? MAX : UHV;
        var input = assertInstanceOf(ItemBusPartMachine.class, create(GTMachines.ITEM_IMPORT_BUS[tier]));
        var output = assertInstanceOf(ItemBusPartMachine.class, create(GTMachines.ITEM_EXPORT_BUS[tier]));
        assertEquals((tier + 1) * (tier + 1), input.getInventory().getSlots());
        var circuit = IntCircuitBehaviour.stack(12);
        assertFalse(input.getInventory().insertItem(0, circuit, false).isEmpty());
        assertTrue(input.getInventory().getStackInSlot(0).isEmpty());
        assertTrue(output.getInventory().storage.isItemValid(0, circuit));
        assertTrue(input.getCircuitSlot().isEnabled()); assertFalse(output.getCircuitSlot().isEnabled());
        input.getCircuitSlot().setCurrentCircuit(12); assertEquals(12, input.getCircuitSlot().getCurrentCircuit());
        assertTrue(input.getInventory().insertItem(0, new ItemStack(Items.DIAMOND), false).isEmpty());
        input.setDistinct(true); assertTrue(input.isDistinct()); input.setDistinct(false); assertFalse(input.isDistinct());
    }

    @Test void lowTierDualHatchesHaveNonzeroFluidCapacityAndBothStorages() {
        for (int tier : new int[] { LV, IV, GTCEuAPI.isHighTier() ? MAX : UHV }) {
            var dual = assertInstanceOf(DualHatchPartMachine.class, create(GTMachines.DUAL_IMPORT_HATCH[tier]));
            assertEquals((tier + 1) * (tier + 1), dual.getInventory().getSlots());
            assertEquals(tier + 1, dual.tank.getTanks());
            assertEquals(16_000 * (1L << tier), dual.tank.getTankCapacity(0));
            assertTrue(dual.tank.getTankCapacity(0) > 0);
            var buffer = assertInstanceOf(BufferMachine.class, create(GTMachines.BUFFER[tier]));
            assertEquals(tier + 2, buffer.getTank().getTanks());
            assertEquals(16_000 * (1L << tier), buffer.getTank().getTankCapacity(0));
            assertEquals((tier + 2) * (tier + 2), buffer.getInventory().getSlots());
        }
    }

    @Test void registeredGeneratorsRetainNativeMenuAndConfiguredOutputAmperage() {
        var entry = GTMachines.COMBUSTION[LV];
        var generator = assertInstanceOf(SimpleGeneratorMachine.class, create(entry));
        assertEquals(V[LV], generator.energyContainer.getOutputVoltage());
        assertEquals(GeneratorArrayMachine.getAmperage(LV), generator.energyContainer.getOutputAmperage());
        assertEquals(V[LV] * GeneratorArrayMachine.getAmperage(LV) * 64, generator.energyContainer.getEnergyCapacity());
        assertNotNull(entry.get().getUI());
        assertTrue(entry.get().defaultRenderState().hasProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS));
    }

    @Test void originalGtMultiblockIdsCreateProductionCoreGeneratorClasses() {
        var engine = assertInstanceOf(GTLLargeCombustionEngineMachine.class, create(GTMultiMachines.LARGE_COMBUSTION_ENGINE));
        assertEquals(EV, engine.getTier()); assertEquals("large_combustion_engine", GTMultiMachines.LARGE_COMBUSTION_ENGINE.getId().getPath());
        assertInstanceOf(GTLLargeCombustionEngineMachine.class, create(GTMultiMachines.EXTREME_COMBUSTION_ENGINE));
        for (var entry : new MachineEntry[] { GTMultiMachines.LARGE_STEAM_TURBINE,
                GTMultiMachines.LARGE_GAS_TURBINE, GTMultiMachines.LARGE_PLASMA_TURBINE }) {
            assertInstanceOf(MegaTurbineMachine.class, create(entry));
            assertTrue(((MachineDefinition) entry.get()).defaultRenderState().hasProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS));
        }
    }

    @Test void coreFuelGeneratorsKeepOriginalIdsAndBindDeferredRecipeTypes() {
        var entries = java.util.List.of(GTLGeneratorMachines.SEMI_FLUID_GENERATOR[LV],
                GTLGeneratorMachines.ROCKET_ENGINE_GENERATOR[EV], GTLGeneratorMachines.NAQUADAH_REACTOR_GENERATOR[IV]);
        var types = java.util.List.of(GTLRecipeTypes.SEMI_FLUID_GENERATOR_FUELS.get(),
                GTLRecipeTypes.ROCKET_ENGINE_FUELS.get(), GTLRecipeTypes.NAQUADAH_REACTOR.get());
        var ids = java.util.List.of("lv_semi_fluid", "ev_rocket_engine", "iv_naquadah_reactor");
        for (int index = 0; index < entries.size(); index++) {
            var entry = entries.get(index); var generator = assertInstanceOf(SimpleGeneratorMachine.class, create(entry));
            assertEquals("gtceu:" + ids.get(index), entry.getId().toString());
            assertSame(types.get(index), generator.getRecipeType());
            assertEquals(GeneratorArrayMachine.getAmperage(generator.getTier()), generator.energyContainer.getOutputAmperage());
            assertNotNull(entry.get().getUI());
        }
        var semi = assertInstanceOf(GTLLargeCombustionEngineMachine.class, create(GTLGeneratorMachines.LARGE_SEMI_FLUID_GENERATOR));
        assertEquals("gtceu:large_semi_fluid_generator", semi.getDefinition().getId().toString());
        assertSame(GTLRecipeTypes.SEMI_FLUID_GENERATOR_FUELS.get(), semi.getRecipeType());
        var rocket = assertInstanceOf(MegaTurbineMachine.class, create(GTLGeneratorMachines.ROCKET_LARGE_TURBINE));
        assertEquals("gtceu:rocket_large_turbine", rocket.getDefinition().getId().toString());
        assertSame(GTLRecipeTypes.ROCKET_ENGINE_FUELS.get(), rocket.getRecipeType());
    }
}
