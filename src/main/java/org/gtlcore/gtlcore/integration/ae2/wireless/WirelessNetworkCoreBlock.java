package org.gtlcore.gtlcore.integration.ae2.wireless;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.item.tool.ToolHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class WirelessNetworkCoreBlock extends MetaMachineBlock {

    public static final DirectionProperty FACING = RotationState.ALL.property;

    public WirelessNetworkCoreBlock() {
        super(prepareProperties(), WirelessNetworkCoreBlockEntity.definition());
        MachineDefinition.clearBuilt();
    }

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @NotNull ItemInteractionResult useItemOn(ItemStack heldStack, @NotNull BlockState state, @NotNull Level level,
                                                    @NotNull BlockPos pos, Player player, @NotNull InteractionHand hand,
                                                    @NotNull BlockHitResult hit) {
        if (!ToolHelper.getToolTypes(heldStack).isEmpty()) {
            return super.useItemOn(heldStack, state, level, pos, player, hand, hit);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level,
                                                     @NotNull BlockPos pos, Player player, @NotNull BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof WirelessNetworkCoreBlockEntity core) {
            WirelessNetworkCoreMenu.open(serverPlayer, core);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, LivingEntity placer, @NotNull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player && level.getBlockEntity(pos) instanceof WirelessNetworkCoreBlockEntity core) {
            core.setOwner(player);
        }
    }

    @Override
    public void onRemove(BlockState state, @NotNull Level level, @NotNull BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof WirelessNetworkCoreBlockEntity core) {
            core.removeNetwork(serverLevel.getServer());
        } else if (!state.is(newState.getBlock()) && !level.isClientSide && level instanceof ServerLevel serverLevel) {
            for (UUID frequency : WirelessAeSavedData.get(serverLevel.getServer())
                    .removeNetworksAtCore(GlobalPos.of(serverLevel.dimension(), pos))) {
                WirelessAeNetworkRuntime.disconnectFrequency(frequency);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    private static BlockBehaviour.Properties prepareProperties() {
        MachineDefinition.setBuilt(WirelessNetworkCoreBlockEntity.definition());
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_PURPLE)
                .strength(5.0F)
                .requiresCorrectToolForDrops();
    }
}
