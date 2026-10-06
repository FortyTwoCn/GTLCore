package org.gtlcore.gtlcore.api.machine.trait.MEPart;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.mui.MachineUIPanelBuilder;
import com.gregtechceu.gtceu.config.ConfigHolder;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.BooleanSyncValue;
import brachy.modularui.widgets.ButtonWidget;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** A native output cycle advances only while automatic output is enabled; zero requests the next return. */
public interface IMEOutputPart {
    default int returnInterval() {
        int configured = this instanceof IModifiableSyncOffset part ? part.getMEUpdateInterval() : 0;
        return Math.max(1, configured == 0 ? ConfigHolder.INSTANCE.compat.ae2.updateIntervals : configured);
    }
    default boolean isReturn() { return getTime() == 0; }
    int getTime();
    void setTime(int time);
    default void advanceReturnCycle() { setTime((int) Math.floorMod((long) getTime() + 1, returnInterval())); }
    void returnStorage();

    static MachineUIPanelBuilder attachRecipeLockable(MachineUIPanelBuilder builder, PanelSyncManager sync, MetaMachine machine) {
        if (!(machine instanceof IMEOutputPart part)) return builder;
        var ready = new BooleanSyncValue(part::isReturn);
        sync.syncValue("gtlcoreMEOutputReady", ready);
        sync.registerServerSyncedAction("gtlcoreMEOutputReturn", packet -> { part.returnStorage(); machine.markAsChanged(); });
        var inherited = builder.leftConfigurators();
        return builder.leftConfigurators(flow -> {
            inherited.accept(flow);
            flow.child(new ButtonWidget<>().name("gtlcoreMEOutputReturnButton").size(18)
                    .overlay(Text.dynamic(() -> Component.literal("R").withStyle(ready.getBoolValue() ? ChatFormatting.GREEN : ChatFormatting.GRAY)))
                    .tooltip(tooltip -> tooltip.addLine(Component.translatable("config.gtceu.option.hand.output")))
                    .onMousePressed((context, button) -> { if (button != 0) return false; sync.callSyncedAction("gtlcoreMEOutputReturn"); return true; }));
        });
    }
}
