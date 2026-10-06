package org.gtlcore.gtlcore.common.machine.multiblock.electric;

import org.gtlcore.gtlcore.api.machine.multiblock.ParallelMachine;
import org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;

import org.jetbrains.annotations.NotNull;

public class WorkableElectricMultipleRecipesMachine extends WorkableElectricMultiblockMachine implements ParallelMachine {

    public WorkableElectricMultipleRecipesMachine(BlockEntityCreationInfo holder, Object... args) {
        this(holder, new MultipleRecipesLogic());
    }

    public WorkableElectricMultipleRecipesMachine(BlockEntityCreationInfo info, MultipleRecipesLogic logic) {
        super(info, logic);
    }

    @NotNull
    @Override
    public MultipleRecipesLogic getRecipeLogic() {
        return (MultipleRecipesLogic) super.getRecipeLogic();
    }

    public int getMaxParallel() {
        return Integer.MAX_VALUE;
    }
}
