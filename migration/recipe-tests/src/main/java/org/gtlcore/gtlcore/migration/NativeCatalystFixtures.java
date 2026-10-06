package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.machine.multiblock.NoEnergyMultiblockMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.BallHatchPartMachine;
import com.gregtechceu.gtceu.api.machine.*;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import com.gregtechceu.gtceu.api.multiblock.pattern.MultiblockPatternBuilder;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import static com.gregtechceu.gtceu.api.multiblock.Predicates.*;
import static com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection.*;

/** A original-namespace grinding ball and reduced controller using the production part's native callbacks. */
public final class NativeCatalystFixtures {
    private static final GTRegistrate KUBEJS = GTRegistrate.create("kubejs", false);
    public static final com.tterrag.registrate.util.entry.ItemEntry<Item> BALL = KUBEJS.item("grindball_native_validation", Item::new).register();
    public static final MachineEntry<MachineDefinition> PART = NativeMachineFixtures.REGISTRATE
            .machine("native_grindball_hatch", BallHatchPartMachine::new).rotationState(RotationState.ALL).register();
    public static final MachineEntry<MultiblockMachineDefinition> CONTROLLER = NativeMachineFixtures.REGISTRATE
            .multiblock("native_grindball_controller", info -> {
                var machine = new NoEnergyMultiblockMachine(info);
                machine.attachTrait(new NotifiableItemStackHandler(1, IO.IN, IO.NONE));
                machine.attachTrait(new NotifiableItemStackHandler(1, IO.OUT, IO.NONE)); return machine;
            }).rotationState(RotationState.ALL).recipeType(GTRecipeTypes.CHEMICAL_RECIPES)
            .appearanceBlock(() -> Blocks.IRON_BLOCK).allowFlip(false)
            .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("SB")
                    .where('S', controller(definition)).where('B', machines(PART.get())).build()).register();
    public static void init(IEventBus bus) { KUBEJS.registerEventListeners(bus); }
}
