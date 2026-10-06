package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.data.GTLRecipeTypes;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.BedrockDrillingRig;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.multiblock.pattern.MultiblockPatternBuilder;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import com.gregtechceu.gtceu.common.data.GTMachines;
import net.minecraft.world.level.block.Blocks;
import static com.gregtechceu.gtceu.api.multiblock.Predicates.*;
import static com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection.*;

/** Reduced structure around the actual drilling controller and physical GT hatches. */
public final class NativeBedrockDrillingFixtures {
    public static final MachineEntry<MultiblockMachineDefinition> DRILLER = NativeMachineFixtures.REGISTRATE
            .multiblock("native_bedrock_drilling", BedrockDrillingRig::new)
            .modelProperty(RecipeLogic.STATUS_PROPERTY, RecipeLogic.Status.IDLE)
            .rotationState(RotationState.NONE).recipeType(GTLRecipeTypes.BEDROCK_DRILLING_RIG_RECIPES)
            .allowExtendedFacing(false).allowFlip(false).appearanceBlock(() -> Blocks.IRON_BLOCK)
            .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("SEIO")
                    .where('S', controller(definition))
                    .where('E', machines(GTMachines.ENERGY_INPUT_HATCH[GTValues.LV].get()))
                    .where('I', machines(GTMachines.ITEM_IMPORT_BUS[GTValues.LV].get()))
                    .where('O', machines(GTMachines.ITEM_EXPORT_BUS[GTValues.LV].get())).build()).register();
    public static void init() {}
}
