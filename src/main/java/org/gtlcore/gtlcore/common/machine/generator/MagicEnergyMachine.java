package org.gtlcore.gtlcore.common.machine.generator;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.TieredEnergyMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableEnergyContainer;

import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.minecraft.MethodsReturnNonnullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class MagicEnergyMachine extends TieredEnergyMachine {

    @Nullable
    protected TickableSubscription energySubs;

    public MagicEnergyMachine(BlockEntityCreationInfo info, int tier) {
        super(info, tier, NotifiableEnergyContainer.emitterContainer(GTValues.V[tier] * 512L, GTValues.V[tier], 16L));
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote() && energySubs == null) energySubs = subscribeServerTick(this::checkEnergy);
    }

    @Override
    public void onUnload() {
        unsubscribe(energySubs); energySubs = null;
        super.onUnload();
    }

    protected void checkEnergy() {
        if (getOffsetTimer() % 20 == 0 && !Objects.requireNonNull(getLevel()).getEntitiesOfClass(EndCrystal.class, AABB.ofSize(new Vec3(getBlockPos().getX(), getBlockPos().getY() + 1, getBlockPos().getZ()), 1, 1, 1), e -> true).isEmpty()) {
            energyContainer.addEnergy(GTValues.V[getTier() + 4]);
        }
    }

}
