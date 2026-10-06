package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.integration.wildcard.*;
import org.gtlcore.gtlcore.api.machine.multiblock.NoEnergyMultiblockMachine;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.*;
import com.gregtechceu.gtceu.api.multiblock.pattern.MultiblockPatternBuilder;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import static com.gregtechceu.gtceu.api.multiblock.Predicates.*;
import static com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection.*;

/** Supplies only the external item/protocol; its editor and Forge mod are not part of this native validation. */
public final class NativeWildcardFixtures {
    private static final GTRegistrate ITEMS = GTRegistrate.create("wildcard_pattern", false);
    public static final ItemEntry<Item> PATTERN = ITEMS.item("wildcard_pattern", Item::new)
            .properties(p -> p.stacksTo(1)).register();
    public static final MachineEntry<MachineDefinition> BUFFER = NativeMachineFixtures.REGISTRATE
            .machine("native_wildcard_pattern_buffer", info -> new MEWildcardPatternBufferPartMachine(info, IO.BOTH))
            .langValue("ME Wildcard Pattern Buffer")
            .rotationState(RotationState.ALL).register();
    public static final MachineEntry<MachineDefinition> STOCKING = NativeMachineFixtures.REGISTRATE
            .machine("native_stocking_wildcard_pattern_buffer", info -> new MEStockingWildcardPatternBufferPartMachine(info, IO.BOTH))
            .langValue("ME Stocking Wildcard Pattern Buffer")
            .rotationState(RotationState.ALL).register();
    public static final MachineEntry<MultiblockMachineDefinition> CONTROLLER = controller("native_wildcard_controller", BUFFER);
    public static final MachineEntry<MultiblockMachineDefinition> STOCKING_CONTROLLER = controller("native_stocking_wildcard_controller", STOCKING);
    private static MachineEntry<MultiblockMachineDefinition> controller(String name, MachineEntry<MachineDefinition> part) {
        return NativeMachineFixtures.REGISTRATE.multiblock(name, NoEnergyMultiblockMachine::new)
                .rotationState(RotationState.ALL).recipeType(GTRecipeTypes.CHEMICAL_RECIPES)
                .appearanceBlock(() -> Blocks.IRON_BLOCK).allowFlip(false)
                .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("SO")
                        .where('S', com.gregtechceu.gtceu.api.multiblock.Predicates.controller(definition)).where('O', machines(part.get())).build()).register();
    }
    public static void init(IEventBus bus) { ITEMS.registerEventListeners(bus); }
}
