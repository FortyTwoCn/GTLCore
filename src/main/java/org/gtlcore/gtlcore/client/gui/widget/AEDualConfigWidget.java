package org.gtlcore.gtlcore.client.gui.widget;

import com.gregtechceu.gtceu.common.mui.widgets.PopupPanel;
import com.gregtechceu.gtceu.integration.ae2.gui.AEConfigSyncHandler;
import com.gregtechceu.gtceu.integration.ae2.slot.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import appeng.api.stacks.*;
import brachy.modularui.api.IPanelHandler;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.value.sync.*;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.ButtonWidget;
import java.util.function.IntConsumer;

/** A single paginated native display for paired item/fluid slots. All edits are checked on the server. */
public class AEDualConfigWidget extends ParentWidget<AEDualConfigWidget> {
    public static final int CONFIG_SIZE = 16;
    private final ExportOnlyAEItemList aeItem;
    private final ExportOnlyAEFluidList aeFluid;
    private final IntConsumer pageSetter;
    private final boolean amountEditingEnabled;
    private final int count, rows, pageSize, maxPage;
    private int page, editingIndex = -1;
    private PanelSyncManager manager;
    private AEConfigSyncHandler display;
    private IntSyncValue pageValue;
    private BooleanSyncValue autoPullValue;
    private IPanelHandler amountPanel;
    private final IConfigurableSlotList combined;

    public AEDualConfigWidget(int x, int y, ExportOnlyAEItemList item, ExportOnlyAEFluidList fluid, IntConsumer setter, int page) {
        this(x, y, item, fluid, setter, page, false, 2);
    }
    public AEDualConfigWidget(int x, int y, ExportOnlyAEItemList item, ExportOnlyAEFluidList fluid,
                              IntConsumer setter, int page, boolean editable) {
        this(x, y, item, fluid, setter, page, editable, 2);
    }
    public AEDualConfigWidget(int x, int y, ExportOnlyAEItemList item, ExportOnlyAEFluidList fluid,
                              IntConsumer setter, int page, boolean editable, int rows) {
        aeItem = item; aeFluid = fluid; pageSetter = setter; amountEditingEnabled = editable;
        if (item.getConfigurableSlots() != fluid.getConfigurableSlots()) throw new IllegalArgumentException("Paired AE slot counts differ");
        count = item.getConfigurableSlots(); this.rows = Math.max(1, rows); pageSize = this.rows * 8;
        maxPage = Math.max(1, (count + pageSize - 1) / pageSize); this.page = Math.clamp(page, 1, maxPage);
        pos(x, y).size(144, this.rows * 38 + 17);
        combined = new IConfigurableSlotList() {
            @Override public int getConfigurableSlots() { return count; }
            @Override public IConfigurableSlot getConfigurableSlot(int index) {
                var item = getItemConfig(index); var fluid = getFluidConfig(index);
                return item.getConfig() != null ? item : fluid.getConfig() != null ? fluid : item.getStock() != null ? item : fluid;
            }
        };
        for (int i = 0; i < pageSize; i++) child(new AEDualConfigSlotWidget((i % 8) * 18, (i / 8) * 38, this, i));
        int navigation = this.rows * 38 + 1;
        child(new ButtonWidget<>().name("dualConfig_previous_page").overlay(Text.str("<")).pos(3, navigation).size(16).onMousePressed((context, button) -> changePage(-1, button)));
        child(new ButtonWidget<>().name("dualConfig_next_page").overlay(Text.str(">")).pos(125, navigation).size(16).onMousePressed((context, button) -> changePage(1, button)));
        child(Text.dynamic(() -> Component.literal(currentPage() + " / " + maxPage)).asWidget().pos(50, navigation + 4).width(60));
    }
    public AEDualConfigWidget syncManager(PanelSyncManager sync) {
        manager = sync;
        pageValue = SyncHandlers.intNumber(() -> page, value -> { page = Math.clamp(value, 1, maxPage); editingIndex = -1; pageSetter.accept(page); }).allowC2S();
        autoPullValue = new BooleanSyncValue(() -> aeItem.isAutoPull() || aeFluid.isAutoPull());
        sync.syncValue("dualConfigPage", pageValue); sync.syncValue("dualConfigAutoPull", autoPullValue);
        display = new AEConfigSyncHandler(combined, count); sync.syncValue("dualConfigDisplay", display);
        if (amountEditingEnabled) amountPanel = sync.syncedPanel("dualAmountEditor", true, (sm, handler) ->
                PopupPanel.createPopupPanel("dualAmountEditorPanel", 168, 58)
                        .child(new AEDualAmountSetWidget(4, 5, this).syncManager(sm)));
        sync.registerServerSyncedAction("dual_config_set", packet -> {
            int index = packet.readVarInt(); if (!canEdit(index)) return;
            var item = sync.getPlayer().containerMenu.getCarried();
            if (item.isEmpty()) return;
            var fluid = FluidUtil.getFluidContained(item);
            if (fluid.isPresent()) configure(index, GenericStack.fromFluidStack(fluid.get()));
            else configure(index, GenericStack.fromItemStack(item));
        });
        sync.registerServerSyncedAction("dual_config_ghost", packet -> {
            int index = packet.readVarInt(); boolean fluid = packet.readBoolean();
            var stack = fluid ? GenericStack.fromFluidStack(FluidStack.STREAM_CODEC.decode(packet)) :
                    GenericStack.fromItemStack(ItemStack.STREAM_CODEC.decode(packet));
            if (canEdit(index)) configure(index, stack);
        });
        sync.registerServerSyncedAction("dual_config_clear", packet -> {
            int index = packet.readVarInt(); if (canEdit(index)) { getItemConfig(index).setConfig(null); getFluidConfig(index).setConfig(null); }
        });
        sync.registerServerSyncedAction("dual_config_amount", packet -> {
            int index = packet.readVarInt(); long amount = packet.readVarLong();
            if (amountEditingEnabled && canEdit(index) && amount > 0 && getConfig(index).getConfig() != null)
                getConfig(index).setConfig(new GenericStack(getConfig(index).getConfig().what(), amount));
        });
        sync.registerServerSyncedAction("dual_config_edit", packet -> {
            int index = packet.readVarInt();
            editingIndex = amountEditingEnabled && canEdit(index) && getConfig(index).getConfig() != null ? index : -1;
        });
        return this;
    }
    private boolean canEdit(int index) { return index >= 0 && index < count && !aeItem.isAutoPull() && !aeFluid.isAutoPull(); }
    private int currentPage() { return Math.clamp(pageValue == null ? page : pageValue.getIntValue(), 1, maxPage); }
    private boolean changePage(int delta, int button) {
        if (button != 0 || pageValue == null) return false;
        pageValue.setIntValue(Math.clamp(currentPage() + delta, 1, maxPage)); disableAmount(); return true;
    }
    public int getSlotIndex(int visible) { return (currentPage() - 1) * pageSize + visible; }
    public int getSlotCount() { return count; }
    public boolean isAutoPull() { return autoPullValue == null ? aeItem.isAutoPull() || aeFluid.isAutoPull() : autoPullValue.getBoolValue(); }
    public boolean isAmountEditingEnabled() { return amountEditingEnabled; }
    public PanelSyncManager getSyncManager() { return manager; }
    public IConfigurableSlot getItemConfig(int index) { return aeItem.getConfigurableSlot(index); }
    public IConfigurableSlot getFluidConfig(int index) { return aeFluid.getConfigurableSlot(index); }
    public IConfigurableSlot getConfig(int index) { return getItemConfig(index).getConfig() != null ? getItemConfig(index) : getFluidConfig(index); }
    public boolean hasStackInConfig(GenericStack stack) { return aeItem.hasStackInConfig(stack, true) || aeFluid.hasStackInConfig(stack, true); }
    public boolean configure(int index, GenericStack stack) {
        if (!canEdit(index) || stack == null || stack.amount() <= 0 || !(stack.what() instanceof AEItemKey || stack.what() instanceof AEFluidKey)) return false;
        var existing = getConfig(index).getConfig();
        if ((existing == null || !existing.what().equals(stack.what())) && hasStackInConfig(stack)) return false;
        var configured = new GenericStack(stack.what(), amountEditingEnabled ? stack.amount() : 1);
        if (stack.what() instanceof AEItemKey) setItemConfig(index, configured); else setFluidConfig(index, configured);
        return true;
    }
    public void setItemConfig(int index, GenericStack stack) { getFluidConfig(index).setConfig(null); getItemConfig(index).setConfig(stack); }
    public void setFluidConfig(int index, GenericStack stack) { getItemConfig(index).setConfig(null); getFluidConfig(index).setConfig(stack); }
    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    public GenericStack getDisplayStack(int index, boolean stock) {
        if (index < 0 || index >= count || display == null) return null;
        return stock ? display.getClientStock(index) : display.getClientConfig(index);
    }
    public void enableAmount(int index) {
        if (!amountEditingEnabled || amountPanel == null || isAutoPull() || index < 0 || index >= count) return;
        manager.callSyncedAction("dual_config_edit", packet -> packet.writeVarInt(index)); amountPanel.openPanel();
    }
    public void disableAmount() { editingIndex = -1; if (amountPanel != null && amountPanel.isPanelOpen()) amountPanel.closePanel(); }
    public String getEditedAmount() {
        if (!canEdit(editingIndex)) return "0";
        var config = getConfig(editingIndex).getConfig(); return config == null ? "0" : Long.toString(config.amount());
    }
    public boolean isEditedFluid() {
        return canEdit(editingIndex) && getConfig(editingIndex).getConfig() != null && getConfig(editingIndex).getConfig().what() instanceof AEFluidKey;
    }
    public void setEditedAmount(String value) {
        if (!canEdit(editingIndex) || !amountEditingEnabled || value.length() > 19) return;
        try {
            long amount = Long.parseLong(value); var slot = getConfig(editingIndex);
            if (amount > 0 && slot.getConfig() != null) slot.setConfig(new GenericStack(slot.getConfig().what(), amount));
        } catch (NumberFormatException ignored) {}
    }
}
