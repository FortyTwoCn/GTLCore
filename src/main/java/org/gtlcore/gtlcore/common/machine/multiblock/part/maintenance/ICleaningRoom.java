package org.gtlcore.gtlcore.common.machine.multiblock.part.maintenance;

import org.gtlcore.gtlcore.api.machine.multiblock.GTLCleanroomType;
import org.gtlcore.gtlcore.mixin.gtm.api.machine.trait.CleanroomReceiverTraitAccessor;
import com.gregtechceu.gtceu.api.machine.multiblock.CleanroomType;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.common.machine.trait.CleanroomProviderTrait;
import com.gregtechceu.gtceu.common.machine.trait.CleanroomReceiverTrait;
import com.google.common.collect.ImmutableSet;
import java.util.Set;

/** Shared immutable type sets; every cleaning part attaches its own native provider trait. */
public final class ICleaningRoom {
    public static final Set<CleanroomType> DUMMY_CLEANROOM = Set.of(CleanroomType.CLEANROOM);
    public static final Set<CleanroomType> STERILE_DUMMY_CLEANROOM = Set.of(CleanroomType.CLEANROOM, CleanroomType.STERILE_CLEANROOM);
    public static final Set<CleanroomType> LAW_DUMMY_CLEANROOM = Set.of(CleanroomType.CLEANROOM, CleanroomType.STERILE_CLEANROOM, GTLCleanroomType.LAW_CLEANROOM);
    private ICleaningRoom() {}
    public static void addedToController(MultiblockControllerMachine controller, CleanroomProviderTrait provider) {
        controller.getTraitOptional(CleanroomReceiverTrait.class).ifPresent(receiver -> receiver.setCleanroomProvider(provider));
    }
    public static void removedFromController(MultiblockControllerMachine controller, CleanroomProviderTrait provider) {
        controller.getTraitOptional(CleanroomReceiverTrait.class).ifPresent(receiver -> {
            if (((CleanroomReceiverTraitAccessor) receiver).gtlcore$getCleanroomProvider() == provider) receiver.removeCleanroom();
        });
    }
    public static ImmutableSet<CleanroomType> getCleanroomTypes(Set<CleanroomType> types) { return ImmutableSet.copyOf(types); }
}
