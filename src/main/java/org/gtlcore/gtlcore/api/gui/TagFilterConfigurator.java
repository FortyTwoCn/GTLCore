package org.gtlcore.gtlcore.api.gui;

import com.gregtechceu.gtceu.api.machine.mui.MachineUIPanelBuilder;
import com.gregtechceu.gtceu.common.mui.widgets.PopupPanel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.drawable.UITexture;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.SyncHandlers;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.ToggleButton;
import brachy.modularui.widgets.textfield.TextFieldWidget;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Native machine popup; text and sorting changes are applied by the authoritative server. */
public final class TagFilterConfigurator {
    public static final int MAX_TAG_LENGTH = 256;
    public static final UITexture ICON = UITexture.fullImage(ResourceLocation.parse("gtceu:textures/gui/widget/button_list.png"));
    private final Supplier<String> whitelist, blacklist;
    private final Consumer<String> setWhitelist, setBlacklist;
    public TagFilterConfigurator(Supplier<String> whitelist, Consumer<String> setWhitelist,
                                 Supplier<String> blacklist, Consumer<String> setBlacklist) {
        this.whitelist = whitelist; this.setWhitelist = setWhitelist;
        this.blacklist = blacklist; this.setBlacklist = setBlacklist;
    }
    public static String normalize(String text) {
        return text.length() <= MAX_TAG_LENGTH ? text : text.substring(0, MAX_TAG_LENGTH);
    }
    public PopupPanel createPanel(PanelSyncManager sync) {
        var white = SyncHandlers.string(whitelist, text -> setWhitelist.accept(normalize(text))).allowC2S();
        var black = SyncHandlers.string(blacklist, text -> setBlacklist.accept(normalize(text))).allowC2S();
        sync.syncValue("tagWhitelist", white); sync.syncValue("tagBlacklist", black);
        return PopupPanel.createPopupPanel("gtlcoreTagFilterPanel", 204, 142)
                .child(Text.lang("gui.gtlcore.tag_filter_config").asWidget().pos(6, 6).width(176))
                .child(Text.lang("gui.gtlcore.tag_whitelist").asWidget().pos(6, 28).width(192))
                .child(new TextFieldWidget().name("tagWhitelistField").value(white).setMaxLength(MAX_TAG_LENGTH).pos(6, 40).size(192, 18))
                .child(Text.lang("gui.gtlcore.tag_blacklist").asWidget().pos(6, 64).width(192))
                .child(new TextFieldWidget().name("tagBlacklistField").value(black).setMaxLength(MAX_TAG_LENGTH).pos(6, 76).size(192, 18))
                .child(Text.lang("gui.gtlcore.wildcard_info").asWidget().pos(6, 104).width(192))
                .child(Text.lang("gui.gtlcore.logic_operators").asWidget().pos(6, 120).width(192));
    }
    public MachineUIPanelBuilder attachTo(MachineUIPanelBuilder builder, PanelSyncManager sync,
                                          BooleanSupplier countSort, Consumer<Boolean> setCountSort) {
        var inherited = builder.leftConfigurators();
        var sorting = SyncHandlers.bool(countSort, setCountSort::accept).allowC2S();
        sync.syncValue("tagCountSort", sorting);
        var popup = sync.syncedPanel("gtlcoreTagFilter", true, (sm, handler) -> createPanel(sm));
        return builder.leftConfigurators(flow -> {
            inherited.accept(flow);
            flow.child(new ToggleButton().name("tagCountSortButton").size(18).value(sorting)
                    .overlay(false, Text.str("A-Z")).overlay(true, Text.str("Σ"))
                    .tooltip(t -> t.addLine(Component.translatable("tooltip.gtlcore.auto_pull_sort_mode"))));
            flow.child(new ButtonWidget<>().name("tagFilterButton").size(18).overlay(ICON)
                    .tooltip(t -> t.addLine(Component.translatable("gui.gtlcore.tag_filter_config")))
                    .onMousePressed((context, button) -> { if (button != 0) return false; popup.openPanel(); return true; }));
        });
    }
}
