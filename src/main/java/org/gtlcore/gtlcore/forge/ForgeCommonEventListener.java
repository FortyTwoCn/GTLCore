package org.gtlcore.gtlcore.forge;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.common.data.GTLItems;
import org.gtlcore.gtlcore.common.item.PatternBoxBehavior;
import org.gtlcore.gtlcore.config.ConfigHolder;
import org.gtlcore.gtlcore.utils.Registries;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;


import java.util.Objects;

@EventBusSubscriber(modid = GTLCore.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class ForgeCommonEventListener {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBindingToolLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getEntity().isShiftKeyDown() ||
                !(event.getEntity().getMainHandItem().getItem() instanceof org.gtlcore.gtlcore.integration.ae2.wireless.WirelessNetworkBindingToolItem) ||
                !org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAeNetworkRuntime.canOpenWirelessTargetMenu(event.getLevel(), event.getPos()))
            return;
        // Cancel on both sides, including creative mode, so unlinking cannot break the hatch.
        event.setCanceled(true);
        if (event.getLevel().isClientSide && event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                    new org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAePackets.UnbindWithToolPacket(event.getPos()));
        }
    }

    @SubscribeEvent
    public static void onPortalSpawnEvent(BlockEvent.PortalSpawnEvent event) {
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getItemStack().is(GTLItems.PATTERN_BOX.asItem())) {
            InteractionResult result = PatternBoxBehavior.handleBlockUse(event.getItemStack(),
                    new UseOnContext(event.getLevel(), event.getEntity(), event.getHand(), event.getItemStack(), event.getHitVec()));
            if (result != InteractionResult.PASS) {
                event.setCanceled(true);
                event.setCancellationResult(result);
                return;
            }
        }
        if (event.getEntity().level().getBlockState(event.getPos()).getBlock() == Blocks.END_PORTAL_FRAME &&
                event.getEntity().getItemInHand(event.getHand()).getItem() == Items.ENDER_EYE) {
            if (event.getEntity() instanceof ServerPlayer player &&
                    Objects.equals(Registries.getItemId(player.getOffhandItem()), "kubejs:end_data")) {
                player.getOffhandItem().setCount(player.getOffhandItem().getCount() - 1);
                return;
            }
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onFarmlandTrampleEvent(BlockEvent.FarmlandTrampleEvent event) {
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onEnderTeleportEvent(EntityTeleportEvent.EnderEntity event) {
        if (event.getEntity() instanceof EnderMan || event.getEntity() instanceof Shulker) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTickEvent(PlayerTickEvent.Post event) {
        if (ConfigHolder.INSTANCE.disableDrift && event.getEntity().level().isClientSide() && event.getEntity().xxa == 0 && event.getEntity().zza == 0) {
            event.getEntity().setDeltaMovement(event.getEntity().getDeltaMovement().multiply(0.5, 1, 0.5));
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (ConfigHolder.INSTANCE.sendUpdateMessages && event.getEntity() instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("gtlcore.message.structure_update_notice"), false);
            player.displayClientMessage(Component.translatable("block.gtceu.huge_incubator"), false);
            player.displayClientMessage(Component.translatable("gtlcore.message.close_update_notice"), false);
        }
    }

}
