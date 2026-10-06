package org.gtlcore.gtlcore.migration;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opens the actual native machine menu; released mod source sets never include this fixture. */
@EventBusSubscriber(modid = "gtlcore_recipe_test", value = Dist.CLIENT)
public final class NativeMachineClientFixtures {
    private static boolean prepared, opened;
    @SubscribeEvent public static void ready(ClientTickEvent.Post event) {
        var client = Minecraft.getInstance();
        if (opened || !Boolean.getBoolean("gtlcore.machineValidation.enabled") || client.level == null || client.player == null ||
                client.screen != null || client.getSingleplayerServer() == null) return;
        var server = client.getSingleplayerServer(); var id = client.player.getUUID();
        if (!prepared) {
            prepared = true;
            server.execute(() -> NativeMachineValidationMonitor.prepare(server, id));
        } else {
            var position = NativeMachineValidationMonitor.position();
            // A setBlock packet is broadcast at the end of the server tick. Wait for the actual client block entity.
            if (position != null && client.level.getBlockEntity(position) instanceof org.gtlcore.gtlcore.common.machine.multiblock.electric.StorageMachine) {
                opened = true;
                server.execute(() -> NativeMachineValidationMonitor.openMenu(server));
            }
        }
    }
}
