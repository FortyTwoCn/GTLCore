package org.gtlcore.gtlcore.integration.jade.provider;

import org.gtlcore.gtlcore.api.capability.BindCapability;
import org.gtlcore.gtlcore.common.machine.multiblock.part.WirelessOpticalDataHatchMachine;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.integration.jade.provider.CapabilityBlockProvider;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * @author EasterFG on 2024/10/27
 */
public class WirelessOpticalDataHatchProvide extends CapabilityBlockProvider<BindCapability> {

    public WirelessOpticalDataHatchProvide() {
        super(ResourceLocation.fromNamespaceAndPath("gtlcore", "wireless_data_hatch_provider"));
    }

    @Override
    protected @Nullable BindCapability getCapability(Level level, BlockPos pos, @Nullable Direction side) {
        if (level.hasChunkAt(pos) && MetaMachine.getMachine(level, pos) instanceof BindCapability capability) {
            return capability;
        }
        return null;
    }

    @Override
    protected void write(CompoundTag data, BindCapability capability) {
        if (capability != null) {
            data.putBoolean("is_bind", capability.bind());
            data.putString("pos", capability.pos());
        } else {
            data.putBoolean("is_bind", false);
            data.putString("pos", "");
        }
    }

    @Override
    protected void addTooltip(CompoundTag capData, ITooltip tooltip, Player player, BlockAccessor block, BlockEntity blockEntity, IPluginConfig config) {
        if (blockEntity instanceof WirelessOpticalDataHatchMachine wod) {
            if (capData.getBoolean("is_bind")) {
                if (wod.isTransmitter()) {
                    tooltip.add(Component.translatable("gtceu.machine.wireless_data_transmitter_hatch.bind", capData.getString("pos")));
                } else {
                    tooltip.add(Component.translatable("gtceu.machine.wireless_data_receiver_hatch.bind", capData.getString("pos")));
                }
            } else {
                if (wod.isTransmitter()) {
                    tooltip.add(Component.translatable("gtceu.machine.wireless_data_transmitter_hatch.unbind"));
                } else {
                    tooltip.add(Component.translatable("gtceu.machine.wireless_data_receiver_hatch.unbind"));
                }
            }
        }
    }
}
