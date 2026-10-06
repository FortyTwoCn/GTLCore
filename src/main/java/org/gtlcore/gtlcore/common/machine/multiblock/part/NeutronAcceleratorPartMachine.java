package org.gtlcore.gtlcore.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.common.machine.multiblock.part.EnergyHatchPartMachine;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;

import com.hepdd.gtmthings.api.machine.WirelessEnergyReceiveCoverHolder;

import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class NeutronAcceleratorPartMachine extends EnergyHatchPartMachine implements IMuiMachine, WirelessEnergyReceiveCoverHolder {

    public NeutronAcceleratorPartMachine(BlockEntityCreationInfo holder, int tier, Object... args) {
        super(holder, tier, IO.IN, 1);
    }

    @Override
    protected NotifiableEnergyContainer createEnergyContainer() {
        NotifiableEnergyContainer container;
        container = NotifiableEnergyContainer.receiverContainer(
                GTValues.V[tier] * 2, GTValues.V[tier], 1);
        // Native wireless covers pass a null side for internal charging. External cables remain front-only.
        container.setSideInputCondition((s) -> (s == null || s == this.getFrontFacing()) && this.isWorkingEnabled());
        container.setCapabilityValidator((s) -> s == null || s == this.getFrontFacing());
        return container;
    }

    public long consumeEnergy() {
        if (this.isWorkingEnabled() && this.energyContainer.getEnergyStored() > 0) {
            return Math.abs(this.energyContainer.changeEnergy(-getMaxEUConsume())) *
                    (10 + ThreadLocalRandom.current().nextInt(11));
        } else {
            return 0L;
        }
    }

    public boolean isProducing() {
        return this.isWorkingEnabled() && this.energyContainer.getEnergyStored() > 0;
    }

    @Override
    public boolean shouldOpenUI(Player player, InteractionHand hand, BlockHitResult hit) {
        return true;
    }

    public long getMaxEUConsume() {
        return Math.round(GTValues.V[tier] * 0.8);
    }
}
