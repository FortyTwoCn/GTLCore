package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.pattern.GTLPredicates;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.FissionReactorMachine;
import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.multiblock.pattern.MultiblockPatternBuilder;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import net.minecraft.world.level.block.Blocks;
import static com.gregtechceu.gtceu.api.multiblock.Predicates.*;
import static com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection.*;

/** Reduced native structure and sparse coolant identities; original Core registration/materials are not loaded. */
public final class NativeFissionFixtures {
    public static final Material HOT = new Material.Builder(GTCEu.id("hot_sodium_potassium")).fluid().buildAndRegister();
    public static final Material SUPERCRITICAL = new Material.Builder(GTCEu.id("supercritical_sodium_potassium")).fluid().buildAndRegister();
    public static final MachineEntry<MultiblockMachineDefinition> CONTROLLER = NativeMachineFixtures.REGISTRATE
            .multiblock("native_fission_reactor", FissionReactorMachine::new)
            .modelProperty(RecipeLogic.STATUS_PROPERTY, RecipeLogic.Status.IDLE)
            .rotationState(RotationState.NON_Y_AXIS).allowExtendedFacing(false).allowFlip(false)
            .recipeType(GTRecipeTypes.DUMMY_RECIPES)
            .recipeModifier(FissionReactorMachine::recipeModifier)
            .appearanceBlock(() -> Blocks.IRON_BLOCK)
            .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("SIL", "FFO", "CCB", "CCR", "CCX")
                    .where('S', controller(definition)).where('I', machines(GTMachines.FLUID_IMPORT_HATCH_9X[GTValues.UV].get()))
                    .where('O', machines(GTMachines.FLUID_EXPORT_HATCH[GTValues.UV].get()))
                    .where('L', machines(NativeAdvancedFluidRigFixtures.LASER.get()))
                    .where('B', machines(GTMachines.ITEM_IMPORT_BUS[GTValues.LV].get()))
                    .where('R', machines(GTMachines.ITEM_EXPORT_BUS[GTValues.LV].get()))
                    .where('F', GTLPredicates.countBlock("FuelAssembly", Blocks.COPPER_BLOCK))
                    .where('C', GTLPredicates.countBlock("Cooler", Blocks.BLUE_ICE))
                    .where('X', blocks(Blocks.IRON_BLOCK)).build()).register();
    public static void init() {}
}
