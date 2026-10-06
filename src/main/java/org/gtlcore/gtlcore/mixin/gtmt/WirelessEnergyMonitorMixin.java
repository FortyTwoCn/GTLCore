package org.gtlcore.gtlcore.mixin.gtmt;

import org.gtlcore.gtlcore.integration.gtmt.NativeWirelessMonitorRows;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.hepdd.gtmthings.api.misc.WirelessEnergyContainer;
import com.hepdd.gtmthings.common.block.machine.electric.WirelessEnergyMonitor;
import com.hepdd.gtmthings.common.item.IWirelessMonitor;
import net.minecraft.network.chat.Component;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import brachy.modularui.widget.ScrollWidget;
import brachy.modularui.widgets.layout.Flow;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import com.gregtechceu.gtceu.api.machine.mui.MachineUIPanelBuilder;
import com.gregtechceu.gtceu.common.mui.GTMuiWidgets;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** The native UI supplies its synchronized scope and preserves GTMThings' highlight buttons. */
@Mixin(WirelessEnergyMonitor.class)
public abstract class WirelessEnergyMonitorMixin extends MetaMachine implements IWirelessMonitor {
    @Unique private List<Component> gtlcore$rows;
    @Unique private WirelessEnergyContainer gtlcore$account;
    @Unique private UUID gtlcore$owner;
    @Unique private boolean gtlcore$all;
    @Unique private int gtlcore$width;
    @Unique private long gtlcore$sampleTick = Long.MIN_VALUE;

    protected WirelessEnergyMonitorMixin(BlockEntityCreationInfo info) { super(info); }

    public MachineUIPanelBuilder getPanelBuilder(PosGuiData data, PanelSyncManager manager, UISettings settings) {
        return MachineUIPanelBuilder.panelBuilder(this).addTitleBar(false);
    }

    @Inject(method = "buildMainUI", at = @At("TAIL"), remap = false, require = 1)
    private void gtlcore$wideNativeTitle(ParentWidget<?> main, PosGuiData data, PanelSyncManager manager,
                                     UISettings settings, CallbackInfo ci) {
        main.child(GTMuiWidgets.createTitleBar(getDefinition(), 236).name("gtlcore_wireless_title"));
    }

    @ModifyExpressionValue(method = "buildMainUI", at = @At(value = "INVOKE",
            target = "Lcom/hepdd/gtmthings/api/gui/WirelessMonitorUI;create(Lcom/hepdd/gtmthings/common/item/IWirelessMonitor;Lbrachy/modularui/value/sync/BooleanSyncValue;Lbrachy/modularui/value/sync/PanelSyncManager;)Lbrachy/modularui/widgets/layout/Flow;"), remap = false, require = 1)
    private Flow gtlcore$fitStatisticsAndInventory(Flow column) {
        // The native title bar and player inventory also need space at an 854x480 window / GUI scale 2.
        for (var child : column.getChildren()) if (child instanceof ScrollWidget<?> scroll) {
            // Content may extend below the viewport; opposing vertical padding would constrain its height.
            scroll.getArea().getPadding().bottom(0);
            scroll.height(88).onUpdateListener(widget -> {
                // Generic ScrollWidget does not measure the dynamic body as ListWidget does.
                int extent = 0;
                for (var row : widget.getChildren()) if (row.isEnabled())
                    extent = Math.max(extent, row.getArea().y - widget.getArea().y + row.getArea().height);
                widget.getScrollArea().getScrollY().setScrollSize(extent + widget.getArea().getPadding().bottom());
            });
        }
        return column;
    }

    @Override
    public List<Component> getDisplayText(boolean all, int width) {
        if (getLevel() == null || getLevel().isClientSide) return List.of();
        long tick = getLevel().getGameTime();
        var account = getWirelessEnergyContainer();
        var owner = getUUID();
        if (gtlcore$rows == null || account != gtlcore$account || !Objects.equals(owner, gtlcore$owner) ||
                all != gtlcore$all || width != gtlcore$width || tick < gtlcore$sampleTick ||
                tick - gtlcore$sampleTick >= 40) {
            gtlcore$rows = NativeWirelessMonitorRows.build(this, all);
            gtlcore$account = account;
            gtlcore$owner = owner;
            gtlcore$all = all;
            gtlcore$width = width;
            gtlcore$sampleTick = tick;
        }
        return gtlcore$rows;
    }
}
