package org.gtlcore.gtlcore.common.machine.multiblock.part.ae;

import org.gtlcore.gtlcore.api.machine.trait.AECraft.IMECraftParallelCore;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;

public class MECraftParallelCorePartMachine extends MultiblockPartMachine implements com.gregtechceu.gtceu.api.machine.feature.IMuiMachine, IMECraftParallelCore {

    public final static int PARALLEL = 4194304;

    public MECraftParallelCorePartMachine(BlockEntityCreationInfo holder) {
        super(holder);
    }

    @Override
    public int getParallel() {
        return PARALLEL;
    }

    @Override
    public boolean shouldOpenUI(Player player, InteractionHand hand, BlockHitResult hit) {
        return false;
    }
}
