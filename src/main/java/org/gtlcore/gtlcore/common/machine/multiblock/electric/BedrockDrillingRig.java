package org.gtlcore.gtlcore.common.machine.multiblock.electric;

import org.gtlcore.gtlcore.common.util.BlockStateWatcher;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;

public class BedrockDrillingRig extends WorkableElectricMultiblockMachine {

    protected BlockStateWatcher.WatcherHandle watcherHandle;
    protected final BlockPos targetPos;
    protected boolean hasBedrockAtTarget;

    public BedrockDrillingRig(BlockEntityCreationInfo holder) {
        super(holder);
        targetPos = getBlockPos().below(9);
    }

    @Override
    public void onUnload() {
        unregisterBlockWatcher();
        super.onUnload();
    }

    @Override
    public void formStructure(String name) {
        super.formStructure(name);
        if (isFormed() && getLevel() instanceof ServerLevel level) {
            registerBlockWatcher(level);
        }
    }

    @Override
    public void invalidateStructure(String name) {
        unregisterBlockWatcher();
        super.invalidateStructure(name);
    }

    protected void registerBlockWatcher(ServerLevel level) {
        unregisterBlockWatcher();
        watcherHandle = BlockStateWatcher.addWatcher(level, targetPos, this::onBlockStateChanged);
        onBlockStateChanged(level.getBlockState(targetPos));
    }

    protected void unregisterBlockWatcher() {
        if (watcherHandle != null) {
            watcherHandle.remove();
            watcherHandle = null;
        }
        hasBedrockAtTarget = false;
    }

    protected void onBlockStateChanged(BlockState newState) {
        hasBedrockAtTarget = newState != null && newState.is(Blocks.BEDROCK);
        if (hasBedrockAtTarget) this.recipeLogic.updateTickSubscription();
    }

    @Override
    public boolean beforeWorking(@Nullable GTRecipe recipe) {
        return hasBedrockAtTarget && super.beforeWorking(recipe);
    }

    @Override
    public void afterWorking() {
        super.afterWorking();
        if (getLevel() instanceof ServerLevel level && level.getBlockState(targetPos).is(Blocks.BEDROCK) &&
                ThreadLocalRandom.current().nextInt(10) == 0) {
            level.setBlockAndUpdate(targetPos, Blocks.AIR.defaultBlockState());
        }
    }
}
