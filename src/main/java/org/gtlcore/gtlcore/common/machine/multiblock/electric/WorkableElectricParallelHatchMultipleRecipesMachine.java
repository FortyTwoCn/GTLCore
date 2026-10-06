package org.gtlcore.gtlcore.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;

import static org.gtlcore.gtlcore.common.data.GTLRecipeModifiers.getHatchParallel;

public class WorkableElectricParallelHatchMultipleRecipesMachine extends WorkableElectricMultipleRecipesMachine {

    public WorkableElectricParallelHatchMultipleRecipesMachine(BlockEntityCreationInfo holder, Object... args) {
        super(holder, args);
    }

    @Override
    public int getMaxParallel() {
        return getHatchParallel(this);
    }
}
