package org.gtlcore.gtlcore.common.data.machines;

import org.gtlcore.gtlcore.common.data.GTLRecipeTypes;
import org.gtlcore.gtlcore.common.data.GTLRetainedGTBlocks;
import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.multiblock.pattern.MultiblockPatternBuilder;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import com.gregtechceu.gtceu.api.recipe.OverclockingLogic;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;

import java.util.Comparator;

import static com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.*;
import static com.gregtechceu.gtceu.api.multiblock.Predicates.*;
import static com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection.*;
import static com.gregtechceu.gtceu.common.registry.GTRegistration.REGISTRATE;

/** Native port of the evaporation tower originally bundled with GTLCore, including its vertical output layers. */
public final class GTLRetainedGTMachines {
    private GTLRetainedGTMachines() {}
    public static final MachineEntry<MultiblockMachineDefinition> EVAPORATION_PLANT = REGISTRATE
            .multiblock("evaporation_plant", WorkableElectricMultiblockMachine::new)
            .langValue("Evaporation Tower")
            .rotationState(RotationState.NON_Y_AXIS)
            .recipeType(GTLRecipeTypes.EVAPORATION_RECIPES)
            .recipeModifier(GTRecipeModifiers.ELECTRIC_OVERCLOCK.apply(OverclockingLogic.NON_PERFECT_OVERCLOCK_SUBTICK))
            .appearanceBlock(GTLRetainedGTBlocks.CASING_STAINLESS_EVAPORATION)
            .pattern(definition -> MultiblockPatternBuilder.start(UP, BACK, RIGHT)
                    .slice("FYF", "YYY", "FYF")
                    .slice("YSY", "Y#Y", "YYY")
                    .sliceRepeatable(2, 5, "XXX", "X#X", "XXX")
                    .slice(" Z ", "ZZZ", " Z ")
                    .where('S', controller(blocks(definition.getBlock())))
                    .where('Y', blocks(GTLRetainedGTBlocks.CASING_STAINLESS_EVAPORATION.get())
                            .or(abilities(INPUT_ENERGY).setMinGlobalLimited(1).setMaxGlobalLimited(2))
                            .or(abilities(IMPORT_FLUIDS).setExactLimit(1))
                            .or(abilities(EXPORT_ITEMS).setMaxGlobalLimited(1)))
                    .where('X', blocks(GTLRetainedGTBlocks.CASING_STAINLESS_EVAPORATION.get())
                            .or(abilities(EXPORT_FLUIDS_1X).setMinLayerLimited(1).setMaxLayerLimited(1)))
                    .where('Z', blocks(GTLRetainedGTBlocks.CASING_STAINLESS_EVAPORATION.get()))
                    .where('F', frames(GTMaterials.Aluminium))
                    .where('#', air())
                    .where(' ', any()).build())
            .allowExtendedFacing(false)
            .partSorter(Comparator.comparingInt(part -> part.getBlockPos().getY()))
            .workableCasingModel(GTCEu.id("block/casings/solid/machine_casing_stainless_evaporation"),
                    GTCEu.id("block/multiblock/evaporation_plant"))
            .register();
    public static void init() {}
}
