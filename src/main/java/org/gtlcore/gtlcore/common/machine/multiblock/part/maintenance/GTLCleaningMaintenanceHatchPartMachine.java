package org.gtlcore.gtlcore.common.machine.multiblock.part.maintenance;

import org.gtlcore.gtlcore.api.machine.multiblock.GTLCleanroomType;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.multiblock.CleanroomType;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.AutoMaintenanceHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.trait.CleanroomProviderTrait;
import java.util.Set;

public class GTLCleaningMaintenanceHatchPartMachine extends AutoMaintenanceHatchPartMachine {
    private final CleanroomProviderTrait cleanroomProvider;
    public GTLCleaningMaintenanceHatchPartMachine(BlockEntityCreationInfo info, Set<CleanroomType> types) {
        super(info); cleanroomProvider = attachTrait(new CleanroomProviderTrait(types)); cleanroomProvider.setActive(true);
    }
    @Override public void addedToController(MultiblockControllerMachine controller, String name) {
        super.addedToController(controller, name); ICleaningRoom.addedToController(controller, cleanroomProvider);
    }
    @Override public void removedFromController(MultiblockControllerMachine controller) {
        ICleaningRoom.removedFromController(controller, cleanroomProvider); super.removedFromController(controller);
    }
    @Override public int getTier() {
        var types = cleanroomProvider.getProvidedTypes();
        return types.contains(GTLCleanroomType.LAW_CLEANROOM) ? GTValues.UEV : types.contains(CleanroomType.STERILE_CLEANROOM) ? GTValues.ZPM : GTValues.HV;
    }
}
