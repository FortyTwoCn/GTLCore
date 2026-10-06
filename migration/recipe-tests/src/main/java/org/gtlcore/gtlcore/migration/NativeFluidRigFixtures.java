package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.machine.multiblock.electric.INFFluidDrillMachine;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.multiblock.pattern.MultiblockPatternBuilder;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.FluidDrillMachine;
import net.minecraft.world.level.block.Blocks;
import static com.gregtechceu.gtceu.api.multiblock.Predicates.*;
import static com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection.*;

/** Original controllers and real GT hatches; reduced structures and separate fixture IDs. */
public final class NativeFluidRigFixtures {
    public static final MachineEntry<MultiblockMachineDefinition> INFINITE = register("native_infinite_fluid_rig", true, false, GTValues.MV);
    public static final MachineEntry<MultiblockMachineDefinition> OVERCLOCKED = register("native_infinite_fluid_overclock", true, false, GTValues.EV);
    public static final MachineEntry<MultiblockMachineDefinition> LONG_INFINITE = register("native_infinite_fluid_long", true, true, GTValues.MV);
    public static final MachineEntry<MultiblockMachineDefinition> GT_RIG = register("native_gt_fluid_rig", false, false, GTValues.MV);
    public static final MachineEntry<MultiblockMachineDefinition> LONG_GT_RIG = register("native_gt_fluid_long", false, true, GTValues.EV);

    private static MachineEntry<MultiblockMachineDefinition> register(String name, boolean infinite, boolean longOutput, int energyTier) {
        return NativeMachineFixtures.REGISTRATE.<WorkableElectricMultiblockMachine>multiblock(name, info -> infinite ?
                new INFFluidDrillMachine(info, GTValues.MV) : new FluidDrillMachine(info, energyTier))
                .modelProperty(RecipeLogic.STATUS_PROPERTY, RecipeLogic.Status.IDLE)
                .rotationState(RotationState.NONE).recipeType(GTRecipeTypes.DUMMY_RECIPES)
                .allowExtendedFacing(false).allowFlip(false).appearanceBlock(() -> Blocks.IRON_BLOCK)
                .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("SEO")
                        .where('S', controller(definition))
                        .where('E', machines(GTMachines.ENERGY_INPUT_HATCH[energyTier].get()))
                        .where('O', machines(longOutput ? NativeGtOutputFixtures.FLUID.get() : GTMachines.FLUID_EXPORT_HATCH[GTValues.HV].get())).build())
                .register();
    }

    public static void init() {}
}
