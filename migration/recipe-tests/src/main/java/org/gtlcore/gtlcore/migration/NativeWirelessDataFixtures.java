package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.machine.multiblock.part.WirelessOpticalDataHatchMachine;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.multiblock.pattern.MultiblockPatternBuilder;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.machines.GTResearchMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.research.DataBankMachine;
import net.minecraft.world.level.block.Blocks;
import static com.gregtechceu.gtceu.api.multiblock.Predicates.*;
import static com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection.*;

public final class NativeWirelessDataFixtures {
    public static final MachineEntry<MachineDefinition> TX = hatch("native_wireless_data_transmitter", true);
    public static final MachineEntry<MachineDefinition> RX = hatch("native_wireless_data_receiver", false);
    public static final MachineEntry<MultiblockMachineDefinition> BANK = bank("native_wireless_data_bank", false);
    public static final MachineEntry<MultiblockMachineDefinition> CHAIN = bank("native_wireless_data_chain", true);
    public static final MachineEntry<MultiblockMachineDefinition> RECEIVING = NativeMachineFixtures.REGISTRATE
            .multiblock("native_wireless_data_consumer", WorkableElectricMultiblockMachine::new)
            .rotationState(RotationState.NONE).allowExtendedFacing(false).allowFlip(false)
            .modelProperty(RecipeLogic.STATUS_PROPERTY, RecipeLogic.Status.IDLE)
            .appearanceBlock(() -> Blocks.IRON_BLOCK).recipeType(GTRecipeTypes.DUMMY_RECIPES)
            .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("SR")
                    .where('S', controller(definition)).where('R', machines(RX.get())).build()).register();
    private static MachineEntry<MachineDefinition> hatch(String name, boolean transmitter) {
        return NativeMachineFixtures.REGISTRATE.machine(name, info -> new WirelessOpticalDataHatchMachine(info, transmitter))
                .rotationState(RotationState.ALL)
                .abilities(transmitter ? PartAbility.OPTICAL_DATA_TRANSMISSION : PartAbility.OPTICAL_DATA_RECEPTION).register();
    }
    private static MachineEntry<MultiblockMachineDefinition> bank(String name, boolean chained) {
        return NativeMachineFixtures.REGISTRATE.multiblock(name, DataBankMachine::new)
                .rotationState(RotationState.NONE).allowExtendedFacing(false).allowFlip(false)
                .modelProperty(RecipeLogic.STATUS_PROPERTY, RecipeLogic.Status.IDLE)
                .appearanceBlock(() -> Blocks.IRON_BLOCK).recipeType(GTRecipeTypes.DUMMY_RECIPES)
                .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("SETDM")
                        .where('S', controller(definition))
                        .where('E', machines(GTMachines.ENERGY_INPUT_HATCH[GTValues.LuV].get()))
                        .where('T', machines(TX.get()))
                        .where('D', machines(chained ? RX.get() : GTResearchMachines.CREATIVE_DATA_ACCESS_HATCH.get()))
                        .where('M', machines(GTMachines.AUTO_MAINTENANCE_HATCH.get())).build()).register();
    }
    public static void init() {}
}
