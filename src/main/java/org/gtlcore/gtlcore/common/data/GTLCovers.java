package org.gtlcore.gtlcore.common.data;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.client.renderer.cover.IOCoverRenderer;
import com.gregtechceu.gtceu.client.renderer.cover.SimpleCoverRenderer;
import com.gregtechceu.gtceu.common.cover.ConveyorCover;
import com.gregtechceu.gtceu.common.cover.FluidRegulatorCover;
import com.gregtechceu.gtceu.common.cover.PumpCover;
import com.gregtechceu.gtceu.common.cover.RobotArmCover;
import com.hepdd.gtmthings.GTMThings;
import com.hepdd.gtmthings.common.cover.WirelessEnergyReceiveCover;
import net.minecraft.core.Holder;

import java.util.Locale;

import static com.gregtechceu.gtceu.common.registry.GTRegistration.REGISTRATE;

/** Native deferred covers retain the original GTCovers.register gtceu namespace and paths. */
public final class GTLCovers {
    public static final Holder<CoverDefinition> ELECTRIC_PUMP_MAX = REGISTRATE.cover(
            "pump.max", (def, holder, side) -> new PumpCover(def, holder, side, GTValues.MAX),
            () -> () -> IOCoverRenderer.PUMP_LIKE_COVER_RENDERER);
    public static final Holder<CoverDefinition> FLUID_REGULATORS_ULV = REGISTRATE.cover(
            "fluid_regulators.ulv", (def, holder, side) -> new FluidRegulatorCover(def, holder, side, GTValues.ULV),
            () -> () -> IOCoverRenderer.PUMP_LIKE_COVER_RENDERER);
    public static final Holder<CoverDefinition> FLUID_REGULATOR_MAX = REGISTRATE.cover(
            "fluid_regulator.max", (def, holder, side) -> new FluidRegulatorCover(def, holder, side, GTValues.MAX),
            () -> () -> IOCoverRenderer.PUMP_LIKE_COVER_RENDERER);
    public static final Holder<CoverDefinition> CONVEYOR_MODULE_MAX = REGISTRATE.cover(
            "conveyor.max", (def, holder, side) -> new ConveyorCover(def, holder, side, GTValues.MAX),
            () -> () -> new IOCoverRenderer(GTCEu.id("block/cover/conveyor"), null,
                    GTCEu.id("block/cover/conveyor_emissive"), GTCEu.id("block/cover/conveyor_inverted_emissive")));
    public static final Holder<CoverDefinition> ROBOT_ARM_MAX = REGISTRATE.cover(
            "robot_arm.max", (def, holder, side) -> new RobotArmCover(def, holder, side, GTValues.MAX),
            () -> () -> new IOCoverRenderer(GTCEu.id("block/cover/arm"), null,
                    GTCEu.id("block/cover/arm_emissive"), GTCEu.id("block/cover/arm_inverted_emissive")));
    public static final Holder<CoverDefinition> ROBOT_ARM_ULV = REGISTRATE.cover(
            "robot_arm.ulv", (def, holder, side) -> new RobotArmCover(def, holder, side, GTValues.ULV),
            () -> () -> new IOCoverRenderer(GTCEu.id("block/cover/arm"), null,
                    GTCEu.id("block/cover/arm_emissive"), GTCEu.id("block/cover/arm_inverted_emissive")));
    public static final Holder<CoverDefinition> MAX_WIRELESS_ENERGY_RECEIVE = registerTieredWirelessCover(
            "wireless_energy_receive", 1, GTValues.MAX);
    public static final Holder<CoverDefinition> MAX_WIRELESS_ENERGY_RECEIVE_4A = registerTieredWirelessCover(
            "4a_wireless_energy_receive", 4, GTValues.MAX);

    public static Holder<CoverDefinition> registerTieredWirelessCover(String id, int amperage, int tier) {
        String name = id + "." + GTValues.VN[tier].toLowerCase(Locale.ROOT);
        return REGISTRATE.cover(name,
                (definition, holder, side) -> new WirelessEnergyReceiveCover(definition, holder, side, tier, amperage),
                () -> () -> new SimpleCoverRenderer(GTMThings.id("block/cover/overlay_" +
                        (amperage == 1 ? "" : "4a_") + "wireless_energy_receive")));
    }

    public static void init() {}

    private GTLCovers() {}
}
