package org.gtlcore.gtlcore.api.machine.multiblock;

import org.gtlcore.gtlcore.api.recipe.RecipeResult;
import org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.CoilWorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;

import net.minecraft.nbt.CompoundTag;

import org.jetbrains.annotations.NotNull;

import java.util.function.BiPredicate;



/**
 * @author EasterFG on 2024/12/5
 */
public class CoilWorkableElectricMultipleRecipesMachine extends CoilWorkableElectricMultiblockMachine implements ParallelMachine {

    public static final BiPredicate<CompoundTag, IRecipeLogicMachine> EBF_CHECK = (data, machine) -> {
        var tm = (CoilWorkableElectricMultiblockMachine) machine;
        var temp = tm.getCoilType().getCoilTemperature() + 100L * Math.max(0, tm.getTier() - GTValues.MV);
        if (temp >= data.getInt("ebf_temp")) return true;
        else {
            RecipeResult.of(machine, RecipeResult.FAIL_NO_ENOUGH_TEMPERATURE);
            return false;
        }
    };

    public CoilWorkableElectricMultipleRecipesMachine(BlockEntityCreationInfo holder, Object @NotNull... args) {
        this(holder, new MultipleRecipesLogic(EBF_CHECK), args);
    }

    public CoilWorkableElectricMultipleRecipesMachine(BlockEntityCreationInfo holder, MultipleRecipesLogic logic,
                                                       Object @NotNull... args) {
        super(holder, logic);
        if (args.length == 2 && args[0] instanceof Number energy && args[1] instanceof Number duration) {
            getRecipeLogic().setReduction(energy.doubleValue(), duration.doubleValue());
        }
    }

    @NotNull
    @Override
    public MultipleRecipesLogic getRecipeLogic() {
        return (MultipleRecipesLogic) super.getRecipeLogic();
    }

    public int getMaxParallel() {
        return getParallelHatch().map(hatch -> hatch.getCurrentParallel()).orElse(1);
    }
}
