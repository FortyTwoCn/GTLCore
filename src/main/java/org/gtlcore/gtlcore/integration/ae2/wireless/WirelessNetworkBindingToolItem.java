package org.gtlcore.gtlcore.integration.ae2.wireless;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public final class WirelessNetworkBindingToolItem extends Item {

    private static final String TAG_NETWORK = "WirelessNetworkId";
    private static final String TAG_NETWORK_NAME = "WirelessNetworkName";

    public WirelessNetworkBindingToolItem() {
        super(new Properties().stacksTo(1));
    }

    public static void clearSavedNetwork(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof WirelessNetworkBindingToolItem)) return;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag == null || !tag.hasUUID(TAG_NETWORK)) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> {
            data.remove(TAG_NETWORK);
            data.remove(TAG_NETWORK_NAME);
        });
        player.inventoryMenu.broadcastChanges();
        message(player, "message.gtlcore.wireless_binding_tool.cleared");
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        var level = context.getLevel();
        var player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        // Run before block use so binding never opens the machine's GUI.
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
        if (!player.mayBuild() || !level.mayInteract(player, context.getClickedPos())) {
            return InteractionResult.FAIL;
        }

        // Sneaking selects a network on cores and allows replacing a hatch binding.
        if (player.isShiftKeyDown() &&
                level.getBlockEntity(context.getClickedPos()) instanceof WirelessNetworkCoreBlockEntity core) {
            UUID frequency = core.getFrequency();
            if (!WirelessAeNetworkRuntime.canAccessNetwork(serverPlayer, frequency)) {
                return message(serverPlayer, "message.gtlcore.wireless_binding_tool.no_access");
            }
            String networkName = WirelessAeSavedData.get(serverPlayer.server).getNetworkName(frequency);
            CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> {
                data.putUUID(TAG_NETWORK, frequency);
                data.putString(TAG_NETWORK_NAME, networkName);
            });
            return message(serverPlayer, "message.gtlcore.wireless_binding_tool.saved", networkName);
        }

        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag == null || !tag.hasUUID(TAG_NETWORK)) {
            return message(serverPlayer, "message.gtlcore.wireless_binding_tool.select_core");
        }
        UUID frequency = tag.getUUID(TAG_NETWORK);
        var server = serverPlayer.server;
        var data = WirelessAeSavedData.get(server);
        if (data.getCore(frequency) == null) {
            return message(serverPlayer, "message.gtlcore.wireless_target.missing_core");
        }
        if (!WirelessAeNetworkRuntime.canAccessNetwork(serverPlayer, frequency)) {
            return message(serverPlayer, "message.gtlcore.wireless_binding_tool.no_access");
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, value -> value.putString(TAG_NETWORK_NAME, data.getNetworkName(frequency)));
        var target = WirelessAeNetworkRuntime.resolveWirelessTarget(serverPlayer.serverLevel(),
                context.getClickedPos(), context.getClickedFace(), context.getClickLocation());
        if (!level.mayInteract(player, target.blockPos())) return InteractionResult.FAIL;
        if (!WirelessAeNetworkRuntime.canBindAsWirelessTarget(serverPlayer.serverLevel(), target.blockPos(), target.side())) {
            return message(serverPlayer, "message.gtlcore.wireless_target.invalid_target");
        }
        UUID currentNetwork = data.getMemberNetwork(target);
        UUID wiredNetwork = WirelessAeNetworkRuntime.findWiredNetworkFrequency(server, target);
        UUID connectedNetwork = wiredNetwork == null ? WirelessAeNetworkRuntime.findConnectedNetworkFrequency(server, target) : wiredNetwork;
        // Persisted bindings count even when the core is offline or reconnection is pending.
        if (!player.isShiftKeyDown() && (currentNetwork != null || connectedNetwork != null)) {
            return message(serverPlayer, "message.gtlcore.wireless_binding_tool.already_bound");
        }
        if (wiredNetwork != null || (connectedNetwork != null &&
                (!connectedNetwork.equals(currentNetwork) || !WirelessAeNetworkRuntime.hasWirelessConnection(connectedNetwork, target)))) {
            return message(serverPlayer, "message.gtlcore.wireless_binding_tool.existing_connection");
        }
        if (currentNetwork != null && !WirelessAeNetworkRuntime.canAccessNetwork(serverPlayer, currentNetwork)) {
            return message(serverPlayer, "message.gtlcore.wireless_binding_tool.no_access");
        }
        var core = WirelessAeNetworkRuntime.getLoadedCore(server, frequency);
        if (core == null) return message(serverPlayer, "message.gtlcore.wireless_target.missing_core");
        if (!core.isLinkedToAeNetwork()) {
            return message(serverPlayer, "message.gtlcore.wireless_target.core_not_connected");
        }
        if (frequency.equals(currentNetwork) && WirelessAeNetworkRuntime.hasWirelessConnection(frequency, target)) {
            return message(serverPlayer, "message.gtlcore.wireless_target.linked_target", data.getNetworkName(frequency));
        }
        for (UUID removedNetwork : data.removeMembersAt(target.pos())) {
            WirelessAeNetworkRuntime.disconnectMembersAt(removedNetwork, target.pos());
        }
        var result = WirelessAeNetworkRuntime.connectMemberNow(server, frequency, target);
        data.addMember(frequency, target);
        WirelessAeNetworkRuntime.requestReconnect(frequency);
        boolean connected = result == WirelessAeNetworkRuntime.ConnectionResult.CONNECTED ||
                result == WirelessAeNetworkRuntime.ConnectionResult.ALREADY_CONNECTED;
        return message(serverPlayer, connected ? "message.gtlcore.wireless_target.linked_target" :
                "message.gtlcore.wireless_target.linked_target_pending", data.getNetworkName(frequency));
    }

    private static InteractionResult message(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        // Refresh renamed networks and migrate tools that only stored the network ID.
        // Keep the cached name in the component so it is available on the client and after reloads.
        if (!(level instanceof ServerLevel serverLevel) || level.getGameTime() % 20 != 0) return;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag == null || !tag.hasUUID(TAG_NETWORK)) return;
        UUID frequency = tag.getUUID(TAG_NETWORK);
        var data = WirelessAeSavedData.get(serverLevel.getServer());
        if (data.getCore(frequency) != null) {
            String networkName = data.getNetworkName(frequency);
            if (!networkName.equals(tag.getString(TAG_NETWORK_NAME))) {
                CustomData.update(DataComponents.CUSTOM_DATA, stack, value -> value.putString(TAG_NETWORK_NAME, networkName));
            }
        }
    }

    private static Component savedNetworkName(CompoundTag tag) {
        String networkName = tag.getString(TAG_NETWORK_NAME);
        return networkName.isEmpty() ? Component.translatable("tooltip.gtlcore.wireless_binding_tool.unknown_name") :
                Component.literal(networkName);
    }

    @Override
    public Component getName(ItemStack stack) {
        var name = Component.translatable(getDescriptionId(stack)).withStyle(ChatFormatting.GOLD);
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag != null && tag.hasUUID(TAG_NETWORK)) {
            name.append(Component.literal(" [").append(savedNetworkName(tag)).append("]").withStyle(ChatFormatting.AQUA));
        }
        return name;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.gtlcore.wireless_binding_tool.select").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.gtlcore.wireless_binding_tool.bind").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.gtlcore.wireless_binding_tool.rebind").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.gtlcore.wireless_binding_tool.clear").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.gtlcore.wireless_binding_tool.disconnect").withStyle(ChatFormatting.GRAY));
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tooltip.add(tag != null && tag.hasUUID(TAG_NETWORK) ?
                Component.translatable("tooltip.gtlcore.wireless_binding_tool.network", savedNetworkName(tag)).withStyle(ChatFormatting.AQUA) :
                Component.translatable("tooltip.gtlcore.wireless_binding_tool.empty").withStyle(ChatFormatting.YELLOW));
    }
}
