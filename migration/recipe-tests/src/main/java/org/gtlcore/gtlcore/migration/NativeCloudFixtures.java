package org.gtlcore.gtlcore.migration;

import com.gtladd.gtladditions.common.machine.CloudOpticalDataMachine;
import com.gtladd.gtladditions.common.machine.CloudOpticalComputationMonitorMachine;
import com.gtladd.gtladditions.common.machine.hatch.CloudOpticalComputationHatchMachine;
import com.gtladd.gtladditions.common.machine.hatch.CloudOpticalDataHatchMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.ComputationProviderMachine;
import com.gregtechceu.gtceu.api.machine.*;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import com.gregtechceu.gtceu.api.multiblock.pattern.MultiblockPatternBuilder;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import net.minecraft.world.level.block.Blocks;
import static com.gregtechceu.gtceu.api.multiblock.Predicates.*;
import static com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection.*;

public final class NativeCloudFixtures {
    public static final MachineEntry<MachineDefinition> DATA = NativeMachineFixtures.REGISTRATE.machine("native_cloud_data", CloudOpticalDataMachine::new).register();
    public static final MachineEntry<MachineDefinition> MONITOR = NativeMachineFixtures.REGISTRATE.machine("native_cloud_monitor", CloudOpticalComputationMonitorMachine::new).register();
    public static final MachineEntry<MachineDefinition> DATA_HATCH = NativeMachineFixtures.REGISTRATE.machine("native_cloud_data_hatch", CloudOpticalDataHatchMachine::new).register();
    public static final MachineEntry<MachineDefinition> TX = NativeMachineFixtures.REGISTRATE.machine("native_cloud_computation_tx", info -> new CloudOpticalComputationHatchMachine(info, true)).register();
    public static final MachineEntry<MachineDefinition> RX = NativeMachineFixtures.REGISTRATE.machine("native_cloud_computation_rx", info -> new CloudOpticalComputationHatchMachine(info, false)).register();
    public static final MachineEntry<MultiblockMachineDefinition> PROVIDER = NativeMachineFixtures.REGISTRATE
            .multiblock("native_cloud_provider", info -> new ComputationProviderMachine(info, false))
            .rotationState(RotationState.NONE).allowExtendedFacing(false).allowFlip(false)
            .modelProperty(com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic.STATUS_PROPERTY,
                    com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic.Status.IDLE)
            .recipeType(GTRecipeTypes.DUMMY_RECIPES).appearanceBlock(() -> Blocks.IRON_BLOCK)
            .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("SEIT")
                    .where('S', controller(definition)).where('E', machines(GTMachines.ENERGY_INPUT_HATCH[com.gregtechceu.gtceu.api.GTValues.UIV].get()))
                    .where('I', machines(GTMachines.ITEM_IMPORT_BUS[com.gregtechceu.gtceu.api.GTValues.LV].get())).where('T', machines(TX.get())).build()).register();
    public static final MachineEntry<MultiblockMachineDefinition> CONSUMER = NativeMachineFixtures.REGISTRATE
            .multiblock("native_cloud_consumer", WorkableElectricMultiblockMachine::new)
            .rotationState(RotationState.NONE).allowExtendedFacing(false).allowFlip(false)
            .recipeType(GTRecipeTypes.DUMMY_RECIPES).appearanceBlock(() -> Blocks.IRON_BLOCK)
            .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("SR")
                    .where('S', controller(definition)).where('R', machines(RX.get())).build()).register();
    public static void init() {}
    private NativeCloudFixtures() {}
}
