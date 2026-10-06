package org.gtlcore.gtlcore.common.machine.multiblock.electric;

import org.gtlcore.gtlcore.common.data.GTLMultiblockDisplays;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.drawable.Icon;
import brachy.modularui.utils.Alignment;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widget.Widget;
import brachy.modularui.widgets.ListWidget;
import brachy.modularui.drawable.GuiTextures;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.gtlcore.gtlcore.common.machine.trait.INFFluidDrillLogic;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.feature.ITieredMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class INFFluidDrillMachine extends WorkableElectricMultiblockMachine implements ITieredMachine {

    private final int tier;

    public INFFluidDrillMachine(BlockEntityCreationInfo holder, int tier) {
        super(holder, new INFFluidDrillLogic());
        this.tier = tier;
    }

    @Override
    public int getTier() { return tier; }

    @NotNull
    @Override
    public INFFluidDrillLogic getRecipeLogic() {
        return (INFFluidDrillLogic) super.getRecipeLogic();
    }

    public int getEnergyTier() {
        var energyContainer = this.getCapabilitiesFlat(IO.IN, EURecipeCapability.CAP);
        if (energyContainer == null) return this.tier;
        var energyCont = new EnergyContainerList(energyContainer.stream().filter(IEnergyContainer.class::isInstance)
                .map(IEnergyContainer.class::cast).toList());

        return Math.min(this.tier + 1, Math.max(this.tier, GTUtil.getFloorTierByVoltage(energyCont.getInputVoltage())));
    }

    public void addDisplayText(List<Component> textList) {
        if (isFormed()) {
            int energyContainer = getEnergyTier();
            long maxVoltage = GTValues.V[energyContainer];
            String voltageName = GTValues.VNF[energyContainer];
            textList.add(Component.translatable("gtceu.multiblock.max_energy_per_tick", maxVoltage, voltageName));

            if (getRecipeLogic().getVeinFluid() != null) {
                // Fluid name
                Fluid drilledFluid = getRecipeLogic().getVeinFluid();
                Component fluidInfo = drilledFluid.getFluidType().getDescription().copy()
                        .withStyle(ChatFormatting.GREEN);
                textList.add(Component.translatable("gtceu.multiblock.fluid_rig.drilled_fluid", fluidInfo)
                        .withStyle(ChatFormatting.GRAY));

                // Fluid amount
                Component amountInfo = Component.literal(FormattingUtil.formatNumbers(
                        getRecipeLogic().getFluidToProduce() * 20L / INFFluidDrillLogic.MAX_PROGRESS) +
                        " mB/s").withStyle(ChatFormatting.BLUE);
                textList.add(Component.translatable("gtceu.multiblock.fluid_rig.fluid_amount", amountInfo)
                        .withStyle(ChatFormatting.GRAY));
            } else {
                Component noFluid = Component.translatable("gtceu.multiblock.fluid_rig.no_fluid_in_area")
                        .withStyle(ChatFormatting.RED);
                textList.add(Component.translatable("gtceu.multiblock.fluid_rig.drilled_fluid", noFluid)
                        .withStyle(ChatFormatting.GRAY));
            }
        } else {
            Component tooltip = Component.translatable("gtceu.multiblock.invalid_structure.tooltip")
                    .withStyle(ChatFormatting.GRAY);
            textList.add(Component.translatable("gtceu.multiblock.invalid_structure")
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.RED)
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, tooltip))));
        }
    }

    @Override
    public List<IWidget> getWidgetsForDisplay(PanelSyncManager syncManager) {
        var widgets = super.getWidgetsForDisplay(syncManager);
        widgets.addAll(GTLMultiblockDisplays.syncedLines("infiniteFluidDrill", (controller, lines) ->
                ((INFFluidDrillMachine) controller).addDisplayText(lines)).apply(this, syncManager));
        return widgets;
    }

    @Override
    public Widget<?> getMainTextPanel(PanelSyncManager syncManager) {
        // Leave room for the native title bar and inventory at GUI scale 2 on an 854x480 window.
        int height = 80;
        var rows = new ListWidget<>()
                .width(MULTI_UI_TEXT_PANEL_WIDTH - 6).height(height - 6)
                .childSeparator(Icon.EMPTY_2PX).crossAxisAlignment(Alignment.CrossAxis.START)
                .collapseDisabledChildren().posRel(Alignment.CenterLeft);
        rows.children(getWidgetsForDisplay(syncManager));
        return new ParentWidget<>().size(MULTI_UI_TEXT_PANEL_WIDTH, height).background(GuiTextures.DISPLAY)
                .child(rows.left(3).top(3));
    }

    public static int getRigMultiplier(int tier) {
        return 256;
    }

    public static Block getCasingState(int tier) {
        return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("gtlcore", "iridium_casing"))
                .orElseThrow(() -> new IllegalStateException("Required Core iridium_casing block is not registered"));
    }
}
