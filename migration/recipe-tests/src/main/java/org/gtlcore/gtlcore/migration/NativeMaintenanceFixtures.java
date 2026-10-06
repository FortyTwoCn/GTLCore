package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.machine.multiblock.part.maintenance.*;
import org.gtlcore.gtlcore.api.machine.multiblock.NoEnergyMultiblockMachine;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.multiblock.pattern.MultiblockPatternBuilder;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import static com.gregtechceu.gtceu.api.multiblock.Predicates.*;
import static com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection.*;

public final class NativeMaintenanceFixtures {
    public static final MachineEntry<MachineDefinition> AUTO = NativeMachineFixtures.REGISTRATE.machine("native_auto_config_maintenance", AutoConfigurationMaintenanceHatchPartMachine::new)
            .rotationState(RotationState.ALL).abilities(PartAbility.MAINTENANCE).register();
    public static final MachineEntry<MachineDefinition> CLEAN = NativeMachineFixtures.REGISTRATE.machine("native_clean_config_maintenance", info -> new CleaningConfigurationMaintenanceHatchPartMachine(info, ICleaningRoom.LAW_DUMMY_CLEANROOM))
            .rotationState(RotationState.ALL).abilities(PartAbility.MAINTENANCE).register();
    public static final MachineEntry<MachineDefinition> AUTOMATIC_CLEAN = NativeMachineFixtures.REGISTRATE.machine("native_clean_maintenance", info -> new GTLCleaningMaintenanceHatchPartMachine(info, ICleaningRoom.STERILE_DUMMY_CLEANROOM))
            .rotationState(RotationState.ALL).abilities(PartAbility.MAINTENANCE).register();
    public static final MachineEntry<MachineDefinition> GRAVITY = NativeMachineFixtures.REGISTRATE.machine("native_gravity_config_maintenance", info -> new GravityCleaningConfigurationMaintenancePartMachine(info, ICleaningRoom.LAW_DUMMY_CLEANROOM))
            .rotationState(RotationState.ALL).abilities(PartAbility.MAINTENANCE).register();
    public static final MachineEntry<MachineDefinition> GRAVITY_ONLY = NativeMachineFixtures.REGISTRATE.machine("native_gravity_only_maintenance", info -> new GravityCleaningConfigurationMaintenancePartMachine(info, false))
            .rotationState(RotationState.ALL).abilities(PartAbility.MAINTENANCE).register();
    public static final MachineEntry<MultiblockMachineDefinition> CONTROLLER = NativeMachineFixtures.REGISTRATE.multiblock("native_maintenance_controller", NoEnergyMultiblockMachine::new)
            .rotationState(RotationState.ALL).recipeType(GTRecipeTypes.CHEMICAL_RECIPES).appearanceBlock(() -> net.minecraft.world.level.block.Blocks.IRON_BLOCK).allowFlip(false)
            .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("SM").where('S', controller(definition)).where('M', machines(GRAVITY.get())).build()).register();
    public static void init() {}
}
