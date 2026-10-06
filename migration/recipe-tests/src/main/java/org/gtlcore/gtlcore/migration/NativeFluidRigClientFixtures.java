package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMENativeOutputBuffer;
import org.gtlcore.gtlcore.common.data.GTLBedrockFluids;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.INFFluidDrillMachine;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.BedrockFluidVeinSavedData;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.FluidVeinWorldEntry;
import com.gregtechceu.gtceu.api.machine.mui.MachineUIPanel;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.multiblock.util.AbstractStructureHelper;
import com.gregtechceu.gtceu.api.multiblock.util.BlockInfo;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import appeng.api.stacks.AEFluidKey;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.value.sync.StringSyncValue;
import brachy.modularui.widgets.ToggleButton;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.apache.logging.log4j.LogManager;
import java.util.*;

/** Real native Core menus, physical power clicks, readonly server display and actual entity reload. */
@EventBusSubscriber(modid = "gtlcore_recipe_test")
public final class NativeFluidRigClientFixtures {
    private static final List<com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry<com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition>> DEFINITIONS =
            List.of(NativeFluidRigFixtures.INFINITE, NativeFluidRigFixtures.OVERCLOCKED, NativeFluidRigFixtures.LONG_INFINITE);
    private static final List<String> NAMES = List.of("normal", "overclocked", "long");
    private static final long[] AMOUNTS = {25_856L, 38_784L, 2_560_000_256L};
    private static final List<BlockPos> blocks = new ArrayList<>();
    private static volatile BlockPos position;
    private static volatile boolean ready, reloaded;
    private static volatile Snapshot snapshot;
    private static UUID owner;
    private static int variant;
    private static long initialEnergy;
    private record Snapshot(RecipeLogic.Status status, int progress, long amount, long energy) {}

    private static void prepare(net.minecraft.server.MinecraftServer server, UUID id, int next) {
        owner = id; variant = next; ready = reloaded = false; snapshot = null;
        var player = Objects.requireNonNull(server.getPlayerList().getPlayer(id)); var level = player.serverLevel();
        if (position != null && level.getBlockEntity(position) instanceof INFFluidDrillMachine old) old.invalidateStructure("main");
        for (var old : blocks) level.setBlockAndUpdate(old, Blocks.AIR.defaultBlockState()); blocks.clear();
        var definition = DEFINITIONS.get(next); var shape = new LinkedHashMap<BlockPos, BlockInfo>();
        AbstractStructureHelper.blockPattern(new Int2IntOpenHashMap(Map.of(0, 1))).populate(shape,
                definition.get().getStructurePatterns().get("main").get(), null, Direction.NORTH, Direction.UP, false);
        var anchor = player.blockPosition().offset(5, 0, 3);
        for (var block : shape.entrySet()) {
            var pos = anchor.offset(block.getKey()); level.setBlockAndUpdate(pos, block.getValue().getBlockState()); blocks.add(pos);
            if (block.getValue().getBlockState().is(definition.getBlock())) position = pos;
        }
        var machine = (INFFluidDrillMachine) level.getBlockEntity(position); machine.checkAndFormStructure();
        if (!machine.isFormed()) throw new AssertionError("Native client fluid rig did not form");
        machine.getRecipeLogic().setWorkingEnabled(false);
        var holder = level.registryAccess().registryOrThrow(GTRegistries.Keys.BEDROCK_FLUID).getHolderOrThrow(GTLBedrockFluids.VOID_ALT_WATER);
        BedrockFluidVeinSavedData.getOrCreate(level).veinFluids.put(new ChunkPos(SectionPos.blockToSectionCoord(position.getX()), SectionPos.blockToSectionCoord(position.getZ())),
                new FluidVeinWorldEntry(holder, next == 2 ? 10_000_001 : 101, 100_000));
        machine.getEnergyContainer().changeEnergy(1_000_000); initialEnergy = machine.getEnergyContainer().getEnergyStored();
        ready = true;
    }

    private static long output(INFFluidDrillMachine machine) {
        if (variant == 2) {
            var part = machine.getParts().stream().filter(IMENativeOutputBuffer.class::isInstance).map(IMENativeOutputBuffer.class::cast).findFirst().orElseThrow();
            return part.gtlcore$getOutputBuffer().storage.getLong(AEFluidKey.of(machine.getRecipeLogic().getVeinFluid()));
        }
        return machine.getParts().stream().filter(FluidHatchPartMachine.class::isInstance).map(FluidHatchPartMachine.class::cast)
                .findFirst().orElseThrow().tank.getFluidInTank(0).getAmount();
    }

    private static void reload(net.minecraft.server.MinecraftServer server) {
        var player = Objects.requireNonNull(server.getPlayerList().getPlayer(owner)); player.closeContainer();
        var level = player.serverLevel(); var machine = (INFFluidDrillMachine) level.getBlockEntity(position);
        var saved = machine.saveWithoutMetadata(level.registryAccess()); var state = machine.getBlockState();
        machine.invalidateStructure("main"); level.removeBlockEntity(position);
        var copy = (INFFluidDrillMachine) DEFINITIONS.get(variant).getBlockEntityType().create(position, state);
        copy.loadAdditional(saved, level.registryAccess()); level.setBlockEntity(copy); copy.checkAndFormStructure();
        if (!copy.isFormed() || !copy.getRecipeLogic().isSuspend() || output(copy) != AMOUNTS[variant])
            throw new AssertionError("Native client rig reload lost its paused controller or actual completed output");
        snapshot = new Snapshot(copy.getRecipeLogic().getStatus(), copy.getRecipeLogic().getProgress(), output(copy), copy.getEnergyContainer().getEnergyStored());
        reloaded = true;
    }

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("gtlcore.fluidRigValidation.enabled") || !ready || owner == null) return;
        var player = event.getServer().getPlayerList().getPlayer(owner);
        if (player != null && player.serverLevel().getBlockEntity(position) instanceof INFFluidDrillMachine machine)
            snapshot = new Snapshot(machine.getRecipeLogic().getStatus(), machine.getRecipeLogic().getProgress(), output(machine), machine.getEnergyContainer().getEnergyStored());
    }

    @EventBusSubscriber(modid = "gtlcore_recipe_test", value = net.neoforged.api.distmarker.Dist.CLIENT)
    public static final class Client {
        private static boolean loading, prepared, opened, capturing;
        private static int completed, phase, ticks;
        private static long started;
        @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
            if (!Boolean.getBoolean("gtlcore.fluidRigValidation.enabled")) return;
            var client = net.minecraft.client.Minecraft.getInstance(); if (started == 0) started = System.nanoTime();
            if (System.nanoTime() - started > java.util.concurrent.TimeUnit.MINUTES.toNanos(4)) throw new AssertionError("Native fluid rig client timed out at " + completed + "/" + phase + " state=" + snapshot);
            if (!loading && client.getOverlay() == null && client.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
                loading = true; client.options.guiScale().set(2); client.resizeDisplay();
                client.createWorldOpenFlows().openWorld("Native Recipe Validation", () -> { throw new AssertionError("Native fluid rig world did not load"); }); return;
            }
            if (client.level == null || client.player == null || client.getSingleplayerServer() == null || capturing) return;
            var server = client.getSingleplayerServer();
            if (!prepared) { prepared = true; var id = client.player.getUUID(); int next = completed; server.execute(() -> prepare(server, id, next)); return; }
            if (phase == 3 && !reloaded) return;
            if (!opened && ready && client.level.getBlockEntity(position) instanceof INFFluidDrillMachine host && host.getDefinition().getId().equals(DEFINITIONS.get(completed).get().getId())) {
                server.execute(() -> { var player = server.getPlayerList().getPlayer(owner);
                    ((INFFluidDrillMachine) player.serverLevel().getBlockEntity(position)).tryToOpenUI(player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(position.getCenter(), Direction.NORTH, position, false)); }); opened = true; return;
            }
            if (!(client.screen instanceof brachy.modularui.api.IMuiScreen ui) || !ui.screen().getMainPanel().isValid() || ui.screen().getContext().getGraphics() == null || snapshot == null) return;
            var panel = (MachineUIPanel) ui.screen().getMainPanel(); bounded(client, panel);
            for (var child : panel.getChildren()) if (child.isEnabled()) bounded(client, child);
            var power = (ToggleButton) panel.getRightConfiguratorPanel().getChildren().getFirst(); bounded(client, power);
            var manager = ui.screen().getSyncManager().getPanelSyncManager(panel.getName());
            var display = manager.findSyncHandler("infiniteFluidDrillText", StringSyncValue.class);
            if (display.isAllowC2S()) throw new AssertionError("Native fluid production display accepts client-authored state");
            if (!display.getStringValue().contains(FormattingUtil.formatNumbers(AMOUNTS[completed]))) return;
            if (phase == 0 && ++ticks >= 20) {
                if (snapshot.status != RecipeLogic.Status.SUSPEND || power.isValueSelected() || snapshot.amount != 0) throw new AssertionError("Native fluid rig lost initial server/GUI pause state");
                capture(client, "ready", () -> { click(client, ui, power); phase = 1; ticks = 0; });
            } else if (phase == 1 && snapshot.status == RecipeLogic.Status.WORKING && power.isValueSelected()) {
                click(client, ui, power); phase = 2; ticks = 0;
            } else if (phase == 2 && snapshot.status == RecipeLogic.Status.SUSPEND && !power.isValueSelected() && ++ticks >= 10) {
                long cost = 20L * GTValues.VA[completed == 1 ? GTValues.HV : GTValues.MV];
                if (snapshot.progress != 0 || snapshot.amount != AMOUNTS[completed] || snapshot.energy != initialEnergy - cost)
                    throw new AssertionError("Native physical power clicks did not complete exactly one fully paid cycle: " + snapshot);
                capture(client, "completed", () -> { client.player.closeContainer(); opened = false; phase = 3; ticks = 0; server.execute(() -> reload(server)); });
            } else if (phase == 3 && reloaded && ++ticks >= 20) {
                if (snapshot.status != RecipeLogic.Status.SUSPEND || power.isValueSelected() || snapshot.amount != AMOUNTS[completed]) throw new AssertionError("Reloaded native machine menu disagrees with its actual server state");
                capture(client, "reloaded", () -> {
                    if (++completed == 3) { LogManager.getLogger().info("GTL_NATIVE_FLUID_RIG_CLIENT=PASS menus=3 long_display_s2c=true readonly=true physical_power_c2s_s2c=true one_paid_cycle=true controller_reload=true bounded=true title_bounds=true"); client.setScreen(null); client.stop(); return; }
                    client.player.closeContainer(); ready = reloaded = false; prepared = opened = false; phase = ticks = 0;
                });
            }
        }

        private static void bounded(net.minecraft.client.Minecraft client, IWidget widget) {
            var area = widget.getArea();
            if (area.x < 0 || area.y < 0 || area.x + area.width > client.screen.width || area.y + area.height > client.screen.height)
                throw new AssertionError("Native fluid rig menu/control exceeds the screen: " + area);
        }
        private static void capture(net.minecraft.client.Minecraft client, String name, Runnable next) {
            capturing = true;
            net.minecraft.client.Screenshot.grab(client.gameDirectory, "native-fluid-rig-" + NAMES.get(completed) + "-" + name + ".png", client.getMainRenderTarget(), message -> client.execute(() -> { capturing = false; next.run(); }));
        }
        private static void click(net.minecraft.client.Minecraft client, brachy.modularui.api.IMuiScreen ui, IWidget widget) {
            var area = widget.getArea(); double x = area.x + area.width / 2.0, y = area.y + area.height / 2.0;
            try { var move = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onMove", long.class, double.class, double.class); move.setAccessible(true);
                move.invoke(client.mouseHandler, client.getWindow().getWindow(), x * client.getWindow().getScreenWidth() / client.screen.width, y * client.getWindow().getScreenHeight() / client.screen.height);
            } catch (ReflectiveOperationException error) { throw new AssertionError("Native fluid rig mouse callback failed", error); }
            ui.screen().getContext().updateState((int) x, (int) y, 0); ui.screen().onFrameUpdate();
            if (ui.screen().getContext().getTopHovered() != widget) throw new AssertionError("Native power click did not hover its actual control");
            client.screen.afterMouseAction(); boolean handled = net.neoforged.neoforge.client.ClientHooks.onScreenMouseClickedPre(client.screen, x, y, 0);
            if (!handled) handled = net.neoforged.neoforge.client.ClientHooks.onScreenMouseClickedPost(client.screen, x, y, 0, client.screen.mouseClicked(x, y, 0));
            if (!handled) throw new AssertionError("Native fluid rig screen rejected its physical power click");
            if (!net.neoforged.neoforge.client.ClientHooks.onScreenMouseReleasedPre(client.screen, x, y, 0))
                net.neoforged.neoforge.client.ClientHooks.onScreenMouseReleasedPost(client.screen, x, y, 0, client.screen.mouseReleased(x, y, 0));
        }
    }
}
