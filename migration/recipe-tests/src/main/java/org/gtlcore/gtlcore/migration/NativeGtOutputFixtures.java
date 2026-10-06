package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.machine.multiblock.NoEnergyMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.*;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import com.gregtechceu.gtceu.api.multiblock.pattern.MultiblockPatternBuilder;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import net.minecraft.world.level.block.Blocks;
import static com.gregtechceu.gtceu.api.multiblock.Predicates.*;
import static com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection.*;

/** Actual GT/GTMThings classes and their production Core mixins, with separate fixture registration IDs. */
public final class NativeGtOutputFixtures {
    public static final MachineEntry<MachineDefinition> ITEM = NativeMachineFixtures.REGISTRATE
            .machine("native_gt_output_item", com.gregtechceu.gtceu.integration.ae2.machine.MEOutputBusPartMachine::new)
            .rotationState(RotationState.ALL).register();
    public static final MachineEntry<MachineDefinition> FLUID = NativeMachineFixtures.REGISTRATE
            .machine("native_gt_output_fluid", com.gregtechceu.gtceu.integration.ae2.machine.MEOutputHatchPartMachine::new)
            .rotationState(RotationState.ALL).register();
    public static final MachineEntry<MachineDefinition> DUAL = NativeMachineFixtures.REGISTRATE
            .machine("native_gtmt_output_dual", com.hepdd.gtmthings.common.block.machine.multiblock.part.appeng.MEOutputPartMachine::new)
            .rotationState(RotationState.ALL).register();
    public static final MachineEntry<MultiblockMachineDefinition> CONTROLLER = NativeMachineFixtures.REGISTRATE
            .multiblock("native_gtmt_output_controller", info -> {
                var machine = new NoEnergyMultiblockMachine(info);
                machine.attachTrait(new NotifiableItemStackHandler(1, IO.IN, IO.NONE)); return machine;
            }).rotationState(RotationState.ALL).recipeType(GTRecipeTypes.CHEMICAL_RECIPES)
            .appearanceBlock(() -> Blocks.IRON_BLOCK).allowFlip(false)
            .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("SO")
                    .where('S', controller(definition)).where('O', machines(DUAL.get())).build()).register();
    public static void init() {}
}
