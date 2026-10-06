package org.gtlcore.gtlcore.mixin.gtm.machine;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.generator.LargeTurbineMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.RotorHolderPartMachine;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LargeTurbineMachine.class)
public abstract class LargeTurbineMachineMixin extends WorkableElectricMultiblockMachine {

    @Unique
    private @Nullable RotorHolderPartMachine gtlcore$rotorHolder;

    public LargeTurbineMachineMixin(BlockEntityCreationInfo info) {
        super(info);
    }

    /**
     * @author GTLCore
     * @reason Cache the formed structure's rotor holder for voltage and efficiency queries.
     */
    @Overwrite(remap = false)
    private @Nullable RotorHolderPartMachine getRotorHolder() {
        return gtlcore$rotorHolder;
    }

    @Override
    public void formStructure(String substructureName) {
        super.formStructure(substructureName);
        gtlcore$rotorHolder = getParts().stream()
                .filter(RotorHolderPartMachine.class::isInstance)
                .map(RotorHolderPartMachine.class::cast)
                .findFirst().orElse(null);
    }

    @Override
    public void invalidateStructure(String substructureName) {
        super.invalidateStructure(substructureName);
        gtlcore$rotorHolder = null;
    }
}
