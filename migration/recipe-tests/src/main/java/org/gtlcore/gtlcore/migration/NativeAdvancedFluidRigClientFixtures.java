package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMENativeOutputBuffer;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.AdvancedInfiniteDrillMachine;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.machine.mui.MachineUIPanel;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.multiblock.util.*;
import com.gregtechceu.gtceu.common.block.CoilBlock;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import appeng.api.stacks.AEFluidKey;
import brachy.modularui.api.IMuiScreen;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.value.sync.StringSyncValue;
import brachy.modularui.widget.WidgetTree;
import brachy.modularui.widgets.ListWidget;
import brachy.modularui.widgets.TextWidget;
import brachy.modularui.widgets.ToggleButton;
import brachy.modularui.widgets.slot.ItemSlot;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.apache.logging.log4j.LogManager;
import java.util.*;

/** Actual native controller/menu transactions; separate sparse materials and reduced structure. */
@EventBusSubscriber(modid = "gtlcore_recipe_test")
public final class NativeAdvancedFluidRigClientFixtures {
    private static volatile BlockPos position;
    private static volatile boolean ready, reloaded;
    private static volatile Snapshot snapshot;
    private static UUID owner;
    private static ItemStack originalHead;
    private static Map<net.minecraft.world.level.material.Fluid, Long> base;
    private static long initialEnergy, startedEut;
    private record Snapshot(RecipeLogic.Status status, int progress, long output, long energy,
                            int heat, int process, boolean fast, ItemStack head, String display) {}

    private static void prepare(net.minecraft.server.MinecraftServer server, UUID id) {
        owner = id; var player = Objects.requireNonNull(server.getPlayerList().getPlayer(id)); var level = player.serverLevel();
        var definition = NativeAdvancedFluidRigFixtures.DRILL; var shape = new LinkedHashMap<BlockPos, BlockInfo>();
        AbstractStructureHelper.blockPattern(new Int2IntOpenHashMap(Map.of(0, 1))).populate(shape,
                definition.get().getStructurePatterns().get("main").get(), null, Direction.NORTH, Direction.UP, false);
        var anchor = player.blockPosition().offset(5, 0, 3);
        for (var relative : shape.keySet()) {
            var pos = anchor.offset(relative);
            if (level.getBlockEntity(pos) instanceof AdvancedInfiniteDrillMachine old) old.invalidateStructure("main");
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        for (var block : shape.entrySet()) {
            var pos = anchor.offset(block.getKey()); var state = block.getValue().getBlockState();
            if (state.getBlock() instanceof CoilBlock) state = NativeAdvancedFluidRigFixtures.COIL.getDefaultState();
            level.setBlockAndUpdate(pos, state); if (state.is(definition.getBlock())) position = pos;
        }
        var machine = (AdvancedInfiniteDrillMachine) level.getBlockEntity(position); machine.checkAndFormStructure(); machine.setWorkingEnabled(false);
        if (!machine.isFormed() || machine.maxHeat != 8000 || machine.getParts().size() != 5) throw new AssertionError("Native advanced client rig did not bind actual coils/parts");
        originalHead = ChemicalHelper.get(TagPrefix.toolHeadDrill, GTMaterials.Neutronium).copy();
        originalHead.set(DataComponents.CUSTOM_NAME, Component.literal("Owned thermal drill head")); machine.setMachineStorageItem(originalHead.copy());
        NativeAdvancedFluidRigGameTests.heat(machine, 8000, 0); machine.setFastMode(false);
        base = NativeAdvancedFluidRigGameTests.deposits(machine, 1001, true);
        machine.getEnergyContainer().changeEnergy(Long.MAX_VALUE); initialEnergy = machine.getEnergyContainer().getEnergyStored(); ready = true;
    }
    private static long output(AdvancedInfiniteDrillMachine machine) {
        var buffer = machine.getParts().stream().filter(IMENativeOutputBuffer.class::isInstance).map(IMENativeOutputBuffer.class::cast)
                .findFirst().orElseThrow().gtlcore$getOutputBuffer();
        long sum = 0;
        for (var entry : base.entrySet()) {
            long quantity = buffer.storage.getLong(AEFluidKey.of(entry.getKey()));
            if (quantity != 0 && quantity != entry.getValue() * 25000L) throw new AssertionError("Native advanced client lost exact fluid ownership: " + quantity);
            sum = Math.addExact(sum, quantity);
        }
        return sum;
    }
    private static Snapshot state(AdvancedInfiniteDrillMachine machine) {
        var lines = new ArrayList<Component>(); machine.addDisplayText(lines); var array = new com.google.gson.JsonArray();
        for (var line : lines) array.add(com.google.gson.JsonParser.parseString(Component.Serializer.toJson(line, machine.getLevel().registryAccess())));
        return new Snapshot(machine.getRecipeLogic().getStatus(), machine.getRecipeLogic().getProgress(), output(machine),
                machine.getEnergyContainer().getEnergyStored(), machine.getCurrentHeat(), machine.getMeltingProgress(), machine.isFastMode(), machine.getMachineStorageItem().copy(), array.toString());
    }
    private static void open(net.minecraft.server.MinecraftServer server) {
        var player = server.getPlayerList().getPlayer(owner);
        ((AdvancedInfiniteDrillMachine) player.serverLevel().getBlockEntity(position)).tryToOpenUI(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(position.getCenter(), Direction.NORTH, position, false));
    }
    private static void reload(net.minecraft.server.MinecraftServer server) {
        var player = server.getPlayerList().getPlayer(owner); player.closeContainer(); var level = player.serverLevel();
        var machine = (AdvancedInfiniteDrillMachine) level.getBlockEntity(position); var before = state(machine);
        var saved = machine.saveWithoutMetadata(level.registryAccess()); var block = machine.getBlockState(); machine.invalidateStructure("main"); level.removeBlockEntity(position);
        var copy = (AdvancedInfiniteDrillMachine) NativeAdvancedFluidRigFixtures.DRILL.getBlockEntityType().create(position, block);
        copy.loadAdditional(saved, level.registryAccess()); level.setBlockEntity(copy); copy.checkAndFormStructure();
        if (!copy.isFormed() || !copy.getRecipeLogic().isSuspend() || copy.getCurrentHeat() != before.heat || copy.getMeltingProgress() != before.process
                || copy.isFastMode() != before.fast || !ItemStack.isSameItemSameComponents(copy.getMachineStorageItem(), originalHead)
                || output(copy) != 6_256_250_000L || copy.getEnergyContainer().getEnergyStored() != before.energy)
            throw new AssertionError("Native advanced client reload lost heat, mode, component head, completed ownership or energy");
        snapshot = state(copy); reloaded = true;
    }
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("gtlcore.advancedFluidRigValidation.enabled") || !ready || owner == null) return;
        var player = event.getServer().getPlayerList().getPlayer(owner);
        if (player != null && player.serverLevel().getBlockEntity(position) instanceof AdvancedInfiniteDrillMachine machine) {
            if (startedEut == 0 && machine.getRecipeLogic().isWorking()) startedEut = machine.getRecipeLogic().getLastRecipe().getInputEUt().getTotalEU();
            snapshot = state(machine);
        }
    }

    @EventBusSubscriber(modid = "gtlcore_recipe_test", value = net.neoforged.api.distmarker.Dist.CLIENT)
    public static final class Client {
        private static boolean loading, prepared, opened, capturing;
        private static int phase, ticks;
        private static long started;
        @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
            if (!Boolean.getBoolean("gtlcore.advancedFluidRigValidation.enabled")) return;
            var client = net.minecraft.client.Minecraft.getInstance(); if (started == 0) started = System.nanoTime();
            if (System.nanoTime() - started > java.util.concurrent.TimeUnit.MINUTES.toNanos(4)) throw new AssertionError("Native advanced client timed out at " + phase + " state=" + snapshot);
            if (!loading && client.getOverlay() == null && client.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
                loading = true; client.options.guiScale().set(2); client.resizeDisplay();
                client.createWorldOpenFlows().openWorld("Native Recipe Validation", () -> { throw new AssertionError("Native advanced world did not load"); }); return;
            }
            if (client.level == null || client.player == null || client.getSingleplayerServer() == null || capturing) return;
            var server = client.getSingleplayerServer();
            if (!prepared) { prepared = true; var id = client.player.getUUID(); server.execute(() -> prepare(server, id)); return; }
            if (phase == 8 && !reloaded) return;
            if (!opened && ready && client.level.getBlockEntity(position) instanceof AdvancedInfiniteDrillMachine host
                    && host.getDefinition().getId().equals(NativeAdvancedFluidRigFixtures.DRILL.get().getId())) {
                server.execute(() -> open(server)); opened = true; return;
            }
            if (!(client.screen instanceof IMuiScreen ui) || !ui.screen().getMainPanel().isValid() || ui.screen().getContext().getGraphics() == null || snapshot == null) return;
            var panel = (MachineUIPanel) ui.screen().getMainPanel();
            for (var active : ui.screen().getPanelManager().getOpenPanels()) {
                bounded(client, active); for (var child : active.getChildren()) if (child.isEnabled()) bounded(client, child);
            }
            var fast = (ToggleButton) WidgetTree.findFirstWithName(panel, "advancedFluidFast");
            var head = (ItemSlot) WidgetTree.findFirstWithName(panel, "advancedFluidHead");
            var power = (ToggleButton) panel.getRightConfiguratorPanel().getChildren().getFirst();
            bounded(client, fast); bounded(client, head); bounded(client, power);
            var manager = ui.screen().getSyncManager().getPanelSyncManager(panel.getName());
            var display = manager.findSyncHandler("advancedFluidDrillText", StringSyncValue.class);
            if (display.isAllowC2S()) throw new AssertionError("Native advanced thermal display accepts client-owned state");
            if (phase != 3 && !display.getStringValue().equals(snapshot.display)) return;
            if (phase == 0 && ++ticks >= 10) {
                if (snapshot.status != RecipeLogic.Status.SUSPEND || snapshot.fast || power.isValueSelected() || snapshot.output != 0
                        || !ItemStack.isSameItemSameComponents(head.getSlot().getItem(), originalHead)) throw new AssertionError("Native advanced initial menu disagrees with actual thermal/head/pause state");
                capture(client, "ready", () -> { click(client, ui, fast); phase = 1; ticks = 0; });
            } else if (phase == 1 && snapshot.fast && fast.isValueSelected() && ++ticks >= 10) {
                capture(client, "fast", () -> { click(client, ui, fast); phase = 2; ticks = 0; });
            } else if (phase == 2 && !snapshot.fast && !fast.isValueSelected() && ++ticks >= 5) {
                click(client, ui, head); phase = 3; ticks = 0;
            } else if (phase == 3 && snapshot.head.isEmpty() && head.getSlot().getItem().isEmpty()
                    && ItemStack.isSameItemSameComponents(client.player.containerMenu.getCarried(), originalHead) && ++ticks >= 5) {
                if (client.player.containerMenu.getCarried().getCount() != 1 || snapshot.output != 0) throw new AssertionError("Native advanced head pickup duplicated components/count or started production");
                capture(client, "head-removed", () -> { click(client, ui, head); phase = 4; ticks = 0; });
            } else if (phase == 4 && ItemStack.isSameItemSameComponents(snapshot.head, originalHead)
                    && ItemStack.isSameItemSameComponents(head.getSlot().getItem(), originalHead) && client.player.containerMenu.getCarried().isEmpty() && ++ticks >= 5) {
                if (snapshot.head.getCount() != 1 || head.getSlot().getItem().getCount() != 1) throw new AssertionError("Native advanced head replacement changed owned count");
                capture(client, "head-restored", () -> { click(client, ui, power); phase = 5; ticks = 0; });
            } else if (phase == 5 && snapshot.status == RecipeLogic.Status.WORKING && power.isValueSelected()) {
                click(client, ui, power); phase = 6; ticks = 0;
            } else if (phase == 6 && snapshot.status == RecipeLogic.Status.SUSPEND && !power.isValueSelected() && ++ticks >= 5) {
                paidCycle(); capture(client, "completed", () -> { phase = 7; ticks = 0; });
            } else if (phase == 7 && ++ticks >= 5) {
                var list = (ListWidget<?, ?>) WidgetTree.findFirstWithName(panel, "advancedFluidRows"); var scroll = list.getScrollArea().getScrollY();
                if (scroll == null) throw new AssertionError("Native advanced long production rows have no scroll viewport");
                if (list.getScrollY() < scroll.getScrollSize() - scroll.getFullVisibleSize(list.getScrollArea())) { wheel(client, ui, list); ticks = 0; return; }
                if (!checkFluidRows(client, list)) return;
                paidCycle();
                capture(client, "fluids", () -> { client.player.closeContainer(); opened = false; phase = 8; ticks = 0; server.execute(() -> reload(server)); });
            } else if (phase == 8 && reloaded && ++ticks >= 10) {
                paidCycle();
                if (!ItemStack.isSameItemSameComponents(head.getSlot().getItem(), originalHead) || snapshot.fast || fast.isValueSelected()
                        || power.isValueSelected() || !display.getStringValue().equals(snapshot.display)) throw new AssertionError("Reloaded native advanced menu lost authoritative head, mode, pause or thermal display");
                capture(client, "reloaded", () -> {
                    LogManager.getLogger().info("GTL_NATIVE_ADVANCED_FLUID_RIG_CLIENT=PASS readonly_thermal_long_s2c=true physical_fast_c2s_s2c=true physical_component_head=true one_paid_cycle=true controller_reload=true scroll=true wrapped=true bounded=true title_bounds=true");
                    client.setScreen(null); client.stop();
                });
            }
        }
        private static void paidCycle() {
            if (snapshot.status != RecipeLogic.Status.SUSPEND || snapshot.progress != 0 || snapshot.output != 6_256_250_000L
                    || startedEut != 58_685_050_000L || snapshot.energy != initialEnergy - 20L * startedEut)
                throw new AssertionError("Native advanced physical power cycle lost exact long outputs, pause or full energy cost: " + snapshot + ", EUt=" + startedEut);
        }
        private static boolean checkFluidRows(net.minecraft.client.Minecraft client, ListWidget<?, ?> list) {
            var rows = new ArrayList<TextWidget<?>>(); collectText(list, rows);
            var fluids = rows.stream().filter(row -> row.getKey().getString().contains("mB/s")).toList();
            if (fluids.size() != 2) throw new AssertionError("Native advanced actual menu lost a fluid row");
            if (fluids.stream().anyMatch(row -> !row.isValid() || row.getArea().width == 0 || row.getArea().height == 0)) return false;
            for (var row : fluids) {
                var area = row.getArea(); var viewport = list.getArea();
                if (area.x < viewport.x || area.x + area.width > viewport.x + viewport.width || area.height <= client.font.lineHeight
                        || area.y - list.getScrollY() < viewport.y || area.y + area.height - list.getScrollY() > viewport.y + viewport.height)
                    throw new AssertionError("Native advanced production row is clipped or not wrapped in its real viewport: " + area + ", viewport=" + viewport);
                var serialized = com.google.gson.JsonParser.parseString(Component.Serializer.toJson(row.getKey(), client.level.registryAccess()));
                if (com.google.gson.JsonParser.parseString(snapshot.display).getAsJsonArray().asList().stream().noneMatch(serialized::equals))
                    throw new AssertionError("Native advanced rendered production row disagrees with the authoritative fluid identity");
            }
            return true;
        }
        private static void collectText(IWidget widget, List<TextWidget<?>> rows) {
            if (widget instanceof TextWidget<?> text) rows.add(text); for (var child : widget.getChildren()) collectText(child, rows);
        }
        private static void bounded(net.minecraft.client.Minecraft client, IWidget widget) {
            var area = widget.getArea(); if (area.x < 0 || area.y < 0 || area.x + area.width > client.screen.width || area.y + area.height > client.screen.height)
                throw new AssertionError("Native advanced menu exceeds GUI scale 2 screen: " + widget.getClass().getSimpleName() + "/" + widget.getName() + " " + area);
        }
        private static void capture(net.minecraft.client.Minecraft client, String stage, Runnable next) {
            capturing = true; net.minecraft.client.Screenshot.grab(client.gameDirectory, "native-advanced-fluid-rig-" + stage + ".png", client.getMainRenderTarget(),
                    message -> client.execute(() -> { capturing = false; next.run(); }));
        }
        private static double[] move(net.minecraft.client.Minecraft client, IMuiScreen ui, IWidget widget) {
            var area = widget.getArea(); double x = area.x + area.width / 2.0, y = area.y + area.height / 2.0;
            try { var callback = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onMove", long.class, double.class, double.class); callback.setAccessible(true);
                callback.invoke(client.mouseHandler, client.getWindow().getWindow(), x * client.getWindow().getScreenWidth() / client.screen.width, y * client.getWindow().getScreenHeight() / client.screen.height);
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            ui.screen().getContext().updateState((int) x, (int) y, 0); ui.screen().onFrameUpdate(); return new double[]{x, y};
        }
        private static void click(net.minecraft.client.Minecraft client, IMuiScreen ui, IWidget widget) {
            var point = move(client, ui, widget); double x = point[0], y = point[1];
            if (ui.screen().getContext().getTopHovered() != widget) throw new AssertionError("Native advanced actual mouse click did not hover " + widget.getName());
            client.screen.afterMouseAction(); boolean handled = net.neoforged.neoforge.client.ClientHooks.onScreenMouseClickedPre(client.screen, x, y, 0);
            if (!handled) handled = net.neoforged.neoforge.client.ClientHooks.onScreenMouseClickedPost(client.screen, x, y, 0, client.screen.mouseClicked(x, y, 0));
            if (!handled) throw new AssertionError("Native advanced screen rejected its physical click");
            if (!net.neoforged.neoforge.client.ClientHooks.onScreenMouseReleasedPre(client.screen, x, y, 0))
                net.neoforged.neoforge.client.ClientHooks.onScreenMouseReleasedPost(client.screen, x, y, 0, client.screen.mouseReleased(x, y, 0));
        }
        private static void wheel(net.minecraft.client.Minecraft client, IMuiScreen ui, IWidget viewport) {
            var point = move(client, ui, viewport); double x = point[0], y = point[1]; client.screen.afterMouseAction();
            boolean handled = net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.ScreenEvent.MouseScrolled.Pre(client.screen, x, y, 0, -1)).isCanceled();
            if (!handled) { handled = client.screen.mouseScrolled(x, y, 0, -1); net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.ScreenEvent.MouseScrolled.Post(client.screen, x, y, 0, -1)); }
            if (!handled) throw new AssertionError("Native advanced list rejected actual wheel input");
        }
    }
}
