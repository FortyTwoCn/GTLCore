package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.data.GTLMultiblockDisplays;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import brachy.modularui.value.sync.ModularSyncManager;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.StringSyncValue;
import brachy.modularui.widgets.dynamic.DynamicWidget;
import brachy.modularui.widgets.layout.Flow;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Production display bridge with native MUI S2C values and actual dynamic client widget rows. */
class NativeMultiblockDisplayIntegrationTest {
    private static MultiblockControllerMachine machine() {
        var entry = GTMultiMachines.VACUUM_FREEZER;
        return (MultiblockControllerMachine) entry.getBlockEntityType().create(BlockPos.ZERO, entry.getBlock().defaultBlockState());
    }

    private static StringSyncValue text(PanelSyncManager sync) {
        return sync.getOrCreateSyncHandler("gtlAdditionalText", StringSyncValue.class,
                () -> { throw new AssertionError("Missing production text value"); });
    }

    private static void transfer(StringSyncValue server, StringSyncValue client) {
        var packet = Unpooled.buffer();
        try { server.write(packet); client.read(packet); assertEquals(0, packet.readableBytes()); }
        finally { packet.release(); }
    }

    @Test void nativeTextSyncRetainsTranslationsStylesAndHoverTextWithoutClientMachineReads() {
        var calls = new AtomicInteger();
        var component = Component.translatable("gtceu.multiblock.parallel", Component.literal("64").withStyle(ChatFormatting.DARK_PURPLE))
                .withStyle(style -> style.withColor(ChatFormatting.GRAY).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Core parallel limit"))));
        var callback = GTLMultiblockDisplays.syncedLines((owner, rows) -> { calls.incrementAndGet(); rows.add(component); });
        var server = new PanelSyncManager(new ModularSyncManager(false), true);
        var client = new PanelSyncManager(new ModularSyncManager(true), true);
        callback.apply(machine(), server);
        int serverCalls = calls.get();
        callback.apply(machine(), client);
        assertEquals(serverCalls, calls.get());
        assertFalse(text(server).isAllowC2S());
        transfer(text(server), text(client));
        var json = JsonParser.parseString(text(client).getStringValue()).getAsJsonArray();
        assertEquals(1, json.size());
        var decoded = Component.Serializer.fromJson(json.get(0), RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        assertEquals(component, decoded);
    }

    @Test void syncedLineCountChangesRebuildTheNativeClientRowsAndClearStaleMessages() {
        var lines = new AtomicReference<List<Component>>(List.of(Component.literal("first"), Component.literal("second")));
        var callback = GTLMultiblockDisplays.syncedLines((owner, rows) -> rows.addAll(lines.get()));
        var server = new PanelSyncManager(new ModularSyncManager(false), true);
        var client = new PanelSyncManager(new ModularSyncManager(true), true);
        callback.apply(machine(), server);
        var widgets = callback.apply(machine(), client);
        var dynamic = (DynamicWidget<?>) widgets.getFirst();
        client.initialize("native_display_test");
        assertTrue(client.isClient());
        assertTrue(dynamic.getSyncHandler().isValid());
        assertNotNull(text(client).getChangeListener());
        // MUI's MutableSingletonList.get(int) returns null in the pinned dependency; its iterator
        // supplies the actual child and is the path used by WidgetTree.
        assertNotNull(dynamic.getChildren().iterator().next(), "Native dynamic handler supplies its initial widget on init");
        transfer(text(server), text(client));
        assertEquals(2, ((Flow) dynamic.getChildren().iterator().next()).getChildren().size());
        lines.set(List.of()); assertTrue(text(server).updateCacheFromSource(false));
        transfer(text(server), text(client));
        assertEquals(0, ((Flow) dynamic.getChildren().iterator().next()).getChildren().size());
        lines.set(List.of(Component.literal("reformed")));
        assertTrue(text(server).updateCacheFromSource(false)); transfer(text(server), text(client));
        assertEquals(1, ((Flow) dynamic.getChildren().iterator().next()).getChildren().size());
        assertFalse(text(server).updateCacheFromSource(false));
    }

    @Test void controllerAndAdditionalDisplaysKeepIndependentNativeSyncHandlers() {
        var sync = new PanelSyncManager(new ModularSyncManager(false), true);
        GTLMultiblockDisplays.syncedLines("gtlMolecular", (owner, rows) -> rows.add(Component.literal("controller"))).apply(machine(), sync);
        GTLMultiblockDisplays.syncedLines((owner, rows) -> rows.add(Component.literal("additional"))).apply(machine(), sync);
        var controller = sync.getOrCreateSyncHandler("gtlMolecularText", StringSyncValue.class,
                () -> { throw new AssertionError("Missing controller text value"); });
        assertNotSame(controller, text(sync));
        assertTrue(controller.getStringValue().contains("controller"));
        assertTrue(text(sync).getStringValue().contains("additional"));
    }
}
