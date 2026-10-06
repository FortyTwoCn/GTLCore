package org.gtlcore.gtlcore.client.gui;

import brachy.modularui.utils.Alignment;
import brachy.modularui.widgets.layout.Flow;
import java.util.List;

/** Arrange native sidebar controls before initialization; native bounds and popup handlers stay with each control. */
public final class MEStorageConfiguratorTabLayout {
    public static final int TABS_PER_COLUMN = 4;
    private MEStorageConfiguratorTabLayout() {}

    public static void arrange(Flow panel, boolean leftSide) {
        var controls = List.copyOf(panel.getChildren());
        if (controls.size() <= TABS_PER_COLUMN) return;
        if (panel.isValid()) throw new IllegalStateException("Configurator columns must be built before widget initialization");
        var columns = Flow.row().name("gtlcoreConfiguratorColumns").coverChildren().childPadding(2)
                .reverseLayout(leftSide).crossAxisAlignment(Alignment.CrossAxis.END);
        for (int start = 0; start < controls.size(); start += TABS_PER_COLUMN) {
            var column = Flow.col().coverChildren().childPadding(2).reverseLayout(panel.isReverseLayout());
            for (int i = start; i < Math.min(start + TABS_PER_COLUMN, controls.size()); i++) column.child(controls.get(i));
            columns.child(column);
        }
        // The native builder has not initialized any child yet; retain their identity and sync handlers when reparenting.
        panel.getChildren().clear();
        panel.child(columns);
    }
}
