package org.gtlcore.gtlcore.api.machine.trait;

import com.gregtechceu.gtceu.common.mui.GTGuiTextures;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import brachy.modularui.value.sync.BooleanSyncValue;
import brachy.modularui.widgets.ToggleButton;
import brachy.modularui.widgets.layout.Flow;
import net.minecraft.network.chat.Component;

public interface ILockRecipe {
    default boolean isLock() { return false; }
    default void setLock(boolean locked) {}
    default GTRecipe getLockRecipe() { return null; }
    default void setLockRecipe(GTRecipe recipe) {}

    static void attachRecipeLockable(Flow configurators, RecipeLogic logic) {
        if (!(logic instanceof ILockRecipe lock)) return;
        var value = new BooleanSyncValue(lock::isLock, lock::setLock).allowC2S();
        configurators.child(new ToggleButton().value(value)
                .background(GTGuiTextures.PRIVATE_MODE_BUTTON[0])
                .background(true, GTGuiTextures.PRIVATE_MODE_BUTTON[1])
                .tooltipAutoUpdate(true)
                .tooltipBuilder(tooltip -> tooltip.addLine(Component.translatable("config.gtceu.option.recipes")
                        .append("[").append(Component.translatable(value.getBoolValue() ? "theoneprobe.ae2.locked" : "theoneprobe.ae2.unlocked")).append("]"))));
    }
}
