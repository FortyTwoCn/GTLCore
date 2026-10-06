package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.GTLConstants;
import org.gtlcore.gtlcore.client.renderer.machine.AnnihilateGeneratorRenderer;
import org.gtlcore.gtlcore.client.renderer.machine.EyeOfHarmonyRenderer;
import org.gtlcore.gtlcore.client.renderer.machine.SpaceElevatorRenderer;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.multiblock.pattern.MultiblockPatternBuilder;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRender;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.function.Supplier;

import static com.gregtechceu.gtceu.api.multiblock.Predicates.controller;
import static com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection.*;
import static com.gregtechceu.gtceu.common.data.models.GTMachineModels.createWorkableCasingMachineModel;

/** Separate native IDs and one-block structures exercise production model bindings and rendering. */
public final class NativeDynamicRenderFixtures {
    public static final List<MachineEntry<MultiblockMachineDefinition>> MACHINES = List.of(
            machine("native_annihilate_render", org.gtlcore.gtlcore.common.data.GTLAnimationModels.annihilateGenerator()),
            machine("native_harmony_render", org.gtlcore.gtlcore.common.data.GTLAnimationModels.eyeOfHarmony()),
            machine("native_elevator_render", org.gtlcore.gtlcore.common.data.GTLAnimationModels.spaceElevator()));

    private static MachineEntry<MultiblockMachineDefinition> machine(String name,
            com.gregtechceu.gtceu.api.registry.registrate.builder.MachineBuilder.ModelInitializer model) {
        return NativeMachineFixtures.REGISTRATE.multiblock(name, RenderMachine::new)
                .rotationState(name.equals("native_annihilate_render") ? RotationState.ALL : RotationState.NON_Y_AXIS)
                .allowExtendedFacing(name.equals("native_annihilate_render"))
                .recipeType(GTRecipeTypes.DUMMY_RECIPES)
                .appearanceBlock(() -> Blocks.IRON_BLOCK)
                .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("S")
                        .where('S', controller(definition)).build())
                .modelProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS, RecipeLogic.Status.IDLE)
                .model(model)
                .hasBER(true).register();
    }

    public static void init() {}

    public static final class RenderMachine extends WorkableElectricMultiblockMachine {
        @SyncToClient private boolean animationActive;
        public RenderMachine(BlockEntityCreationInfo info) { super(info); }
        @Override public boolean isActive() { return animationActive; }
        public void activate() {
            animationActive = true;
            getSyncDataHolder().markClientSyncFieldDirty("animationActive");
        }
        /** Drives renderer admission cases on this explicit fixture; does not claim recipe execution. */
        public void fixtureState(boolean active, boolean formed) {
            animationActive = active;
            isFormed = formed;
        }
    }
}
