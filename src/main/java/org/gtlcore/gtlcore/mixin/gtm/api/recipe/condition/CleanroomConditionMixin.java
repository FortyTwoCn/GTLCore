package org.gtlcore.gtlcore.mixin.gtm.api.recipe.condition;

import org.gtlcore.gtlcore.api.recipe.RecipeResult;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.CleanroomType;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.machine.trait.CleanroomReceiverTrait;
import com.gregtechceu.gtceu.common.recipe.condition.CleanroomCondition;
import com.gregtechceu.gtceu.config.ConfigHolder;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.*;

@Mixin(CleanroomCondition.class)
public abstract class CleanroomConditionMixin {
    @Shadow(remap = false) private CleanroomType cleanroom;
    /** @author GTLCore @reason Report the original Core failure using native provider/receiver traits. */
    @Overwrite(remap = false) public boolean testCondition(GTRecipe recipe, RecipeLogic logic) {
        var machine = logic.getMachine();
        if (!ConfigHolder.INSTANCE.machines.enableCleanroom || cleanroom == null ||
                ConfigHolder.INSTANCE.machines.cleanMultiblocks && machine instanceof MultiblockControllerMachine) return true;
        var receiver = machine.getTrait(CleanroomReceiverTrait.class);
        if (receiver == null || receiver.hasActiveCleanroom(cleanroom)) return true;
        if (machine instanceof IRecipeLogicMachine holder) RecipeResult.of(holder, RecipeResult.fail(Component.translatable(
                "gtceu.recipe.fail.cleanroom", Component.translatable(cleanroom.getTranslationKey()).getString())));
        return false;
    }
}
