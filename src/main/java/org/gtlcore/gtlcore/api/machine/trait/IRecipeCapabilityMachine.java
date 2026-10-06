package org.gtlcore.gtlcore.api.machine.trait;

import com.gregtechceu.gtceu.api.capability.IDataAccessHatch;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ParallelHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.MaintenanceHatchPartMachine;
import com.gregtechceu.gtceu.common.mui.GTGuiTextures;
import brachy.modularui.value.sync.BooleanSyncValue;
import brachy.modularui.widgets.ToggleButton;
import brachy.modularui.widgets.layout.Flow;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import it.unimi.dsi.fastutil.objects.ReferenceSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 部分代码参考自GTO
 * &#064;line <a href="https://github.com/GregTech-Odyssey/GTOCore">...</a>
 */

public interface IRecipeCapabilityMachine extends IRecipeInputHandlerPolicy {

    @NotNull
    List<RecipeHandlePart> getNormalRecipeHandlePart(IO io);

    @Nullable
    RecipeHandlePart getSharedRecipeHandlePart();

    boolean emptyRecipeHandlePart();

    boolean emptyHandlePart();

    /** Custom Core routes supplement native groups only when the holder has opted into that policy. */
    default boolean usesCoreRecipeRouting() {
        return !emptyHandlePart();
    }

    // ==================== ME ====================

    List<MEPatternRecipeHandlePart> getMEPatternRecipeHandleParts();

    List<MEIORecipeHandlePart<?>> getMEOutputRecipeHandleParts();

    void tryAddAndActiveMERhp(MEPatternRecipeHandlePart part, GTRecipe recipe, int slot);

    void sortMEOutput();

    // ==================== Cache ====================

    @Nullable
    IRecipeHandlePart getActiveRecipeHandle(GTRecipe recipe);

    @NotNull
    Iterator<@NotNull IRecipeHandlePart> getAllCachedRecipeHandlesIter(GTRecipe recipe);

    @NotNull
    ReferenceSet<@NotNull IRecipeHandlePart> getAllCachedRecipeHandles(GTRecipe recipe);

    void tryAddAndActiveRhp(GTRecipe recipe, IRecipeHandlePart part);

    // ==================== Structure Form ====================

    void upDate();

    boolean isDistinct();

    void setDistinct(boolean isDistinct);

    default boolean itemOutPutAlwaysMatch() {
        return false;
    }

    default boolean fluidOutPutAlwaysMatch() {
        return false;
    }

    default boolean isRecipeOutputAlwaysMatch(GTRecipe recipe) {
        return false;
    }

    default Optional<ParallelHatchPartMachine> getParallelHatch() {
        return Optional.empty();
    }

    default MaintenanceHatchPartMachine getMaintenanceMachine() {
        return null;
    }

    default IDataAccessHatch getDataAccessHatch() {
        return null;
    }

    static void attachConfigurators(Flow configuratorPanel, WorkableElectricMultiblockMachine machine) {
        if (machine instanceof IRecipeCapabilityMachine distinctMachine) {
            var value = new BooleanSyncValue(distinctMachine::isDistinct, pressed -> {
                        if (distinctMachine.isDistinct() == pressed) return;
                        distinctMachine.setDistinct(pressed);
                        distinctMachine.upDate();
                    }).allowC2S();
            configuratorPanel.child(new ToggleButton().value(value)
                    .background(GTGuiTextures.BUTTON_DISTINCT[0])
                    .background(true, GTGuiTextures.BUTTON_DISTINCT[1])
                    .tooltipAutoUpdate(true)
                    .tooltipBuilder(tooltip -> tooltip.addLine(Component.translatable("gtceu.multiblock.universal.distinct.all")
                            .withStyle(ChatFormatting.YELLOW)
                            .append(Component.translatable(value.getBoolValue() ? "gtceu.multiblock.universal.distinct.yes" : "gtceu.multiblock.universal.distinct.no")))));
        }
    }
}
