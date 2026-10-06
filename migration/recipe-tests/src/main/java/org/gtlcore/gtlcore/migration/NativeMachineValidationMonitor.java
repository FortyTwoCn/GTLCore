package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.machine.trait.*;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.StorageMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.apache.logging.log4j.LogManager;
import java.util.UUID;

/** Logs actual server state after native ModularUI C2S packets; no fixture packet or fake client screen. */
@EventBusSubscriber(modid = "gtlcore_recipe_test")
public final class NativeMachineValidationMonitor {
    private static volatile BlockPos position;
    private static UUID player;
    private static String previous;
    private static boolean saved;
    public static void prepare(MinecraftServer server, UUID playerId) {
        var serverPlayer = server.getPlayerList().getPlayer(playerId);
        if (serverPlayer == null) return;
        player = playerId; position = serverPlayer.blockPosition().offset(3, 0, 0);
        var level = serverPlayer.serverLevel();
        level.setBlockAndUpdate(position, NativeMachineFixtures.BATCH.getBlock().defaultBlockState());
        var machine = (StorageMachine) level.getBlockEntity(position);
        machine.setFrontFacing(Direction.NORTH); machine.checkAndFormStructure(); machine.setWorkingEnabled(false);
        machine.setBatchEnabled(false); ((IRecipeCapabilityMachine) machine).setDistinct(false); ((ILockRecipe) machine.getRecipeLogic()).setLock(false);
        LogManager.getLogger().info("GTL_NATIVE_MACHINE_UI_OPEN={} formed={} machine={}", position.toShortString(), machine.isFormed(), machine.getDefinition().getId());
    }
    public static BlockPos position() { return position; }
    public static void openMenu(MinecraftServer server) {
        var serverPlayer = server.getPlayerList().getPlayer(player);
        if (serverPlayer == null) return;
        var machine = (StorageMachine) serverPlayer.serverLevel().getBlockEntity(position);
        ((IMuiMachine) machine).tryToOpenUI(serverPlayer, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(position), Direction.NORTH, position, false));
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("gtlcore.machineValidation.enabled") || player == null || position == null) return;
        var serverPlayer = event.getServer().getPlayerList().getPlayer(player);
        if (serverPlayer == null || !(serverPlayer.serverLevel().getBlockEntity(position) instanceof StorageMachine machine)) return;
        var core = (IRecipeCapabilityMachine) machine; var lock = (ILockRecipe) machine.getRecipeLogic();
        String state = "batch=" + machine.isBatchEnabled() + " distinct=" + core.isDistinct() + " lock=" + lock.isLock();
        if (!state.equals(previous)) {
            LogManager.getLogger().info("GTL_NATIVE_MACHINE_UI_SERVER_STATE={} menu={}", state, serverPlayer.containerMenu.getClass().getName());
            previous = state;
        }
        if (!saved && machine.isBatchEnabled() && core.isDistinct() && lock.isLock()) {
            saved = true;
            var snapshot = machine.saveWithoutMetadata(serverPlayer.registryAccess());
            machine.setBatchEnabled(false); core.setDistinct(false); lock.setLock(false);
            machine.loadAdditional(snapshot, serverPlayer.registryAccess());
            if (!machine.isBatchEnabled() || !core.isDistinct() || !lock.isLock()) throw new IllegalStateException("Native machine menu settings failed SaveField round trip");
            LogManager.getLogger().info("GTL_NATIVE_MACHINE_UI_SERVER_SAVE_RESTORE=PASS batch=true distinct=true lock=true");
        }
    }
}
