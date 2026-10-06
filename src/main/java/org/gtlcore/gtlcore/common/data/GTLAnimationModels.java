package org.gtlcore.gtlcore.common.data;

import org.gtlcore.gtlcore.api.GTLConstants;
import org.gtlcore.gtlcore.client.renderer.machine.AnnihilateGeneratorRenderer;
import org.gtlcore.gtlcore.client.renderer.machine.EyeOfHarmonyRenderer;
import org.gtlcore.gtlcore.client.renderer.machine.SpaceElevatorRenderer;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.registry.registrate.builder.MachineBuilder;

import static com.gregtechceu.gtceu.common.data.models.GTMachineModels.createWorkableCasingMachineModel;

/** Shared native model initializers used by production registrations and scoped asset generation. */
public final class GTLAnimationModels {
    private GTLAnimationModels() {}
    public static MachineBuilder.ModelInitializer annihilateGenerator() {
        return createWorkableCasingMachineModel(GTCEu.id("block/casings/hpca/high_power_casing"),
                GTCEu.id("block/multiblock/fusion_reactor"))
                .andThen(model -> model.addDynamicRenderer(AnnihilateGeneratorRenderer::new));
    }
    public static MachineBuilder.ModelInitializer eyeOfHarmony() {
        return createWorkableCasingMachineModel(GTLConstants.id("block/casings/dimensionally_transcendent_casing"),
                GTCEu.id("block/multiblock/cosmos_simulation"))
                .andThen(model -> model.addDynamicRenderer(EyeOfHarmonyRenderer::new));
    }
    public static MachineBuilder.ModelInitializer spaceElevator() {
        return createWorkableCasingMachineModel(GTLConstants.id("block/space_elevator_mechanical_casing"),
                GTCEu.id("block/multiblock/data_bank"))
                .andThen(model -> model.addDynamicRenderer(SpaceElevatorRenderer::new));
    }
}
