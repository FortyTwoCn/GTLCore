package org.gtlcore.gtlcore.integration.ae2.gui;

import org.gtlcore.gtlcore.client.gui.widget.MEOutListGridWidget;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.feature.IGridConnectedMachine;
import com.gregtechceu.gtceu.integration.ae2.utils.KeyStorage;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.value.sync.BooleanSyncValue;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.layout.Flow;
import net.minecraft.network.chat.Component;

/** Shared bounded native menus for GT item/fluid output and GTMThings dual output. */
public final class NativeMEOutputUI {
    private NativeMEOutputUI() {}
    public static void build(ParentWidget<?> parent, MetaMachine machine, PanelSyncManager sync, KeyStorage items, KeyStorage fluids) {
        var online = new BooleanSyncValue(((IGridConnectedMachine) machine)::isOnline);
        sync.syncValue("gtlcoreOutputOnline", online);
        var column = Flow.col().width(158).coverChildrenHeight().margin(4).childPadding(2)
                .child(Text.of(machine.getDefinition().asStack().getHoverName()).asWidget().size(158, 18))
                .child(Text.dynamic(() -> Component.translatable(online.getBoolValue() ?
                        "gtceu.gui.me_network.online" : "gtceu.gui.me_network.offline")).asWidget().height(12));
        boolean dual = items != null && fluids != null;
        if (items != null) column.child(new MEOutListGridWidget(items, sync, "gtlcoreOutputItems", dual ? 2 : 5));
        if (fluids != null) column.child(new MEOutListGridWidget(fluids, sync, "gtlcoreOutputFluids", dual ? 2 : 5));
        parent.child(column);
    }
}
