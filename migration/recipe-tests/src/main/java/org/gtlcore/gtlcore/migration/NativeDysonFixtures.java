package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.machine.multiblock.generator.DysonSphereMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.ComputationProviderMachine;
import org.gtlcore.gtlcore.common.data.GTLRecipeTypes;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.*;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.multiblock.pattern.MultiblockPatternBuilder;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.machines.GTResearchMachines;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.world.level.block.*;
import net.neoforged.bus.api.IEventBus;
import static com.gregtechceu.gtceu.api.multiblock.Predicates.*;
import static com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection.*;

/** Reduced structures; only the required external receiver casing is supplied by this fixture. */
public final class NativeDysonFixtures {
    private static final GTRegistrate CASINGS = GTRegistrate.create("kubejs", false);
    public static final BlockEntry<Block> RECEIVER = CASINGS.block("dyson_receiver_casing", Block::new)
            .initialProperties(() -> Blocks.IRON_BLOCK).register();
    public static final MachineEntry<MultiblockMachineDefinition> DYSON = NativeMachineFixtures.REGISTRATE
            .multiblock("native_dyson_sphere", info -> {
                var machine = new DysonSphereMachine(info);
                machine.getTraitHolder().attachPersistentTrait("launchEnergy", NotifiableEnergyContainer.receiverContainer(Long.MAX_VALUE / 2, GTValues.V[GTValues.MAX], 2));
                machine.getTraitHolder().attachPersistentTrait("generationA", NotifiableEnergyContainer.emitterContainer(Long.MAX_VALUE / 2, GTValues.V[GTValues.MAX], 128));
                machine.getTraitHolder().attachPersistentTrait("generationB", NotifiableEnergyContainer.emitterContainer(Long.MAX_VALUE / 2, GTValues.V[GTValues.MAX], 128));
                machine.getTraitHolder().attachPersistentTrait("launchItems", new NotifiableItemStackHandler(1, IO.IN, IO.IN));
                return machine;
            }).rotationState(RotationState.ALL).modelProperty(RecipeLogic.STATUS_PROPERTY, RecipeLogic.Status.IDLE)
            .recipeType(GTLRecipeTypes.DYSON_SPHERE_RECIPES)
            .recipeModifier(DysonSphereMachine::recipeModifier).appearanceBlock(() -> Blocks.IRON_BLOCK)
            .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("SC")
                    .where('S', controller(definition)).where('C', machines(GTResearchMachines.COMPUTATION_HATCH_RECEIVER.get())).build())
            .register();
    public static final MachineEntry<MultiblockMachineDefinition> PROVIDER = NativeMachineFixtures.REGISTRATE
            .multiblock("native_dyson_computation", info -> new ComputationProviderMachine(info, true))
            .rotationState(RotationState.ALL).modelProperty(RecipeLogic.STATUS_PROPERTY, RecipeLogic.Status.IDLE)
            .recipeType(GTRecipeTypes.DUMMY_RECIPES).appearanceBlock(() -> Blocks.IRON_BLOCK)
            .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("SET")
                    .where('S', controller(definition)).where('E', machines(GTMachines.ENERGY_INPUT_HATCH[GTValues.MAX].get()))
                    .where('T', machines(GTResearchMachines.COMPUTATION_HATCH_TRANSMITTER.get())).build()).register();
    public static void init(IEventBus bus) { CASINGS.registerEventListeners(bus); }
}
