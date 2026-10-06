package org.gtlcore.gtlcore.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;

import net.neoforged.neoforge.fluids.FluidType;

public class LargeSteamHatchPartMachine extends FluidHatchPartMachine {
    public final boolean is_steel;

    public LargeSteamHatchPartMachine(BlockEntityCreationInfo info, IO io, int buckets,
                                      boolean steel, Object... args) {
        super(info, steel ? 0 : 1, io, Math.multiplyExact(buckets, FluidType.BUCKET_VOLUME), 1);
        is_steel = steel;
    }

    @Override
    protected NotifiableFluidTank createTank(int initialCapacity, int slots) {
        return super.createTank(initialCapacity, slots)
                .setFilter(stack -> stack.getFluid().is(GTMaterials.Steam.getFluidTag()));
    }
}
