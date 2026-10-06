package org.gtlcore.gtlcore.common.player;

import org.gtlcore.gtlcore.network.GTLNetworkHandler;
import org.gtlcore.gtlcore.network.packet.SSetNoClip;
import org.gtlcore.gtlcore.utils.MachineUtil;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = GTLNetworkHandler.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class NoClipManager {

    private static final String PERSISTENT_KEY = GTLNetworkHandler.MOD_ID + ":no_clip_enabled";
    private static final String FLYING_KEY = GTLNetworkHandler.MOD_ID + ":flying";
    private static final String LEGACY_INFINITY_FLYING_KEY = GTLNetworkHandler.MOD_ID + ":infinity_flying";
    private static final String STATE_VERSION_KEY = GTLNetworkHandler.MOD_ID + ":no_clip_state_version";
    private static final int STATE_VERSION = 2;
    private static final int FLIGHT_RESTORE_TIMEOUT_TICKS = 200;
    private static final Set<Player> ENABLED_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<Player, FlightRestore> PENDING_FLIGHT_RESTORES = new ConcurrentHashMap<>();

    private NoClipManager() {}

    public static boolean isEnabled(Player player) {
        return ENABLED_PLAYERS.contains(player);
    }

    public static void setEnabled(Player player, boolean enabled) {
        boolean active = enabled && MachineUtil.hasNoClipArmorSet(player);
        if (active) {
            ENABLED_PLAYERS.add(player);
        } else {
            ENABLED_PLAYERS.remove(player);
            player.noPhysics = player.isSpectator();
        }
        setPersistedEnabled(player, active);
    }

    private static void setPersistedEnabled(Player player, boolean enabled) {
        if (!player.level().isClientSide) {
            player.getPersistentData().putBoolean(PERSISTENT_KEY, enabled);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            migrateLeakedAbilities(serverPlayer);
            queueFlightRestore(serverPlayer);
            setEnabled(serverPlayer, serverPlayer.getPersistentData().getBoolean(PERSISTENT_KEY));
            GTLNetworkHandler.INSTANCE.sendTo(new SSetNoClip(isEnabled(serverPlayer)), serverPlayer);
        }
    }

    private static void migrateLeakedAbilities(ServerPlayer player) {
        if (player.getPersistentData().getInt(STATE_VERSION_KEY) >= STATE_VERSION) {
            return;
        }

        if (player.getPersistentData().contains(PERSISTENT_KEY) && !player.isCreative() && !player.isSpectator()) {
            Abilities abilities = player.getAbilities();
            abilities.flying = false;
            if (!MachineUtil.hasNoClipArmorSet(player)) {
                abilities.mayfly = false;
            }
            player.setNoGravity(false);
            player.noPhysics = false;
            player.onUpdateAbilities();
        }
        player.getPersistentData().putInt(STATE_VERSION_KEY, STATE_VERSION);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ENABLED_PLAYERS.remove(event.getEntity());
        PENDING_FLIGHT_RESTORES.remove(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            queueFlightRestore(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawned(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            queueFlightRestore(serverPlayer);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        updateFlightState(serverPlayer);
        if (!ENABLED_PLAYERS.contains(event.getEntity())) {
            return;
        }
        if (!MachineUtil.hasNoClipArmorSet(event.getEntity())) {
            setEnabled(event.getEntity(), false);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ENABLED_PLAYERS.clear();
        PENDING_FLIGHT_RESTORES.clear();
    }

    private static void queueFlightRestore(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) {
            PENDING_FLIGHT_RESTORES.remove(player);
            return;
        }

        var data = player.getPersistentData();
        boolean flying;
        if (data.contains(FLYING_KEY)) {
            flying = data.getBoolean(FLYING_KEY);
        } else if (data.contains(LEGACY_INFINITY_FLYING_KEY)) {
            flying = data.getBoolean(LEGACY_INFINITY_FLYING_KEY);
            data.putBoolean(FLYING_KEY, flying);
        } else {
            flying = true;
        }
        PENDING_FLIGHT_RESTORES.put(player, new FlightRestore(flying, FLIGHT_RESTORE_TIMEOUT_TICKS));
    }

    private static void updateFlightState(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) {
            PENDING_FLIGHT_RESTORES.remove(player);
            return;
        }

        Abilities abilities = player.getAbilities();
        FlightRestore restore = PENDING_FLIGHT_RESTORES.get(player);
        if (restore != null) {
            if (abilities.mayfly) {
                if (abilities.flying != restore.flying()) {
                    abilities.flying = restore.flying();
                    player.onUpdateAbilities();
                }
                player.getPersistentData().putBoolean(FLYING_KEY, abilities.flying);
                PENDING_FLIGHT_RESTORES.remove(player);
                return;
            }
            if (restore.remainingTicks() <= 1) {
                player.getPersistentData().putBoolean(FLYING_KEY, false);
                PENDING_FLIGHT_RESTORES.remove(player);
            } else {
                PENDING_FLIGHT_RESTORES.put(
                        player, new FlightRestore(restore.flying(), restore.remainingTicks() - 1));
            }
            return;
        }

        if (abilities.mayfly) {
            player.getPersistentData().putBoolean(FLYING_KEY, abilities.flying);
        }
    }

    private record FlightRestore(boolean flying, int remainingTicks) {}
}
