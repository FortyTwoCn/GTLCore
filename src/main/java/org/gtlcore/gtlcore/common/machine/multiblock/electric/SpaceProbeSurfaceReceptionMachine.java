package org.gtlcore.gtlcore.common.machine.multiblock.electric;

import org.gtlcore.gtlcore.api.recipe.RecipeResult;
import org.gtlcore.gtlcore.common.data.GTLMaterials;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * @author EasterFG on 2024/12/2
 */
public class SpaceProbeSurfaceReceptionMachine extends WorkableElectricMultiblockMachine {

    @Nullable
    private BlockPos cachedCheckPos;

    public SpaceProbeSurfaceReceptionMachine(BlockEntityCreationInfo info) {
        super(info);
    }

    @Override
    public void formStructure(String substructureName) {
        super.formStructure(substructureName);
        cachedCheckPos = isFormed() ? findTopBlock() : null;
    }

    @Override
    public void invalidateStructure(String substructureName) {
        cachedCheckPos = null;
        super.invalidateStructure(substructureName);
    }

    @Override
    public void onUnload() {
        cachedCheckPos = null;
        super.onUnload();
    }

    @Nullable
    protected BlockPos findTopBlock() {
        Level level = getLevel();
        if (level == null) return null;

        BlockPos pos = getBlockPos();
        BlockPos[] coordinates = new BlockPos[] {
                pos.offset(4, 8, 0),
                pos.offset(-4, 8, 0),
                pos.offset(0, 8, 4),
                pos.offset(0, 8, -4)
        };

        for (BlockPos checkPos : coordinates) {
            if (level.hasChunkAt(checkPos) && level.getBlockState(checkPos).is(ChemicalHelper.getBlock(TagPrefix.frameGt, GTLMaterials.BlackTitanium))) {
                return checkPos.offset(0, 1, 0);
            }
        }
        return null;
    }

    @Override
    public boolean beforeWorking(@Nullable GTRecipe recipe) {
        if (!isFormed() || cachedCheckPos == null || !Objects.requireNonNull(getLevel()).hasChunkAt(cachedCheckPos)) return false;
        if (!Objects.requireNonNull(getLevel()).canSeeSky(cachedCheckPos)) {
            RecipeResult.of(this, RecipeResult.fail(Component.translatable("gtceu.recipe.fail.block")));
            return false;
        }
        return super.beforeWorking(recipe);
    }
}
