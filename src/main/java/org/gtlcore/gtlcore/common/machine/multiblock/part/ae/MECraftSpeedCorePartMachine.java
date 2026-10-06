package org.gtlcore.gtlcore.common.machine.multiblock.part.ae;

import org.gtlcore.gtlcore.api.machine.trait.AECraft.IMECraftSpeedCore;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;

public class MECraftSpeedCorePartMachine extends MultiblockPartMachine implements com.gregtechceu.gtceu.api.machine.feature.IMuiMachine, IMECraftSpeedCore {

    public MECraftSpeedCorePartMachine(BlockEntityCreationInfo holder) {
        super(holder);
    }

    @Override
    public int getSpeedTier() {
        return 8;
    }

    @Override
    public boolean shouldOpenUI(Player player, InteractionHand hand, BlockHitResult hit) {
        return false;
    }
}
