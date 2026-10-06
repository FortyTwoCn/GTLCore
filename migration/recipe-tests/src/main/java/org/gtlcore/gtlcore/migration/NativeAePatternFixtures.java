package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferProxyPartMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEStockingPatternBufferPartMachine;
import org.gtlcore.gtlcore.common.item.VirtualIngredientBehavior;
import org.gtlcore.gtlcore.api.machine.multiblock.NoEnergyMultiblockMachine;
import com.gregtechceu.gtceu.api.item.ComponentItem;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.multiblock.pattern.MultiblockPatternBuilder;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.level.block.Blocks;
import static com.gregtechceu.gtceu.api.multiblock.Predicates.*;
import static com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection.*;

/** Registers actual production parts with independent validation machine IDs. */
public final class NativeAePatternFixtures {
    private static final GTRegistrate VIRTUAL_REGISTRATE = GTRegistrate.create("gtlcore", false);
    public static final com.tterrag.registrate.util.entry.ItemEntry<org.gtlcore.gtlcore.common.item.VirtualIngredientItem> VIRTUAL = VIRTUAL_REGISTRATE
            .item("virtual_ingredient", org.gtlcore.gtlcore.common.item.VirtualIngredientItem::new)
            .onRegister(item -> item.attachComponents(VirtualIngredientBehavior.INSTANCE)).register();
    public static final com.tterrag.registrate.util.entry.ItemEntry<ComponentItem> PATTERN_BOX = VIRTUAL_REGISTRATE
            .item("pattern_box", ComponentItem::new).properties(properties -> properties.stacksTo(1))
            .onRegister(item -> item.attachComponents(org.gtlcore.gtlcore.common.item.PatternBoxBehavior.INSTANCE)).register();
    public static final com.tterrag.registrate.util.entry.ItemEntry<ComponentItem> PATTERN_MODIFIER = VIRTUAL_REGISTRATE
            .item("pattern_modifier", ComponentItem::new).properties(properties -> properties.stacksTo(1))
            .onRegister(item -> item.attachComponents(org.gtlcore.gtlcore.common.item.PatternModifier.INSTANCE)).register();
    public static final com.tterrag.registrate.util.entry.ItemEntry<ComponentItem> ULTIMATE_TERMINAL = VIRTUAL_REGISTRATE
            .item("ultimate_terminal", ComponentItem::new).properties(properties -> properties.stacksTo(1))
            .onRegister(item -> item.attachComponents(new org.gtlcore.gtlcore.common.item.UltimateTerminalBehavior())).register();
    public static final com.tterrag.registrate.util.entry.ItemEntry<ComponentItem> PATTERN_TEST = VIRTUAL_REGISTRATE
            .item("debug_pattern_test", ComponentItem::new).properties(properties -> properties.stacksTo(1))
            .onRegister(item -> item.attachComponents(org.gtlcore.gtlcore.common.item.PatternTestBehavior.INSTANCE)).register();
    public static final com.tterrag.registrate.util.entry.ItemEntry<ComponentItem> STRUCTURE_WRITER = VIRTUAL_REGISTRATE
            .item("debug_structure_writer", ComponentItem::new).properties(properties -> properties.stacksTo(1))
            .onRegister(item -> item.attachComponents(org.gtlcore.gtlcore.common.item.StructureWriteBehavior.INSTANCE)).register();
    public static final com.tterrag.registrate.util.entry.ItemEntry<ComponentItem> STRUCTURE_DETECT = VIRTUAL_REGISTRATE
            .item("structure_detect", ComponentItem::new).properties(properties -> properties.stacksTo(1))
            .onRegister(item -> item.attachComponents(org.gtlcore.gtlcore.common.item.StructureDetectBehavior.INSTANCE)).register();
    public static final MachineEntry<MachineDefinition> BUFFER = NativeMachineFixtures.REGISTRATE
            .machine("native_ae_pattern_buffer", info -> new MEPatternBufferPartMachine(info, 27, IO.BOTH))
            .rotationState(RotationState.ALL).register();
    public static final MachineEntry<MachineDefinition> LARGE_BUFFER = NativeMachineFixtures.REGISTRATE
            .machine("native_ae_large_pattern_buffer", info -> new MEPatternBufferPartMachine(info, 108, IO.BOTH))
            .rotationState(RotationState.ALL).register();
    public static final MachineEntry<MachineDefinition> PROXY = NativeMachineFixtures.REGISTRATE
            .machine("native_ae_pattern_proxy", MEPatternBufferProxyPartMachine::new)
            .rotationState(RotationState.ALL).register();
    public static final MachineEntry<MachineDefinition> STOCKING = NativeMachineFixtures.REGISTRATE
            .machine("native_ae_stocking_pattern_buffer", info -> new MEStockingPatternBufferPartMachine(info, 27, IO.BOTH))
            .rotationState(RotationState.ALL).register();
    public static final MachineEntry<MachineDefinition> VIRTUAL_SUPPLY = NativeMachineFixtures.REGISTRATE
            .machine("native_virtual_ingredient_supply", org.gtlcore.gtlcore.common.machine.VirtualIngredientSupplyMachine::new)
            .rotationState(RotationState.ALL).register();
    public static final MachineEntry<MultiblockMachineDefinition> CONTROLLER = NativeMachineFixtures.REGISTRATE
            .multiblock("native_ae_pattern_controller", NoEnergyMultiblockMachine::new)
            .rotationState(RotationState.ALL).recipeType(com.gregtechceu.gtceu.common.data.GTRecipeTypes.CHEMICAL_RECIPES)
            .appearanceBlock(() -> Blocks.IRON_BLOCK).allowFlip(false)
            .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("SO")
                    .where('S', controller(definition)).where('O', machines(BUFFER.get(), PROXY.get())).build()).register();
    public static final MachineEntry<MultiblockMachineDefinition> STOCKING_CONTROLLER = NativeMachineFixtures.REGISTRATE
            .multiblock("native_ae_stocking_pattern_controller", NoEnergyMultiblockMachine::new)
            .rotationState(RotationState.ALL).recipeType(com.gregtechceu.gtceu.common.data.GTRecipeTypes.CHEMICAL_RECIPES)
            .appearanceBlock(() -> Blocks.IRON_BLOCK).allowFlip(false)
            .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("SO")
                    .where('S', controller(definition)).where('O', machines(STOCKING.get())).build()).register();

    public static void init(IEventBus bus) { VIRTUAL_REGISTRATE.registerEventListeners(bus); }
}
