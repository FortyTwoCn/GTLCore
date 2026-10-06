package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.machine.multiblock.electric.AdvancedInfiniteDrillMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.TemperatureSensorPartMachine;
import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.block.ICoilType;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.ToolProperty;
import com.gregtechceu.gtceu.api.item.tool.GTToolType;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.multiblock.pattern.MultiblockPatternBuilder;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import com.gregtechceu.gtceu.common.block.CoilBlock;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.machine.multiblock.part.LaserHatchPartMachine;
import com.gregtechceu.gtceu.data.recipe.CustomTags;
import com.gregtechceu.gtceu.common.data.models.GTModels;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.tags.BlockTags;
import static com.gregtechceu.gtceu.api.multiblock.Predicates.*;
import static com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection.*;

/** Separate rig/coils/sensor IDs. Sparse material fixtures supply only the two driller lookup identities;
 * complete Core materials, custom KubeJS coils and the original large structure are not loaded. */
public final class NativeAdvancedFluidRigFixtures {
    private static final Material COIL_MATERIAL = new Material.Builder(GTCEu.id("titansteel")).ingot().buildAndRegister();
    public static final Material VIBRANIUM = new Material.Builder(GTCEu.id("vibranium")).ingot()
            .flags(com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialFlags.GENERATE_PLATE)
            .toolStats(ToolProperty.Builder.of(1, 1, 100, 10, GTToolType.DRILL_LV).build()).buildAndRegister();
    private static final ICoilType TYPE = new ICoilType() {
        public String getName() { return "native_drill_titansteel"; }
        public int getCoilTemperature() { return 12600; }
        public int getLevel() { return 1; }
        public int getEnergyDiscount() { return 1; }
        public int getTier() { return GTValues.UEV; }
        public Material getMaterial() { return COIL_MATERIAL; }
        public ResourceLocation getTexture() { return CoilBlock.CoilType.KANTHAL.getTexture(); }
    };
    public static final BlockEntry<CoilBlock> COIL = NativeMachineFixtures.REGISTRATE.block("native_drill_titansteel_coil", p -> new CoilBlock(p, TYPE))
            .initialProperties(() -> Blocks.IRON_BLOCK).blockstate(GTModels.createCoilModel(TYPE)).item(BlockItem::new).build().register();
    static { GTCEuAPI.HEATING_COILS.put(TYPE, COIL); }
    public static final BlockEntry<net.minecraft.world.level.block.Block> TIER_TEN_BLOCK = NativeMachineFixtures.REGISTRATE.block("native_tier_ten_block", net.minecraft.world.level.block.Block::new)
            .initialProperties(() -> Blocks.IRON_BLOCK).properties(p -> p.requiresCorrectToolForDrops())
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, CustomTags.TOOL_TIERS[10]).item(BlockItem::new).build().register();
    public static final MachineEntry<MachineDefinition> LASER = NativeMachineFixtures.REGISTRATE.machine("native_drill_max_laser",
            info -> new LaserHatchPartMachine(info, IO.IN, GTValues.MAX, 4096)).rotationState(RotationState.ALL).abilities(PartAbility.INPUT_LASER).register();
    public static final MachineEntry<MachineDefinition> SENSOR = NativeMachineFixtures.REGISTRATE.machine("native_drill_heat_sensor", TemperatureSensorPartMachine::new)
            .rotationState(RotationState.ALL).register();
    public static final MachineEntry<MultiblockMachineDefinition> DRILL = NativeMachineFixtures.REGISTRATE.multiblock("native_advanced_fluid_rig", AdvancedInfiniteDrillMachine::new)
            .modelProperty(RecipeLogic.STATUS_PROPERTY, RecipeLogic.Status.IDLE)
            .rotationState(RotationState.NONE).recipeType(GTRecipeTypes.DUMMY_RECIPES).allowExtendedFacing(false).allowFlip(false)
            .appearanceBlock(() -> Blocks.IRON_BLOCK)
            .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("SEI", "OBC", "Q C")
                    .where('S', controller(definition)).where('E', machines(LASER.get()))
                    .where('I', machines(GTMachines.FLUID_IMPORT_HATCH[GTValues.UV].get()))
                    .where('B', machines(GTMachines.FLUID_IMPORT_HATCH[GTValues.UV].get()))
                    .where('O', machines(NativeGtOutputFixtures.FLUID.get())).where('C', heatingCoils())
                    .where('Q', machines(SENSOR.get())).where(' ', air()).build()).register();
    public static void init() {}
}
