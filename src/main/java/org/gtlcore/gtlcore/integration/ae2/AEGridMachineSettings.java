package org.gtlcore.gtlcore.integration.ae2;

import com.gregtechceu.gtceu.integration.ae2.machine.feature.IGridConnectedMachine;
import net.minecraft.core.Direction;

import java.util.EnumSet;

/** Current-version connection settings shared by Core parts and native GT ME machines. */
public final class AEGridMachineSettings {
    private AEGridMachineSettings() {}

    public static void applyExposedSides(IGridConnectedMachine machine, Direction facing) {
        var host = machine.self();
        machine.getMainNode().setExposedOnSides(host.getPersistentData().getBoolean("isAllFacing") || !host.hasFrontFacing() ?
                EnumSet.allOf(Direction.class) : EnumSet.of(facing));
    }
}
