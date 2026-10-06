package org.gtlcore.gtlcore.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredPartMachine;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.utils.RedstoneUtil;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

import brachy.modularui.api.drawable.Text;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.BooleanSyncValue;
import brachy.modularui.value.sync.IntSyncValue;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.ToggleButton;
import brachy.modularui.widgets.layout.Flow;
import brachy.modularui.widgets.textfield.TextFieldWidget;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

public class NeutronSensorPartMachine extends TieredPartMachine implements IMuiMachine {
    private static final int MAX_ENERGY_MEV = 1_200_000;

    @SaveField
    @SyncToClient
    private int min, max;

    @Getter
    @SaveField
    @SyncToClient
    private boolean isInverted;

    @Getter
    @SaveField
    @SyncToClient
    protected int redstoneSignalOutput;

    public NeutronSensorPartMachine(BlockEntityCreationInfo info) {
        super(info, GTValues.IV);
    }

    public void setMin(int value) { min = Mth.clamp(value, 0, max); }
    public void setMax(int value) { max = Mth.clamp(value, min, MAX_ENERGY_MEV); }
    public void setInverted(boolean inverted) { isInverted = inverted; }

    @Override
    public void buildMainUI(ParentWidget<?> mainWidget, PosGuiData guiData,
                            PanelSyncManager syncManager, UISettings settings) {
        var minimum = new IntSyncValue(() -> min, this::setMin).allowC2S();
        var maximum = new IntSyncValue(() -> max, this::setMax).allowC2S();
        var inverted = new BooleanSyncValue(this::isInverted, this::setInverted).allowC2S();
        mainWidget.child(Flow.col().coverChildren()
                .child(new ToggleButton().width(150).height(20).value(inverted)
                        .overlay(Text.lang("gui.gtceu.neutron_sensor.invert.enabled")))
                .child(Flow.row().coverChildren()
                        .child(Text.lang("gui.gtlcore.neutron_sensor.min_energy", "MeV").asWidget().width(85))
                        .child(new TextFieldWidget().width(85).height(18)
                                .setNumbers(0, MAX_ENERGY_MEV).value(minimum)))
                .child(Flow.row().coverChildren()
                        .child(Text.lang("gui.gtlcore.neutron_sensor.max_energy", "MeV").asWidget().width(85))
                        .child(new TextFieldWidget().width(85).height(18)
                                .setNumbers(0, MAX_ENERGY_MEV).value(maximum))));
    }

    public void update(int energy) {
        int output = RedstoneUtil.computeRedstoneBetweenValues(energy,
                max * 1_000_000F, min * 1_000_000F, isInverted());
        if (redstoneSignalOutput != output) {
            redstoneSignalOutput = output;
            notifyBlockUpdate();
        }
    }

    @Override
    public int getOutputSignal(@Nullable Direction side) {
        return side == getFrontFacing().getOpposite() ? redstoneSignalOutput : 0;
    }

    @Override
    public boolean canConnectRedstone(@Nullable Direction side) { return false; }
}
