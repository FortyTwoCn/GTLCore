package org.gtlcore.gtlcore.api.gui;

import org.gtlcore.gtlcore.client.gui.PatternBufferProxyPositionConfiguratorClient;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.Items;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.drawable.ItemDrawable;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.GenericListSyncHandler;
import brachy.modularui.value.sync.DynamicLinkedSyncHandler;
import brachy.modularui.widgets.DynamicSyncedWidget;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.layout.Flow;
import brachy.modularui.widget.scroll.VerticalScrollData;
import brachy.modularui.widget.ScrollWidget;
import com.gregtechceu.gtceu.common.mui.widgets.PopupPanel;
import java.util.List;
import java.util.function.Supplier;

/** A server-synchronized list of proxy positions with client-local highlighting. */
public final class PatternBufferProxyPositionConfigurator {
    private final Supplier<List<BlockPos>> positions;
    public PatternBufferProxyPositionConfigurator(Supplier<List<BlockPos>> positions) { this.positions = positions; }

    public PopupPanel createPanel(PanelSyncManager sync) {
        var positionsSync = new GenericListSyncHandler<FriendlyByteBuf, BlockPos>(
                () -> List.copyOf(positions.get()), null, buffer -> buffer.readBlockPos(),
                (buffer, position) -> buffer.writeBlockPos(position), BlockPos::equals, BlockPos::immutable);
        sync.syncValue("patternBufferProxyPositions", positionsSync);
        var contents = new DynamicLinkedSyncHandler<>(positionsSync).widgetProvider((manager, value) -> {
            var snapshot = List.copyOf(value.getValue());
            var list = Flow.col().coverChildren().width(174);
            if (snapshot.isEmpty()) list.child(Text.lang("gui.gtlcore.pattern_buffer_proxy_positions.empty").asWidget());
            else {
                list.child(Text.lang("gui.gtlcore.pattern_buffer_proxy_positions.count", snapshot.size()).asWidget().height(16));
                for (int i = 0; i < snapshot.size(); i++) {
                    BlockPos pos = snapshot.get(i);
                    int index = i + 1;
                    list.child(new ButtonWidget<>().size(174, 18)
                            .overlay(Text.lang("gui.gtlcore.pattern_buffer_proxy_positions.entry", index, pos.getX(), pos.getY(), pos.getZ()))
                            .onMousePressed((context, button) -> {
                                if (button != 0 || !manager.isClient()) return false;
                                PatternBufferProxyPositionConfiguratorClient.handlePositionClick(pos);
                                return true;
                            }));
                }
            }
            return new ScrollWidget<>(new VerticalScrollData()).size(180, 102).child(list);
        });
        return PopupPanel.createPopupPanel("patternBufferProxyPositionsPanel", 190, 128)
                .child(Text.lang("gui.gtlcore.pattern_buffer_proxy_positions").asWidget().pos(4, 4))
                .child(new DynamicSyncedWidget<>().syncHandler(contents).pos(4, 22).size(180, 102));
    }

    public ItemDrawable getIcon() { return new ItemDrawable(Items.COMPASS); }
}
