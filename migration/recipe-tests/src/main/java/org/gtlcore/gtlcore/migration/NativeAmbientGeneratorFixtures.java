package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.machine.generator.LightningRodMachine;
import org.gtlcore.gtlcore.common.machine.generator.MagicEnergyMachine;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;

public final class NativeAmbientGeneratorFixtures {
    public static final MachineEntry<MachineDefinition> LIGHTNING = NativeMachineFixtures.REGISTRATE
            .machine("native_lightning_generator", info -> new LightningRodMachine(info, GTValues.EV))
            .tier(GTValues.EV).rotationState(RotationState.NON_Y_AXIS).register();
    public static final MachineEntry<MachineDefinition> MAGIC = NativeMachineFixtures.REGISTRATE
            .machine("native_magic_generator", info -> new MagicEnergyMachine(info, GTValues.LV))
            .tier(GTValues.LV).rotationState(RotationState.NON_Y_AXIS).register();
    public static final MachineEntry<MachineDefinition> ULV_MAGIC = NativeMachineFixtures.REGISTRATE
            .machine("native_ulv_magic_generator", info -> new MagicEnergyMachine(info, GTValues.ULV))
            .tier(GTValues.ULV).rotationState(RotationState.NON_Y_AXIS).register();
    public static void init() {}
}
