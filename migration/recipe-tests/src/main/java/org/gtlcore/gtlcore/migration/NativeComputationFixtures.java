package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.machine.multiblock.electric.ComputationProviderMachine;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.multiblock.pattern.MultiblockPatternBuilder;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import static com.gregtechceu.gtceu.api.multiblock.Predicates.*;
import static com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection.*;

/** Reduced native structures; the mainframe fixture stands in for the pack's required KubeJS item. */
public final class NativeComputationFixtures {
    private static final GTRegistrate MAINFRAMES = GTRegistrate.create("kubejs", false);
    public static final ItemEntry<Item> OPTICAL_MAINFRAME = MAINFRAMES.item("optical_mainframe", Item::new).register();
    public static final MachineEntry<MultiblockMachineDefinition> FINITE = provider("native_finite_computation", false);
    public static final MachineEntry<MultiblockMachineDefinition> INFINITE = provider("native_infinite_computation", true);
    private static MachineEntry<MultiblockMachineDefinition> provider(String name, boolean infinite) {
        return NativeMachineFixtures.REGISTRATE.multiblock(name, info -> new ComputationProviderMachine(info, infinite))
                .modelProperty(com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic.STATUS_PROPERTY,
                        com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic.Status.IDLE)
                .rotationState(RotationState.ALL).recipeType(GTRecipeTypes.DUMMY_RECIPES).allowFlip(false)
                .appearanceBlock(() -> Blocks.IRON_BLOCK)
                .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("SEI")
                        .where('S', controller(definition))
                        .where('E', machines(GTMachines.ENERGY_INPUT_HATCH[infinite ? GTValues.MAX : GTValues.UIV].get()))
                        .where('I', machines(GTMachines.ITEM_IMPORT_BUS[GTValues.LV].get())).build()).register();
    }
    public static void init(IEventBus bus) { MAINFRAMES.registerEventListeners(bus); }
}
