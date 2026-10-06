package org.gtlcore.gtlcore.migration;

import com.gtladd.gtladditions.common.machine.multiblock.part.MESuperPatternBufferPartMachine;
import com.gtladd.gtladditions.common.machine.multiblock.part.MESuperPatternBufferProxyPartMachine;
import org.gtlcore.gtlcore.api.machine.multiblock.NoEnergyMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.*;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import com.gregtechceu.gtceu.api.multiblock.pattern.MultiblockPatternBuilder;
import com.gregtechceu.gtceu.api.item.ComponentItem;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import static com.gregtechceu.gtceu.api.multiblock.Predicates.*;
import static com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection.*;

/** Actual Additions classes and original external cycle-container IDs; registration entrypoints remain independent. */
public final class NativeSuperPatternFixtures {
    private static final GTRegistrate CYCLES = GTRegistrate.create("kubejs", false);
    public static final ItemEntry<ComponentItem> TIME_CELL = CYCLES.item("time_dilation_containment_unit", ComponentItem::new).register();
    public static final ItemEntry<ComponentItem> DURABLE_CELL = CYCLES.item("extremely_durable_plasma_cell", ComponentItem::new).register();
    public static final ItemEntry<ComponentItem> PLASMA_CELL = CYCLES.item("plasma_containment_cell", ComponentItem::new).register();
    public static final MachineEntry<MachineDefinition> BUFFER = NativeMachineFixtures.REGISTRATE
            .machine("native_super_pattern_buffer", MESuperPatternBufferPartMachine::new).rotationState(RotationState.ALL).register();
    public static final MachineEntry<MachineDefinition> WIDE_BUFFER = NativeMachineFixtures.REGISTRATE
            .machine("native_wide_super_pattern_buffer", info -> new MESuperPatternBufferPartMachine(info, 18, 10, 10)).rotationState(RotationState.ALL).register();
    public static final MachineEntry<MachineDefinition> TINY_BUFFER = NativeMachineFixtures.REGISTRATE
            .machine("native_tiny_super_pattern_buffer", info -> new MESuperPatternBufferPartMachine(info, 1, 1, 1)).rotationState(RotationState.ALL).register();
    public static final MachineEntry<MachineDefinition> PROXY = NativeMachineFixtures.REGISTRATE
            .machine("native_super_pattern_proxy", MESuperPatternBufferProxyPartMachine::new).rotationState(RotationState.ALL).register();
    public static final MachineEntry<MultiblockMachineDefinition> CONTROLLER = NativeMachineFixtures.REGISTRATE
            .multiblock("native_super_pattern_controller", NoEnergyMultiblockMachine::new).rotationState(RotationState.ALL)
            .recipeType(GTRecipeTypes.CHEMICAL_RECIPES).appearanceBlock(() -> Blocks.IRON_BLOCK).allowFlip(false)
            .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("SB")
                    .where('S', controller(definition)).where('B', machines(BUFFER.get())).build()).register();
    public static final MachineEntry<MultiblockMachineDefinition> PROXY_CONTROLLER = NativeMachineFixtures.REGISTRATE
            .multiblock("native_super_proxy_controller", NoEnergyMultiblockMachine::new).rotationState(RotationState.ALL)
            .recipeType(GTRecipeTypes.CHEMICAL_RECIPES).appearanceBlock(() -> Blocks.IRON_BLOCK).allowFlip(false)
            .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("SP")
                    .where('S', controller(definition)).where('P', machines(PROXY.get())).build()).register();
    public static void init(IEventBus bus) { CYCLES.registerEventListeners(bus); }
}
