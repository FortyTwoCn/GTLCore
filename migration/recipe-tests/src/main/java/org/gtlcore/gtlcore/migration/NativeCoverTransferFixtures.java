package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.machine.multiblock.part.HugeFluidHatchPartMachine;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;

/** Real production Core large hatches with separate fixture block IDs. */
public final class NativeCoverTransferFixtures {
    public static final MachineEntry<MachineDefinition> FLUID_OUTPUT = NativeMachineFixtures.REGISTRATE
            .machine("native_cover_large_output", info -> new HugeFluidHatchPartMachine(info, GTValues.MAX, IO.OUT))
            .rotationState(RotationState.ALL).register();
    public static final MachineEntry<MachineDefinition> FLUID_INPUT = NativeMachineFixtures.REGISTRATE
            .machine("native_cover_large_input", info -> new HugeFluidHatchPartMachine(info, GTValues.MAX, IO.IN))
            .rotationState(RotationState.ALL).register();
    public static void init() {}
}
