package org.gtlcore.gtlcore.common.machine.multiblock.electric;

import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import org.gtlcore.gtlcore.common.data.GTLMultiblockDisplays;
import org.gtlcore.gtlcore.common.machine.multiblock.part.TemperatureSensorPartMachine;
import org.gtlcore.gtlcore.common.machine.trait.AdvancedFluidDrillProduction;
import org.gtlcore.gtlcore.common.machine.trait.AdvancedInfiniteDrillLogic;
import org.gtlcore.gtlcore.utils.MachineUtil;
import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.block.ICoilType;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.fluids.store.FluidStorageKeys;
import com.gregtechceu.gtceu.api.machine.ConditionalSubscriptionHandler;
import com.gregtechceu.gtceu.api.machine.mui.MachineUIPanelBuilder;
import com.gregtechceu.gtceu.api.multiblock.error.CoilMatchingError;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.block.CoilBlock;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.mui.GTGuiTextures;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.drawable.GuiTextures;
import brachy.modularui.drawable.Icon;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.utils.Alignment;
import brachy.modularui.value.sync.BooleanSyncValue;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.SyncHandlers;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widget.Widget;
import brachy.modularui.widgets.ListWidget;
import brachy.modularui.widgets.ToggleButton;
import brachy.modularui.widgets.slot.ItemSlot;
import brachy.modularui.widgets.slot.SlotGroup;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.math.BigInteger;

/** Original five-by-five infinite drill with native recipe, thermal and menu ownership. */
public class AdvancedInfiniteDrillMachine extends StorageMachine {
    public static final double RUNNING_HEAT = 2000;
    private static final FluidStack DISTILLED_WATER = GTMaterials.DistilledWater.getFluid(20000);
    private static final FluidStack OXYGEN = GTMaterials.Oxygen.getFluid(FluidStorageKeys.LIQUID, 20000);
    private static final FluidStack HELIUM = GTMaterials.Helium.getFluid(FluidStorageKeys.LIQUID, 20000);
    private static final FluidStack NITROGEN = GTMaterials.Nitrogen.getFluid(20000);

    @SyncToClient public int maxHeat = -1;
    @SaveField @SyncToClient protected int currentHeat = 300;
    @SaveField @SyncToClient protected int process;
    @SaveField @SyncToClient protected boolean fast;
    protected final ConditionalSubscriptionHandler heatSubs;
    private @Nullable TemperatureSensorPartMachine sensorMachines;

    public AdvancedInfiniteDrillMachine(BlockEntityCreationInfo info) {
        this(info, new AdvancedInfiniteDrillLogic(5));
    }
    public AdvancedInfiniteDrillMachine(BlockEntityCreationInfo info, AdvancedInfiniteDrillLogic logic) {
        super(info, 1, logic);
        heatSubs = new ConditionalSubscriptionHandler(this, this::heatUpdate, this::isFormed);
    }
    @Override public AdvancedInfiniteDrillLogic getRecipeLogic() { return (AdvancedInfiniteDrillLogic) super.getRecipeLogic(); }
    public int getCurrentHeat() { return currentHeat; }
    public int getMeltingProgress() { return process; }
    public boolean isFastMode() { return fast; }
    public void setFastMode(boolean value) {
        if (fast == value) return;
        fast = value; getSyncDataHolder().markClientSyncFieldDirty("fast"); markAsChanged();
    }
    public boolean isEmpty() { return getMachineStorageItem().isEmpty(); }
    public boolean canRunnable() { return !isEmpty() && currentHeat >= RUNNING_HEAT && maxHeat > RUNNING_HEAT && getRate() > 0; }

    @Override public void formStructure(String name) {
        super.formStructure(name);
        if (!getPatternState(name).isFormed()) return;
        ICoilType matched = null;
        for (var entry : getPatternState(name).getCache().long2ObjectEntrySet()) {
            if (!(entry.getValue().getBlockState().getBlock() instanceof CoilBlock coil) || !GTCEuAPI.HEATING_COILS.containsKey(coil.coilType)) continue;
            if (matched == null) matched = coil.coilType;
            else if (matched != coil.coilType) {
                getPatternState(name).setError(new CoilMatchingError(BlockPos.of(entry.getLongKey()), matched, coil.coilType));
                invalidateStructure(name); return;
            }
        }
        var material = matched == null ? null : matched.getMaterial();
        maxHeat = AdvancedFluidDrillProduction.maxHeat(material == null ? null : material.getResourceLocation());
        sensorMachines = getParts().stream().filter(TemperatureSensorPartMachine.class::isInstance)
                .map(TemperatureSensorPartMachine.class::cast).findFirst().orElse(null);
        getSyncDataHolder().markClientSyncFieldDirty("maxHeat"); heatSubs.initialize(getLevel());
    }
    private void clearThermalParts() {
        maxHeat = -1; sensorMachines = null; heatSubs.unsubscribe();
        getSyncDataHolder().markClientSyncFieldDirty("maxHeat");
    }
    @Override public void invalidateStructure(String name) { super.invalidateStructure(name); clearThermalParts(); }
    @Override public void onPartUnload() { super.onPartUnload(); clearThermalParts(); }
    @Override public void onUnload() { heatSubs.unsubscribe(); sensorMachines = null; super.onUnload(); }

    protected void heatUpdate() {
        if ((!fast && getOffsetTimer() % 5 != 0) || isEmpty() || maxHeat == -1) return;
        int heat = 0;
        if (getRecipeLogic().isWorking() && process <= 0) heat += (int) Math.floor(Math.abs(currentHeat - RUNNING_HEAT) / 2000D);
        if (getRecipeLogic().isWorking() || process > 0) {
            if (MachineUtil.inputFluid(this, DISTILLED_WATER)) heat--;
            else if (MachineUtil.inputFluid(this, OXYGEN)) heat -= 2;
            else if (MachineUtil.inputFluid(this, HELIUM)) heat -= 4;
            else if (MachineUtil.inputFluid(this, NITROGEN)) heat -= 8;
        }
        if (inputBlast()) heat++;
        int oldHeat = currentHeat, oldProcess = process;
        currentHeat = (int) Math.max(0, Math.min(Integer.MAX_VALUE, (long) currentHeat + heat));
        if (currentHeat > maxHeat) {
            process += fast ? 1 : 5;
            if (process >= 300) {
                process = 0; currentHeat = 300; machineStorage.setStackInSlot(0, ItemStack.EMPTY);
                getRecipeLogic().interruptRecipe();
            }
        } else if (process > 0) process--;
        if (sensorMachines != null) sensorMachines.update(currentHeat);
        if (oldHeat != currentHeat || oldProcess != process) {
            getSyncDataHolder().markClientSyncFieldDirty("currentHeat");
            getSyncDataHolder().markClientSyncFieldDirty("process"); markAsChanged();
        }
    }

    private static boolean neutroniumHead(ItemStack stack) {
        return stack.is(ChemicalHelper.get(TagPrefix.toolHeadDrill, GTMaterials.Neutronium).getItem());
    }
    private static boolean vibraniumHead(ItemStack stack) {
        var material = GTRegistries.MATERIALS.get(ResourceLocation.fromNamespaceAndPath("gtceu", "vibranium"));
        if (material == null) throw new IllegalStateException("Required Core gtceu:vibranium material is not registered");
        return stack.is(ChemicalHelper.get(TagPrefix.toolHeadDrill, material).getItem());
    }
    @Override protected boolean filter(ItemStack stack) { return !stack.isEmpty() && (neutroniumHead(stack) || vibraniumHead(stack)); }
    public int getDrillHeadRate() {
        var stack = getMachineStorageItem();
        return stack.isEmpty() ? 0 : neutroniumHead(stack) ? 50000 : vibraniumHead(stack) ? 100000 : 0;
    }
    public int getRate() {
        return AdvancedFluidDrillProduction.rate(getDrillHeadRate(), currentHeat, maxHeat, GTUtil.getTierByVoltage(getMaxVoltage()));
    }
    protected long getFluidConsume() { return (long) Math.pow(currentHeat, 1.3); }
    protected boolean inputBlast() {
        long amount = getFluidConsume(); if (amount <= 0) return false;
        var recipe = GTRecipeBuilder.ofRaw().duration(1)
                .inputFluids(LongFluidIngredient.create(new FluidStack(GTMaterials.Blaze.getFluid(), 1), amount)).build();
        return RecipeRunnerHelper.matchRecipeInputNocache(this, recipe) && RecipeRunnerHelper.handleRecipeInputNocache(this, recipe);
    }

    public void addDisplayText(List<Component> tooltips) {
        if (!isFormed()) return;
        if (isEmpty()) {
            tooltips.add(Component.translatable("gtceu.machine.advanced_infinite_driller.not_fluid_head").withStyle(ChatFormatting.RED)); return;
        }
        if (maxHeat == -1) {
            tooltips.add(Component.translatable("gtceu.machine.advanced_infinite_driller.no_coil").withStyle(ChatFormatting.RED)); return;
        }
        tooltips.add(Component.translatable("gtceu.machine.advanced_infinite_driller.range", Component.literal("5x5").withStyle(ChatFormatting.GOLD)));
        tooltips.add(Component.translatable("gtceu.machine.advanced_infinite_driller.heat", maxHeat, RUNNING_HEAT));
        tooltips.add(Component.translatable("gtceu.machine.advanced_infinite_driller.current_heat", currentHeat));
        tooltips.add(Component.translatable("gtceu.machine.advanced_infinite_driller.fast",
                Component.translatable(fast ? "gui.gtlcore.status_on" : "gui.gtlcore.status_off")));
        tooltips.add(Component.translatable("gtceu.machine.advanced_infinite_driller.process", FormattingUtil.formatNumber2Places(process / 300F * 100)).append("%"));
        var fluids = getRecipeLogic().getVeinFluids();
        if (fluids.isEmpty()) tooltips.add(Component.translatable("gtceu.multiblock.fluid_rig.drilled_fluid",
                Component.translatable("gtceu.multiblock.fluid_rig.no_fluid_in_area").withStyle(ChatFormatting.RED)));
        else fluids.forEach((fluid, produced) -> tooltips.add(Component.translatable("gtceu.machine.advanced_infinite_driller.drilled_fluid",
                fluid.getFluidType().getDescription().copy().withStyle(ChatFormatting.GREEN),
                Component.literal(FormattingUtil.formatNumbers(BigInteger.valueOf(produced).multiply(BigInteger.valueOf(getRate()))) + " mB/s").withStyle(ChatFormatting.BLUE))));
    }
    @Override public List<IWidget> getWidgetsForDisplay(PanelSyncManager syncManager) {
        var widgets = super.getWidgetsForDisplay(syncManager);
        widgets.addAll(GTLMultiblockDisplays.syncedLines("advancedFluidDrill", MULTI_UI_TEXT_PANEL_WIDTH - 6, (machine, lines) ->
                ((AdvancedInfiniteDrillMachine) machine).addDisplayText(lines)).apply(this, syncManager));
        return widgets;
    }
    @Override public Widget<?> getMainTextPanel(PanelSyncManager syncManager) {
        int height = 80;
        var rows = new ListWidget<>().name("advancedFluidRows").width(MULTI_UI_TEXT_PANEL_WIDTH - 6).height(height - 6)
                .childSeparator(Icon.EMPTY_2PX).crossAxisAlignment(Alignment.CrossAxis.START)
                .collapseDisabledChildren().posRel(Alignment.CenterLeft);
        rows.children(getWidgetsForDisplay(syncManager));
        return new ParentWidget<>().size(MULTI_UI_TEXT_PANEL_WIDTH, height).background(GuiTextures.DISPLAY).child(rows.left(3).top(3));
    }
    @Override public MachineUIPanelBuilder getPanelBuilder(PosGuiData data, PanelSyncManager sync, UISettings settings) {
        var toggle = sync.getOrCreateSyncHandler("advancedFluidFast", BooleanSyncValue.class,
                () -> new BooleanSyncValue(this::isFastMode, this::setFastMode).allowC2S());
        return super.getPanelBuilder(data, sync, settings)
                .leftConfigurators(flow -> flow.child(new ItemSlot().name("advancedFluidHead")
                        .slot(SyncHandlers.itemSlot(machineStorage.storage, 0)
                                .slotGroup(new SlotGroup("machine_storage", 1, 0, true)).accessibility(true, true))))
                .rightConfigurators(flow -> flow.child(new ToggleButton().name("advancedFluidFast")
                .value(toggle).background(GTGuiTextures.BUTTON_BATCH[0]).background(true, GTGuiTextures.BUTTON_BATCH[1])
                .tooltipAutoUpdate(true).tooltipDynamic(tooltip -> tooltip.addLine(Component.translatable("gtceu.machine.advanced_infinite_driller.fast",
                        Component.translatable(toggle.getBoolValue() ? "gui.gtlcore.status_on" : "gui.gtlcore.status_off"))))));
    }

    @Override public void buildMainUI(ParentWidget<?> contents, PosGuiData data, PanelSyncManager sync, UISettings settings) {
        contents.child(getMainTextPanel(sync).margin(4, 2));
    }
}
