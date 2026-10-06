package org.gtlcore.gtlcore.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.ActionResult;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.OverclockingLogic;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Grinding-ball wear belongs to accepted native recipe input, never to recipe previews. */
public class IsaMillMachine extends StorageMachine {
    public IsaMillMachine(BlockEntityCreationInfo info) { super(info, 1, new GrindingRecipeLogic()); }

    private static int grindingTier(ItemStack stack) {
        return switch (BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()) {
            case "kubejs:grindball_aluminium" -> 2;
            case "kubejs:grindball_soapstone" -> 1;
            default -> 0;
        };
    }

    private boolean hasGrindingBall(GTRecipe recipe) {
        return grindingTier(getMachineStorageItem()) == recipe.data.getInt("grindball");
    }

    public static ModifierFunction recipeModifier(MetaMachine machine, GTRecipe recipe) {
        if (!(machine instanceof IsaMillMachine mill) || !mill.hasGrindingBall(recipe)) return ModifierFunction.NULL;
        return OverclockingLogic.PERFECT_OVERCLOCK_SUBTICK.getModifier(machine, recipe, mill.getOverclockVoltage());
    }

    private static final class GrindingRecipeLogic extends RecipeLogic {
        @Override protected ActionResult handleRecipeIO(GTRecipe recipe, IO io) {
            if (io != IO.IN) return super.handleRecipeIO(recipe, io);
            var mill = (IsaMillMachine) getRLMachine();
            if (!mill.hasGrindingBall(recipe)) return ActionResult.fail(
                    Component.translatable("gtceu.machine.isa_mill.tooltip.0"), ItemRecipeCapability.CAP, IO.IN);
            var result = super.handleRecipeIO(recipe, io);
            if (result.isSuccess() && recipe.data.getInt("grindball") > 0) {
                var ball = mill.getMachineStorageItem().copy();
                if (ball.getDamageValue() < ball.getMaxDamage()) ball.setDamageValue(ball.getDamageValue() + 1);
                else ball = ItemStack.EMPTY;
                mill.setMachineStorageItem(ball); mill.setChanged();
            }
            return result;
        }
    }
}
