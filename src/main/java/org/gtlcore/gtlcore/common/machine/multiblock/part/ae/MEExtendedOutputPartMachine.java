package org.gtlcore.gtlcore.common.machine.multiblock.part.ae;

import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEFilterIOTrait;
import org.gtlcore.gtlcore.api.machine.MEExtendedOutputFancyConfigurator;
import org.gtlcore.gtlcore.integration.ae2.AEOutputIngredients;
import org.gtlcore.gtlcore.integration.ae2.AEBufferedOutput;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.common.mui.GTGuiTextures;
import com.gregtechceu.gtceu.api.machine.mui.MachineUIPanelBuilder;
import brachy.modularui.screen.UISettings;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.*;
import appeng.api.stacks.*;
import brachy.modularui.factory.PosGuiData;
import com.gregtechceu.gtceu.common.mui.widgets.PopupPanel;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widgets.ButtonWidget;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.jetbrains.annotations.NotNull;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Function;

public class MEExtendedOutputPartMachine extends MEExtendedOutputPartMachineBase {
    protected static final int FILTER_ROW = 3, FILTER_COL = 9;
    @SaveField protected final MEOutputFilterHandler filterHandler;

    public MEExtendedOutputPartMachine(BlockEntityCreationInfo info) {
        super(info);
        filterHandler = new MEOutputFilterHandler(FILTER_ROW, FILTER_COL,
                this::onFilterChanged, this::updatePriority, this::getPriority, this::setPriority);
    }
    private void onFilterChanged() { markAsChanged(); notifyHandlers(); }
    public MEOutputFilterHandler getFilterHandler() { return filterHandler; }

    @Override public MachineUIPanelBuilder getPanelBuilder(PosGuiData data, PanelSyncManager manager, UISettings settings) {
        var configurator = new MEExtendedOutputFancyConfigurator(filterHandler::createMainWidgetGroup);
        var filters = manager.syncedPanel("extendedOutputFilters", true, (sync, handler) -> configurator.createPanel(sync));
        return MachineUIPanelBuilder.panelBuilder(this).leftConfigurators(flow -> flow.child(
                new ButtonWidget<>().size(18).overlay(GTGuiTextures.BUTTON_ITEM_OUTPUT)
                        .onMousePressed((context, button) -> { filters.openPanel(); return true; })
                        .tooltipDynamic(t -> t.addLine(Component.translatable("tooltip.gtlcore.change_item_fluid_filter_priority")))));
    }
    @Override protected CompoundTag writeConfigToTag() {
        var tag = super.writeConfigToTag();
        tag.put("meOutputFilterHandler", filterHandler.serializeNBT(getLevel().registryAccess())); return tag;
    }
    @Override protected void readConfigFromTag(CompoundTag tag) {
        super.readConfigFromTag(tag);
        if (tag.contains("meOutputFilterHandler", 10)) {
            filterHandler.deserializeNBT(getLevel().registryAccess(), tag.getCompound("meOutputFilterHandler"));
            onFilterChanged();
        }
    }

    /** Preflight the complete accepted subset so a long overflow never acknowledges lost output. */
    private <T> List<T> accept(List<T> left, boolean simulate, Function<T, GenericStack> resolver) {
        var accepted = new Object2LongOpenHashMap<AEKey>(); var remaining = new ArrayList<T>();
        try {
            for (T ingredient : left) {
                var output = resolver.apply(ingredient);
                if (output == null) continue;
                boolean matches = output.what() instanceof AEItemKey key ? filterHandler.test(key.toStack(1)) :
                        output.what() instanceof AEFluidKey fluidKey && filterHandler.test(fluidKey.toStack(1));
                if (!matches) { remaining.add(ingredient); continue; }
                accepted.put(output.what(), Math.addExact(accepted.getLong(output.what()), output.amount()));
            }
            for (var entry : accepted.object2LongEntrySet()) Math.addExact(buffer.getLong(entry.getKey()), entry.getLongValue());
        } catch (ArithmeticException overflow) { return left; }
        if (!simulate && !accepted.isEmpty()) {
            for (var entry : accepted.object2LongEntrySet()) buffer.put(entry.getKey(), buffer.getLong(entry.getKey()) + entry.getLongValue());
            markAsChanged(); getMETrait().notifySelfIO();
        }
        return remaining;
    }
    @Override protected NotifiableMERecipeHandlerTrait<SizedIngredient, ItemStack> createItemOutputHandler() {
        return new MEItemOutputHandler() {
            @Override public boolean outputHasFilter() { return filterHandler.isHasItemFilter(); }
            @Override public List<SizedIngredient> meHandleRecipeOutputInner(List<SizedIngredient> left, boolean simulate) {
                return accept(left, simulate, ingredient -> AEOutputIngredients.item(ingredient, simulate));
            }
        };
    }
    @Override protected NotifiableMERecipeHandlerTrait<SizedFluidIngredient, FluidStack> createFluidOutputHandler() {
        return new MEFluidOutputHandler() {
            @Override public boolean outputHasFilter() { return filterHandler.isHasFluidFilter(); }
            @Override public List<SizedFluidIngredient> meHandleRecipeOutputInner(List<SizedFluidIngredient> left, boolean simulate) {
                return accept(left, simulate, ingredient -> AEOutputIngredients.fluid(ingredient, simulate));
            }
        };
    }
    @Override protected @NotNull IMEFilterIOTrait createMETrait() { return new MEFilterIOTrait(); }
    @Override public @NotNull IMEFilterIOTrait getMETrait() { return (IMEFilterIOTrait) meTrait; }
    @Override protected void registerDefaultServices() { getMainNode().addService(IGridTickable.class, new Ticker()); }

    protected class Ticker implements IGridTickable {
        @Override public TickingRequest getTickingRequest(IGridNode node) { return new TickingRequest(MIN_FREQUENCY, MAX_FREQUENCY, false); }
        @Override public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
            if (!getMainNode().isActive()) { isSleeping = true; return TickRateModulation.SLEEP; }
            if (buffer.isEmpty()) {
                if (ticksSinceLastCall >= MAX_FREQUENCY) { isSleeping = true; return TickRateModulation.SLEEP; }
                return TickRateModulation.SLOWER;
            }
            if (AEBufferedOutput.flush(buffer, getMainNode().getGrid(), actionSource)) { markAsChanged(); return TickRateModulation.URGENT; }
            return TickRateModulation.SLOWER;
        }
    }
    protected class MEFilterIOTrait extends MEIOTrait implements IMEFilterIOTrait {
        @Override public boolean hasItemFilter() { return filterHandler.isHasItemFilter(); }
        @Override public boolean hasFluidFilter() { return filterHandler.isHasFluidFilter(); }
    }
}
