package org.gtlcore.gtlcore.integration.ae2.chamber;

import org.gtlcore.gtlcore.api.machine.trait.MEPart.IModifiableSyncOffset;
import org.gtlcore.gtlcore.common.machine.multiblock.part.TagFilterMEStockBusPartMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.TagFilterMEStockHatchPartMachine;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;

import net.minecraft.network.FriendlyByteBuf;

/** Remote commands; target and grid permissions are checked by the terminal menu. */
public final class MEChamberConfigurator {
    public static final int MAX_ACTION_BYTES = 4096;
    public static final int MAX_TAG_LENGTH = ChamberConfiguratorActions.MAX_TAG_LENGTH;
    public static final int ROOT_ACTION_ID = 1;

    private MEChamberConfigurator() {}

    public static boolean supports(MetaMachine machine, Kind kind) {
        return switch (kind) {
            case CIRCUIT -> machine instanceof ItemBusPartMachine || machine instanceof FluidHatchPartMachine;
            case SYNC_OFFSET -> machine instanceof IModifiableSyncOffset;
            case TAG_FILTER -> machine instanceof TagFilterMEStockBusPartMachine ||
                    machine instanceof TagFilterMEStockHatchPartMachine;
        };
    }

    public static void handleClientAction(MetaMachine machine, Kind kind, int actionId, FriendlyByteBuf payload) {
        if (actionId != ROOT_ACTION_ID || payload.readableBytes() > MAX_ACTION_BYTES || !supports(machine, kind)) {
            throw new IllegalArgumentException("Invalid chamber configuration command");
        }
        switch (kind) {
            case CIRCUIT -> ChamberConfiguratorActions.circuit(machine, payload);
            case SYNC_OFFSET -> ChamberConfiguratorActions.offset(machine, payload);
            case TAG_FILTER -> ChamberConfiguratorActions.tag(machine, payload);
        }
    }

    // Ordinals are part of the existing terminal packet protocol.
    public enum Kind {
        CIRCUIT,
        SYNC_OFFSET,
        TAG_FILTER
    }
}
