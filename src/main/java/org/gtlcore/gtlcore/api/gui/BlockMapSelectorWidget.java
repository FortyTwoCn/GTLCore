package org.gtlcore.gtlcore.api.gui;

import org.gtlcore.gtlcore.common.block.BlockMap;
import com.gregtechceu.gtceu.common.mui.GTGuiTextures;
import org.gtlcore.gtlcore.common.item.UltimateTerminalBehavior;
import net.minecraft.network.chat.Component;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.drawable.ItemDrawable;
import brachy.modularui.factory.PlayerInventoryGuiData;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.SyncHandlers;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.CycleButtonWidget;
import java.util.Arrays;

/** Each native category selector remains independently saved and server validated. */
public class BlockMapSelectorWidget extends ParentWidget<BlockMapSelectorWidget> {
    public static final Component SC = Component.translatable("gui.gtlcore.stellar_thermal_container");
    public static final Component SEPM = Component.translatable("gui.gtlcore.space_elevator_module");
    public static final Component CAL = Component.translatable("gui.gtlcore.component_assembly_casing");
    public static final Component COIL = Component.translatable("gui.gtlcore.coil");
    public static Component getBlock(String type) {
        return switch (type) { case "sc" -> SC; case "sepm" -> SEPM; case "cal" -> CAL; case "coil" -> COIL;
            default -> throw new IllegalArgumentException("Unknown terminal block category: " + type); };
    }
    public BlockMapSelectorWidget(int x, int y, int width, PlayerInventoryGuiData<?> data, PanelSyncManager sync) {
        pos(x, y).size(width, 136);
        int row = 0;
        for (String type : new String[]{"sc", "sepm", "cal", "coil"}) {
            var source = BlockMap.tierBlockMap.get(type); if (source == null) continue;
            var blocks = source.get(); if (blocks.length == 0) continue;
            var value = SyncHandlers.intNumber(() -> Arrays.asList(blocks)
                    .indexOf(UltimateTerminalBehavior.getSelectedTierBlock(data.getUsedItemStack(), type)) + 1,
                    state -> {
                        if (state < 0 || state > blocks.length) return;
                        var item = data.getUsedItemStack(); UltimateTerminalBehavior.setTierSelection(item, type, state - 1); data.setUsedItemStack(item);
                    }).allowC2S();
            sync.syncValue("terminalTier_" + type, value);
            child(Text.of(getBlock(type)).asWidget().pos(2, row * 33).width(width - 4));
            var button = new CycleButtonWidget().name("terminalTier_" + type).background(GTGuiTextures.BUTTON).stateCount(blocks.length + 1).value(value)
                    .stateOverlay(0, Text.of(Component.literal("AUTO"))).size(30, 20).pos(2, row * 33 + 12);
            for (int tier = 0; tier < blocks.length; tier++) {
                var block = blocks[tier];
                button.stateOverlay(tier + 1, new ItemDrawable(block));
                button.tooltip(tier + 1, tooltip -> tooltip.addLine(block.getName()));
            }
            child(button);
            child(Text.dynamic(() -> {
                int selected = value.getIntValue() - 1;
                return selected < 0 || selected >= blocks.length ? Component.literal("AUTO") : blocks[selected].getName();
            }).asWidget().pos(36, row * 33 + 17).width(width - 38));
            row++;
        }
    }
}
