package org.gtlcore.gtlcore.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;

public class HugeFluidHatchPartMachine extends FluidHatchPartMachine {
    public HugeFluidHatchPartMachine(BlockEntityCreationInfo info, int tier, IO io, Object... args) {
        super(info, tier, io, 0, tier);
    }

    @Override
    protected NotifiableFluidTank createTank(int initialCapacity, int slots) {
        return new NotifiableFluidTank(slots, Integer.MAX_VALUE, io);
    }
}
