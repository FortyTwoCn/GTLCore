package org.gtlcore.gtlcore.common.machine.multiblock.electric;

import org.gtlcore.gtlcore.api.machine.multiblock.IModularMachineModule;
import org.gtlcore.gtlcore.common.data.GTLRecipeModifiers;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import org.gtlcore.gtlcore.common.data.GTLMultiblockDisplays;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.value.sync.PanelSyncManager;

import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ArrayList;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class SuprachronalAssemblyLineModuleMachine extends WorkableElectricMultiblockMachine
                                                   implements IModularMachineModule<SuprachronalAssemblyLineMachine, SuprachronalAssemblyLineModuleMachine> {

    @SaveField
    @Nullable
    private BlockPos hostPosition;

    @Nullable
    private SuprachronalAssemblyLineMachine host;

    public SuprachronalAssemblyLineModuleMachine(BlockEntityCreationInfo holder) {
        super(holder);
    }

    @Override
    public BlockPos getHostPosition() { return hostPosition; }
    @Override
    public void setHostPosition(@Nullable BlockPos pos) { hostPosition = pos == null ? null : pos.immutable(); setChanged(); }
    @Override
    public SuprachronalAssemblyLineMachine getHost() { return host; }
    @Override
    public void setHost(@Nullable SuprachronalAssemblyLineMachine host) { this.host = host; }

    @Override
    public @NotNull Class<SuprachronalAssemblyLineMachine> getHostType() {
        return SuprachronalAssemblyLineMachine.class;
    }

    @Override
    public BlockPos[] getHostScanPositions() {
        final BlockPos pos = getBlockPos();
        return new BlockPos[] {
                pos.offset(3, 0, 0),
                pos.offset(-3, 0, 0),
                pos.offset(0, 0, 3),
                pos.offset(0, 0, -3)
        };
    }

    @Override
    public void onConnected(@NotNull SuprachronalAssemblyLineMachine host) {
        getRecipeLogic().updateTickSubscription();
    }

    public int getParallel() {
        if (host != null) {
            return GTLRecipeModifiers.getHatchParallel(host);
        }
        return 0;
    }

    @Override
    public boolean onWorking() {
        boolean value = super.onWorking();
        if (getOffsetTimer() % 20 == 0) {
            if (!isConnectedToHost()) {
                getRecipeLogic().setProgress(0);
            }
        }
        return value;
    }

    @Override
    public boolean beforeWorking(@Nullable GTRecipe recipe) {
        if (!isConnectedToHost()) {
            getRecipeLogic().interruptRecipe();
            return false;
        }
        return super.beforeWorking(recipe);
    }

    @Override
    public void formStructure(String name) {
        super.formStructure(name);
        if (!findAndConnectToHost()) {
            removeFromHost(this.host);
        }
    }

    @Override
    public void invalidateStructure(String name) {
        super.invalidateStructure(name);
        removeFromHost(this.host);
    }

    @Override
    public void onPartUnload() {
        super.onPartUnload();
        removeFromHost(this.host);
    }

    @Override
    public void onUnload() {
        removeFromHost(this.host);
        super.onUnload();
    }

    @Override
    public List<IWidget> getWidgetsForDisplay(PanelSyncManager sync) {
        var widgets = new ArrayList<>(super.getWidgetsForDisplay(sync));
        widgets.addAll(GTLMultiblockDisplays.syncedLines("suprachronalModule", (controller, lines) -> addDisplayText(lines)).apply(this, sync));
        return widgets;
    }

    private void addDisplayText(@NotNull List<Component> textList) {
        if (!this.isFormed) return;
        textList.add(Component.translatable("gtceu.multiblock.parallel",
                Component.literal(FormattingUtil.formatNumbers(getParallel()))
                        .withStyle(ChatFormatting.DARK_PURPLE))
                .withStyle(ChatFormatting.GRAY));
        textList.add(Component.translatable(
                isConnectedToHost() ? "tooltip.gtlcore.module_installed" : "tooltip.gtlcore.module_not_installed"));
    }

}
