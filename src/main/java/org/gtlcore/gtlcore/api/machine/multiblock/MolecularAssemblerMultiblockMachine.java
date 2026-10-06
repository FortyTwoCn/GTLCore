package org.gtlcore.gtlcore.api.machine.multiblock;

import org.gtlcore.gtlcore.api.machine.trait.AECraft.IMECraftSpeedCore;

import org.gtlcore.gtlcore.common.data.GTLMultiblockDisplays;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.value.sync.PanelSyncManager;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import java.util.*;

public class MolecularAssemblerMultiblockMachine extends MolecularAssemblerMultiblockMachineBase {

    public MolecularAssemblerMultiblockMachine(BlockEntityCreationInfo holder) {
        super(holder);
    }

    public MolecularAssemblerMultiblockMachine(BlockEntityCreationInfo info, RecipeLogic logic) {
        super(info, logic);
    }

    // ========================================
    // GUI
    // ========================================

    public void addDisplayText(List<Component> textList) {
        textList.add(Component.translatable(getBlockState().getBlock().getDescriptionId()));
        if (isFormed()) {
            int coreCount = 0;
            for (var part : this.getParts()) {
                if (part instanceof IMECraftSpeedCore) {
                    coreCount++;
                }
            }
            textList.add(Component.translatable(getRecipeType().id.toLanguageKey())
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.AQUA)
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.translatable("gtceu.gui.machinemode.title")))));
            if (!isWorkingEnabled()) {
                textList.add(Component.translatable("gtceu.multiblock.work_paused"));
            } else if (isActive()) {
                textList.add(Component.translatable("gtceu.multiblock.running"));
                int currentProgress = (int) (recipeLogic.getProgressPercent() * 100);
                textList.add(Component.translatable("gtceu.multiblock.progress", currentProgress));
            } else {
                textList.add(Component.translatable("gtceu.multiblock.idling"));
            }
            if (recipeLogic.isWaiting()) {
                textList.add(Component.translatable("gtceu.multiblock.waiting")
                        .setStyle(Style.EMPTY.withColor(ChatFormatting.RED)));
            }
            if (maxParallel > 1) {
                textList.add(Component.translatable("gtceu.multiblock.parallel", Component.literal(FormattingUtil.formatNumbers(maxParallel)).withStyle(ChatFormatting.DARK_PURPLE))
                        .withStyle(ChatFormatting.GRAY));
            }
            textList.add(Component.translatable("gtlcore.multiblock.tick_Duration", Component.literal(FormattingUtil.formatNumbers(tickDuration)).withStyle(ChatFormatting.BLUE))
                    .withStyle(ChatFormatting.GRAY));
            textList.add(Component.translatable("gtlcore.multiblock.contains_Patttern", Component.literal(FormattingUtil.formatNumbers(patternSize)).withStyle(ChatFormatting.GOLD))
                    .withStyle(ChatFormatting.GRAY));
            textList.add(Component.translatable("gui.gtlcore.molecular_assembler_0", Component.literal(FormattingUtil.formatNumbers(maxParallel / 4194304)).withStyle(ChatFormatting.DARK_GREEN))
                    .withStyle(ChatFormatting.GRAY));
            textList.add(Component.translatable("gui.gtlcore.molecular_assembler_2", Component.literal(FormattingUtil.formatNumbers(coreCount)).withStyle(ChatFormatting.DARK_GREEN))
                    .withStyle(ChatFormatting.GRAY));
            textList.add(Component.translatable("gui.gtlcore.molecular_assembler_1", Component.literal(FormattingUtil.formatNumbers(patternSize / 108)).withStyle(ChatFormatting.DARK_GREEN))
                    .withStyle(ChatFormatting.GRAY));
        } else {
            Component tooltip = Component.translatable("gtceu.multiblock.invalid_structure.tooltip")
                    .withStyle(ChatFormatting.GRAY);
            textList.add(Component.translatable("gtceu.multiblock.invalid_structure")
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.RED)
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, tooltip))));
        }
    }

    @Override
    public List<IWidget> getWidgetsForDisplay(PanelSyncManager sync) {
        var widgets = new ArrayList<>(GTLMultiblockDisplays.syncedLines("gtlMolecular",
                (controller, text) -> ((MolecularAssemblerMultiblockMachine) controller).addDisplayText(text)).apply(this, sync));
        widgets.addAll(getDefinition().getAdditionalDisplay().apply(this, sync));
        return widgets;
    }
}
