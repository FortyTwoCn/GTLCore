package org.gtlcore.gtlcore.mixin.gtm.machine;

import org.gtlcore.gtlcore.api.capability.IInt128EnergyContainer;
import org.gtlcore.gtlcore.common.data.GTLMultiblockDisplays;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.ActiveTransformerMachine;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.common.mui.GTMultiblockTextUtil;

import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.value.sync.PanelSyncManager;

import java.util.List;
import java.util.ArrayList;
import java.math.BigInteger;

@Mixin(ActiveTransformerMachine.class)
public abstract class ActiveTransformerMachineMixin extends WorkableElectricMultiblockMachine {

    @Shadow(remap = false)
    private IEnergyContainer powerOutput;
    @Shadow(remap = false)
    private IEnergyContainer powerInput;

    public ActiveTransformerMachineMixin(BlockEntityCreationInfo holder) {
        super(holder);
    }

    /**
     * @author Dragons
     * @reason Fix overflow
     */
    @Overwrite(remap = false)
    protected boolean isSubscriptionActive() {
        if (!isFormed()) return false;

        if (powerInput == null || powerInput.getEnergyStored() <= 0) return false;
        if (powerOutput == null) return false;
        return ((IInt128EnergyContainer) powerOutput).getInt128EnergyStored().compareTo(((IInt128EnergyContainer) powerOutput).getInt128EnergyCapacity()) < 0;
    }

    /** @author GTLCore @reason Synchronize exact energy displays through the native ModularUI menu. */
    @Overwrite(remap = false)
    public List<IWidget> getWidgetsForDisplay(PanelSyncManager sync) {
        var widgets = new ArrayList<IWidget>();
        widgets.add(GTMultiblockTextUtil.addUnformedWarning(this, sync));
        widgets.addAll(GTLMultiblockDisplays.syncedLines("activeTransformer", (machine, lines) ->
                gtlcore$addDisplayText(lines)).apply(this, sync));
        return widgets;
    }

    @Unique
    private void gtlcore$addDisplayText(@NotNull List<Component> textList) {
        if (isFormed()) {
            if (!isWorkingEnabled()) {
                textList.add(Component.translatable("gtceu.multiblock.work_paused"));
            } else if (isActive()) {
                textList.add(Component.translatable("gtceu.multiblock.running"));
                textList.add(Component
                        .translatable("gtceu.multiblock.active_transformer.max_input",
                                FormattingUtil.formatNumbers(
                                        BigInteger.valueOf(powerInput.getInputVoltage()).multiply(BigInteger.valueOf(powerInput.getInputAmperage())).abs())));
                textList.add(Component
                        .translatable("gtceu.multiblock.active_transformer.max_output",
                                FormattingUtil.formatNumbers(
                                        BigInteger.valueOf(powerOutput.getOutputVoltage()).multiply(BigInteger.valueOf(powerOutput.getOutputAmperage())).abs())));
                textList.add(Component
                        .translatable("gtceu.multiblock.active_transformer.average_in", ((IInt128EnergyContainer) powerInput).getInt128InputPerSec().divide(20).toFormattedString()));
                textList.add(Component
                        .translatable("gtceu.multiblock.active_transformer.average_out", ((IInt128EnergyContainer) powerOutput).getInt128OutputPerSec().divide(20).toFormattedString()));
                if (!ConfigHolder.INSTANCE.machines.harmlessActiveTransformers) {
                    textList.add(Component
                            .translatable("gtceu.multiblock.active_transformer.danger_enabled"));
                }
            } else {
                textList.add(Component.translatable("gtceu.multiblock.idling"));
            }
        }
    }
}
