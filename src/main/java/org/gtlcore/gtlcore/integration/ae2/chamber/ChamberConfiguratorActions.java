package org.gtlcore.gtlcore.integration.ae2.chamber;

import org.gtlcore.gtlcore.api.machine.trait.MEPart.IModifiableSyncOffset;
import org.gtlcore.gtlcore.common.machine.multiblock.part.TagFilterMEStockBusPartMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.TagFilterMEStockHatchPartMachine;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.trait.ProgrammableCircuitSlotTrait;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/** Bounded remote commands, applied only after the terminal has authorized the machine. */
public final class ChamberConfiguratorActions {
    public static final int MAX_TAG_LENGTH = 256;
    private ChamberConfiguratorActions() {}

    public static void circuit(MetaMachine machine, FriendlyByteBuf payload) {
        int configuration = readInt(payload);
        complete(payload);
        if (configuration < -1 || configuration > IntCircuitBehaviour.CIRCUIT_MAX) {
            throw new IllegalArgumentException("Invalid circuit configuration");
        }
        var stack = configuration < 0 ? ItemStack.EMPTY : IntCircuitBehaviour.stack(configuration);
        if (machine instanceof ItemBusPartMachine bus) {
            bus.getCircuitSlot().storage.setStackInSlot(0, stack);
        } else if (machine instanceof FluidHatchPartMachine hatch) {
            hatch.getTraitOrThrow(ProgrammableCircuitSlotTrait.class).storage.setStackInSlot(0, stack);
        } else {
            throw new IllegalArgumentException("Machine has no programmable circuit slot");
        }
        changed(machine);
    }

    public static void offset(MetaMachine machine, FriendlyByteBuf payload) {
        int ticks = readInt(payload);
        complete(payload);
        if (ticks < 0 || !(machine instanceof IModifiableSyncOffset configurable)) {
            throw new IllegalArgumentException("Invalid machine refresh interval");
        }
        configurable.setMEUpdateInterval(ticks);
        changed(machine);
    }

    public static void tag(MetaMachine machine, FriendlyByteBuf payload) {
        boolean whitelist = payload.readBoolean();
        String text;
        try {
            text = payload.readUtf(MAX_TAG_LENGTH);
        } catch (RuntimeException malformed) {
            throw new IllegalArgumentException("Invalid chamber tag", malformed);
        }
        complete(payload);
        if (machine instanceof TagFilterMEStockBusPartMachine bus) {
            if (whitelist) bus.setTagWhite(text); else bus.setTagBlack(text);
        } else if (machine instanceof TagFilterMEStockHatchPartMachine hatch) {
            if (whitelist) hatch.setTagWhite(text); else hatch.setTagBlack(text);
        } else {
            throw new IllegalArgumentException("Machine has no tag filter");
        }
        changed(machine);
    }

    private static void complete(FriendlyByteBuf payload) {
        if (payload.isReadable()) throw new IllegalArgumentException("Trailing chamber configuration data");
    }

    private static int readInt(FriendlyByteBuf payload) {
        try {
            return payload.readVarInt();
        } catch (RuntimeException malformed) {
            throw new IllegalArgumentException("Invalid chamber configuration integer", malformed);
        }
    }

    private static void changed(MetaMachine machine) {
        machine.markAsChanged();
        machine.notifyBlockUpdate();
    }
}
