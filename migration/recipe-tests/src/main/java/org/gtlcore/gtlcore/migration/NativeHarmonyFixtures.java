package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.machine.multiblock.electric.HarmonyMachine;
import org.gtlcore.gtlcore.common.data.GTLRecipeTypes;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.*;
import com.gregtechceu.gtceu.api.multiblock.pattern.MultiblockPatternBuilder;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import com.gregtechceu.gtceu.common.machine.trait.ProgrammableCircuitSlotTrait;
import static com.gregtechceu.gtceu.api.multiblock.Predicates.*;
import static com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection.*;

public final class NativeHarmonyFixtures {
    public static final MachineEntry<MultiblockMachineDefinition> HARMONY = NativeMachineFixtures.REGISTRATE
            .multiblock("native_eye_of_harmony", info -> {
                var machine = new HarmonyMachine(info);
                machine.getTraitHolder().attachPersistentTrait("startupGas", new NotifiableFluidTank(2, Integer.MAX_VALUE, IO.IN, IO.IN));
                machine.getTraitHolder().attachPersistentTrait("harmonyInput", new NotifiableItemStackHandler(1, IO.IN, IO.IN));
                machine.getTraitHolder().attachPersistentTrait("harmonyOutput", new NotifiableItemStackHandler(1, IO.OUT, IO.OUT));
                machine.attachTrait(new ProgrammableCircuitSlotTrait()).setEnabled(true);
                return machine;
            }).rotationState(RotationState.ALL).recipeType(GTLRecipeTypes.COSMOS_SIMULATION_RECIPES)
            .recipeModifier(HarmonyMachine::recipeModifier).appearanceBlock(() -> net.minecraft.world.level.block.Blocks.IRON_BLOCK)
            .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("CSC").where('S', controller(definition))
                    .where('C', blocks(net.minecraft.world.level.block.Blocks.IRON_BLOCK)).build())
            .register();
    public static void init() {}
}
