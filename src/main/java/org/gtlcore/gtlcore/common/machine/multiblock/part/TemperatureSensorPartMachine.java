package org.gtlcore.gtlcore.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredPartMachine;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

import brachy.modularui.api.drawable.Text;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.IntSyncValue;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.layout.Flow;
import brachy.modularui.widgets.textfield.TextFieldWidget;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

public class TemperatureSensorPartMachine extends TieredPartMachine implements IMuiMachine {
    private static final int MAX_TEMP = 16000;

    @SaveField
    @SyncToClient
    protected int max;

    @Getter
    @SaveField
    @SyncToClient
    protected int redStoneSignalOutput;

    public TemperatureSensorPartMachine(BlockEntityCreationInfo info) {
        super(info, GTValues.UIV);
    }

    public void setMax(int value) {
        max = Mth.clamp(value, 1, MAX_TEMP);
    }

    @Override
    public void buildMainUI(ParentWidget<?> mainWidget, PosGuiData guiData,
                            PanelSyncManager syncManager, UISettings settings) {
        var temperature = new IntSyncValue(() -> max, this::setMax).allowC2S();
        mainWidget.child(Flow.row().coverChildren()
                .child(Text.lang("gui.gtlcore.temperature_sensor.max_temperature").asWidget().width(85))
                .child(new TextFieldWidget().width(85).height(18)
                        .setNumbers(1, MAX_TEMP).value(temperature)));
    }

    @Override
    public int getOutputSignal(@Nullable Direction side) {
        return side == getFrontFacing().getOpposite() ? redStoneSignalOutput : 0;
    }

    public void update(int heat) {
        int value = heat > max ? 15 : 0;
        if (redStoneSignalOutput != value) {
            redStoneSignalOutput = value;
            notifyBlockUpdate();
        }
    }
}
