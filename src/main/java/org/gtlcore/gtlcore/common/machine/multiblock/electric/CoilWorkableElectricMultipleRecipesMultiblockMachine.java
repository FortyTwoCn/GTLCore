package org.gtlcore.gtlcore.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.block.ICoilType;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.multiblock.error.CoilMatchingError;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.block.CoilBlock;
import net.minecraft.core.BlockPos;

/** Core's coil-scaled cross-recipe controller over the native accepted pattern cache. */
public class CoilWorkableElectricMultipleRecipesMultiblockMachine extends WorkableElectricMultipleRecipesMachine {
    private ICoilType coilType = CoilBlock.CoilType.CUPRONICKEL;
    @SyncToClient private int coilTemperature = coilType.getCoilTemperature();
    @SyncToClient private int coilTier = coilType.getTier();
    public CoilWorkableElectricMultipleRecipesMultiblockMachine(BlockEntityCreationInfo info, Object... args) { super(info, args); }
    public ICoilType getCoilType() { return coilType; }
    public int getCoilTier() { return coilTier; }
    @Override public void formStructure(String name) {
        super.formStructure(name);
        ICoilType matched = null;
        for (var entry : patternStates.get(name).getCache().long2ObjectEntrySet()) {
            if (entry.getValue().getBlockState().getBlock() instanceof CoilBlock coil && GTCEuAPI.HEATING_COILS.containsKey(coil.coilType)) {
                if (matched == null) matched = coil.coilType;
                else if (matched != coil.coilType) {
                    patternStates.get(name).setError(new CoilMatchingError(BlockPos.of(entry.getLongKey()), matched, coil.coilType));
                    invalidateStructure(name); return;
                }
            }
        }
        updateCoil(matched == null ? CoilBlock.CoilType.CUPRONICKEL : matched);
    }
    @Override public void invalidateStructure(String name) { super.invalidateStructure(name); updateCoil(CoilBlock.CoilType.CUPRONICKEL); }
    private void updateCoil(ICoilType coil) {
        coilType = coil; coilTemperature = coil.getCoilTemperature(); coilTier = coil.getTier();
        getSyncDataHolder().markClientSyncFieldDirty("coilTemperature"); getSyncDataHolder().markClientSyncFieldDirty("coilTier");
    }
    @Override public int getMaxParallel() { return Math.max(1, (int) Math.pow(2, coilTemperature / 900.0)); }
}
