package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.item.StructureWriteBehavior;
import org.gtlcore.gtlcore.common.machine.multiblock.part.MEDualHatchStockPartMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import org.apache.logging.log4j.LogManager;
import java.util.UUID;

/** Scoped integrated-server proof: real factory menus and native screen input events. */
@EventBusSubscriber(modid = "gtlcore_recipe_test")
public final class NativeItemMenusClientFixtures {
    static volatile int phase;
    static volatile boolean pageClickConfirmed, rotationClickConfirmed;
    static volatile BlockPos position;
    private static UUID playerId;
    private static volatile boolean filled;
    static void prepare(net.minecraft.server.MinecraftServer server, UUID player) {
        var owner = server.getPlayerList().getPlayer(player); if (owner == null) return;
        for (String category : new String[]{"sc", "sepm", "cal", "coil"})
            org.gtlcore.gtlcore.common.block.BlockMap.tierBlockMap.put(category, net.neoforged.neoforge.common.util.Lazy.of(() ->
                    new net.minecraft.world.level.block.Block[]{net.minecraft.world.level.block.Blocks.IRON_BLOCK, net.minecraft.world.level.block.Blocks.GOLD_BLOCK}));
        playerId = player; position = owner.blockPosition().offset(3, 0, 0); var level = owner.serverLevel();
        level.setBlockAndUpdate(position, NativeAeGridFixtures.DUAL_STOCK.getBlock().defaultBlockState());
        var machine = (MEDualHatchStockPartMachine) level.getBlockEntity(position); machine.setFrontFacing(Direction.NORTH); machine.setMEUpdateInterval(1);
        level.setBlockAndUpdate(position.north(), appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(position.north(2), appeng.core.definitions.AEBlocks.DRIVE.block().defaultBlockState());
        var drive = (appeng.blockentity.storage.DriveBlockEntity) level.getBlockEntity(position.north(2));
        drive.getInternalInventory().setItemDirect(0, appeng.core.definitions.AEItems.ITEM_CELL_1K.stack());
        drive.getInternalInventory().setItemDirect(1, appeng.core.definitions.AEItems.FLUID_CELL_1K.stack());
    }
    static void open(net.minecraft.server.MinecraftServer server, int next) {
        var player = server.getPlayerList().getPlayer(playerId); if (player == null) return;
        if (next == 0) {
            if (!filled) return;
            var machine = (MEDualHatchStockPartMachine) player.serverLevel().getBlockEntity(position);
            ((IMuiMachine) machine).tryToOpenUI(player, InteractionHand.MAIN_HAND, new BlockHitResult(position.getCenter(), Direction.NORTH, position, false));
        } else {
            var item = switch (next) {
                case 1 -> NativeAePatternFixtures.STRUCTURE_WRITER.asStack();
                case 2 -> NativeAePatternFixtures.PATTERN_TEST.asStack();
                case 3 -> NativeAePatternFixtures.ULTIMATE_TERMINAL.asStack();
                case 4 -> NativeAePatternFixtures.PATTERN_BOX.asStack();
                case 5 -> NativeAePatternFixtures.PATTERN_MODIFIER.asStack();
                default -> throw new IllegalArgumentException("Invalid client menu phase");
            };
            if (next == 1) {
                StructureWriteBehavior.addPos(item, position); StructureWriteBehavior.addPos(item, position.offset(2, 1, 3));
            }
            player.setItemInHand(InteractionHand.OFF_HAND, item); player.inventoryMenu.broadcastChanges();
            brachy.modularui.factory.PlayerInventoryUIFactory.INSTANCE.openFromHand(player, InteractionHand.OFF_HAND);
        }
        phase = next;
    }
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("gtlcore.itemMenusValidation.enabled") || playerId == null || position == null) return;
        var player = event.getServer().getPlayerList().getPlayer(playerId); if (player == null) return;
        var machine = (MEDualHatchStockPartMachine) player.serverLevel().getBlockEntity(position);
        if (!filled && machine != null && machine.isOnline()) {
            var storage = machine.getMainNode().getGrid().getStorageService().getInventory(); var source = IActionSource.ofPlayer(player);
            var items = new net.minecraft.world.item.Item[]{Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND, Items.CARROT, Items.COPPER_INGOT};
            int[] slots = {0, 1, 16, 32, 48};
            for (int i = 0; i < items.length; i++) {
                var key = AEItemKey.of(items[i]); storage.insert(key, 13 + i * 7, Actionable.MODULATE, source);
                machine.aeItemHandler.getInventory()[slots[i]].setConfig(new GenericStack(key, 1));
            }
            var water = AEFluidKey.of(net.minecraft.world.level.material.Fluids.WATER);
            storage.insert(water, 2500, Actionable.MODULATE, source);
            machine.aeFluidHandler.getInventory()[63].setConfig(new GenericStack(water, 1)); machine.autoIO(); filled = true;
            LogManager.getLogger().info("GTL_NATIVE_ITEM_MENUS_GRID=PASS native_cells=true");
        }
        if (phase == 0 && !pageClickConfirmed && machine != null && machine.getPage() == 4) {
            pageClickConfirmed = true; LogManager.getLogger().info("GTL_NATIVE_ITEM_MENUS_PAGE_C2S=PASS page=4");
        }
        if (phase == 1 && !rotationClickConfirmed && StructureWriteBehavior.getDir(player.getOffhandItem()) == Direction.NORTH) {
            rotationClickConfirmed = true; LogManager.getLogger().info("GTL_NATIVE_ITEM_MENUS_ROTATION_C2S=PASS direction=north");
        }
    }
    @EventBusSubscriber(modid = "gtlcore_recipe_test", value = net.neoforged.api.distmarker.Dist.CLIENT)
    public static final class Client {
        private static boolean loading, prepared, opened, capturing;
        private static int ticks, completed, openWaitTicks;
        private static long started;
        private static final String[] NAMES = {"dual-stock", "structure-writer", "pattern-generator", "ultimate-terminal", "pattern-box", "pattern-modifier"};
        @SubscribeEvent public static void clientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
            var client = net.minecraft.client.Minecraft.getInstance();
            if (!Boolean.getBoolean("gtlcore.itemMenusValidation.enabled")) return;
            if (started == 0) started = System.nanoTime();
            if (System.nanoTime() - started > java.util.concurrent.TimeUnit.MINUTES.toNanos(5))
                throw new AssertionError("Native item menu validation timed out at phase " + completed);
            // TitleScreen also exists while the initial resource reload callback is still running.
            // Starting the blocking world loader from that callback prevents the overlay from completing.
            if (!loading && client.getOverlay() == null && client.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
                LogManager.getLogger().info("GTL_NATIVE_ITEM_MENUS_WORLD_LOAD=START");
                loading = true; client.createWorldOpenFlows().openWorld("Native Recipe Validation", () -> {
                    throw new IllegalStateException("Could not open the scoped native GUI validation world");
                }); return;
            }
            if (client.level == null || client.player == null || client.getSingleplayerServer() == null || capturing) return;
            var server = client.getSingleplayerServer();
            if (!prepared) {
                prepared = true; var id = client.player.getUUID(); server.execute(() -> prepare(server, id)); return;
            }
            if (!opened && filled && position != null && client.level.getBlockEntity(position) instanceof MEDualHatchStockPartMachine) {
                int next = completed;
                server.execute(() -> open(server, next)); opened = true; openWaitTicks = 0; return;
            }
            if (!(client.screen instanceof brachy.modularui.api.IMuiScreen ui)) {
                if (opened && ++openWaitTicks > 200) throw new AssertionError("Native menu did not open at phase " + completed);
                return;
            }
            if (!ui.screen().getMainPanel().isValid() || ui.screen().getContext().getGraphics() == null) return;
            if (++ticks == 20 || ticks == 35 || ticks == 50) {
                if (completed == 0) click(client, ui, "dualConfig_next_page");
                if (completed == 1 && ticks == 20) click(client, ui, "structureWriter_rotate_y");
            }
            if (ticks < 90 || completed == 0 && !pageClickConfirmed || completed == 1 && !rotationClickConfirmed) return;
            var panelArea = ui.screen().getMainPanel().getArea();
            if (panelArea.x < 0 || panelArea.y < 0 || panelArea.x + panelArea.width > client.screen.width ||
                    panelArea.y + panelArea.height > client.screen.height)
                throw new AssertionError("Native menu exceeds the scaled screen at phase " + completed);
            if (completed == 0) {
                var sync = ui.screen().getSyncManager().getPanelSyncManager(ui.screen().getMainPanel().getName());
                var display = sync.findSyncHandler("dualConfigDisplay", 0, com.gregtechceu.gtceu.integration.ae2.gui.AEConfigSyncHandler.class);
                if (display.getClientStock(63) == null || display.getClientStock(63).amount() != 2500) throw new AssertionError("Native GUI lost fluid slot 63 in S2C stock sync");
            }
            capturing = true;
            var stage = NAMES[completed];
            net.minecraft.client.Screenshot.grab(client.gameDirectory, "native-menu-" + stage + ".png", client.getMainRenderTarget(), message -> {
                LogManager.getLogger().info("GTL_NATIVE_ITEM_MENU_RENDER=PASS menu={} ticks=90 screenshot={}", stage, message.getString());
                client.execute(() -> {
                    if (++completed == NAMES.length) {
                        LogManager.getLogger().info("GTL_NATIVE_ITEM_MENUS=PASS menus=6 offhand=true page_c2s=true rotation_c2s=true stock_s2c=true");
                        client.stop(); return;
                    }
                    ticks = 0; client.player.closeContainer(); opened = false; capturing = false;
                });
            });
        }
        private static void click(net.minecraft.client.Minecraft client, brachy.modularui.api.IMuiScreen ui, String name) {
            var widget = brachy.modularui.widget.WidgetTree.findFirstWithName(ui.screen().getMainPanel(), name);
            var area = widget.getArea();
            double x = area.x + area.width / 2.0, y = area.y + area.height / 2.0;
            var context = ui.screen().getContext();
            context.updateState((int) x, (int) y, 0);
            ui.screen().onFrameUpdate();
            LogManager.getLogger().info("GTL_NATIVE_ITEM_MENU_CLICK name={} x={} y={} hover={} size={}x{}", name, x, y,
                    context.getTopHovered(), area.width, area.height);
            if (context.getTopHovered() != widget) throw new AssertionError("Native click did not hover " + name);
            client.screen.afterMouseAction();
            // ModularUI handles NeoForge's pre-event, before vanilla Screen.mouseClicked is called.
            boolean handled = net.neoforged.neoforge.client.ClientHooks.onScreenMouseClickedPre(client.screen, x, y, 0);
            if (!handled) handled = net.neoforged.neoforge.client.ClientHooks.onScreenMouseClickedPost(client.screen, x, y, 0,
                    client.screen.mouseClicked(x, y, 0));
            if (!handled)
                throw new AssertionError("Actual native screen rejected click on " + name);
            if (!net.neoforged.neoforge.client.ClientHooks.onScreenMouseReleasedPre(client.screen, x, y, 0))
                net.neoforged.neoforge.client.ClientHooks.onScreenMouseReleasedPost(client.screen, x, y, 0, client.screen.mouseReleased(x, y, 0));
        }
    }
}
