package org.gtlcore.gtlcore.common.machine.multiblock.electric;

import org.gtlcore.gtlcore.api.recipe.RecipeResult;
import org.gtlcore.gtlcore.common.data.GTLMultiblockDisplays;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.data.GTBlocks;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

import org.jetbrains.annotations.Nullable;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.value.sync.PanelSyncManager;

import java.util.List;
import java.util.ArrayList;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class GreenhouseMachine extends WorkableElectricMultiblockMachine {

    public GreenhouseMachine(BlockEntityCreationInfo info) {
        super(info);
    }

    private int SkyLight = 15;

    private void getGreenhouseLight() {
        Level level = getLevel();
        BlockPos pos = getBlockPos();
        SkyLight = 15;
        BlockPos[] coordinates = new BlockPos[] {
                pos.offset(1, 2, 0),
                pos.offset(1, 2, 1),
                pos.offset(1, 2, -1),
                pos.offset(0, 2, 1),
                pos.offset(0, 2, -1),
                pos.offset(-1, 2, 0),
                pos.offset(-1, 2, 1),
                pos.offset(-1, 2, -1),
                pos.offset(2, 2, 0),
                pos.offset(2, 2, -1),
                pos.offset(2, 2, 1),
                pos.offset(3, 2, 0),
                pos.offset(3, 2, -1),
                pos.offset(3, 2, 1),
                pos.offset(-2, 2, 0),
                pos.offset(-2, 2, -1),
                pos.offset(-2, 2, 1),
                pos.offset(-3, 2, 0),
                pos.offset(-3, 2, -1),
                pos.offset(-3, 2, 1),
                pos.offset(-1, 2, 2),
                pos.offset(0, 2, 2),
                pos.offset(1, 2, 2),
                pos.offset(-1, 2, 3),
                pos.offset(0, 2, 3),
                pos.offset(1, 2, 3),
                pos.offset(-1, 2, -2),
                pos.offset(0, 2, -2),
                pos.offset(1, 2, -2),
                pos.offset(-1, 2, -3),
                pos.offset(0, 2, -3),
                pos.offset(1, 2, -3) };
        for (BlockPos i : coordinates) {
            if (level != null && !level.hasChunkAt(i)) { SkyLight = 0; return; }
            if (level != null && level.getBlockState(i).getBlock() == GTBlocks.CASING_TEMPERED_GLASS.get()) {
                int l = level.getBrightness(LightLayer.SKY, i.offset(0, 1, 0)) - level.getSkyDarken();
                if (l < SkyLight) {
                    SkyLight = l;
                }
            }
        }
    }

    @Override
    public boolean beforeWorking(@Nullable GTRecipe recipe) {
        getGreenhouseLight();
        if (SkyLight == 0) {
            getRecipeLogic().interruptRecipe();
            RecipeResult.of(this, RecipeResult.FAIL_NO_SKYLIGHT);
            return false;
        }
        return super.beforeWorking(recipe);
    }

    @Override
    public boolean onWorking() {
        boolean value = super.onWorking();
        if (getOffsetTimer() % 20 == 0) {
            getGreenhouseLight();
            if (SkyLight == 0) {
                RecipeResult.of(this, RecipeResult.FAIL_NO_SKYLIGHT);
                getRecipeLogic().setProgress(0);
            }
            if (SkyLight < 13) {
                RecipeResult.of(this,
                        RecipeResult.fail(Component.translatable("gtceu.recipe.fail.no.enough.skylight")));
                getRecipeLogic().setProgress(getRecipeLogic().getProgress() - 10);
            }
        }
        return value;
    }

    @Override
    public List<IWidget> getWidgetsForDisplay(PanelSyncManager syncManager) {
        var widgets = new ArrayList<IWidget>(super.getWidgetsForDisplay(syncManager));
        widgets.addAll(GTLMultiblockDisplays.syncedLines("gtlGreenhouseLight", (controller, lines) -> {
            if (isFormed()) {
                getGreenhouseLight();
                lines.add(Component.translatable("tooltip.gtlcore.current_light_level", SkyLight));
            }
        }).apply(this, syncManager));
        return widgets;
    }
}
