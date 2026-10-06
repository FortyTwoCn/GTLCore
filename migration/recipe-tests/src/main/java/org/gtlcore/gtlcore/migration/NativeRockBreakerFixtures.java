package org.gtlcore.gtlcore.migration;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.multiblock.pattern.MultiblockPatternBuilder;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import net.minecraft.world.level.block.Blocks;
import static com.gregtechceu.gtceu.api.multiblock.Predicates.*;
import static com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection.*;

/** Native controller and real nine-tank input/output hatches, separate from production machine registrations. */
public final class NativeRockBreakerFixtures {
    public static final MachineEntry<MultiblockMachineDefinition> CONTROLLER = NativeMachineFixtures.REGISTRATE
            .multiblock("native_rock_fluid_controller", WorkableElectricMultiblockMachine::new)
            .modelProperty(com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic.STATUS_PROPERTY,
                    com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic.Status.IDLE)
            .rotationState(RotationState.NONE).recipeType(GTRecipeTypes.DUMMY_RECIPES).allowFlip(false).allowExtendedFacing(false)
            .appearanceBlock(() -> Blocks.IRON_BLOCK)
            .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("SIO")
                    .where('S', controller(definition)).where('I', machines(GTMachines.FLUID_IMPORT_HATCH_9X[GTValues.UV].get()))
                    .where('O', machines(GTMachines.FLUID_EXPORT_HATCH_9X[GTValues.UV].get())).build()).register();
    public static void init() {}
}
