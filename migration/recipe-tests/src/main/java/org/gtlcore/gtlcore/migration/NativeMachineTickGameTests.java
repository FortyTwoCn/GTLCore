package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.machine.*;
import org.gtlcore.gtlcore.api.machine.trait.IBatchMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.StorageMachine;
import org.gtlcore.gtlcore.config.ConfigHolder;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.item.tool.GTToolType;
import com.gregtechceu.gtceu.api.item.tool.ToolHelper;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.utils.ExtendedUseOnContext;
import com.lowdragmc.lowdraglib.async.AsyncThreadData;
import com.lowdragmc.lowdraglib.async.IAsyncLogic;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.gametest.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeMachineTickGameTests {
    @GameTest(template = "empty", batch = "gtl_native_ticks", timeoutTicks = 100)
    public static void cancellingTraitsStillFlushesNativeDirtyFieldsAndSavesNewWorldState(GameTestHelper helper) {
        var pos = new BlockPos(3, 3, 3); helper.setBlock(pos, NativePerformanceFixtures.PROBE.getBlock());
        var machine = (NativePerformanceFixtures.TickProbe) helper.getBlockEntity(pos); machine.cancelled = true;
        machine.savedValue = 987654; machine.markAsChanged();
        helper.assertTrue(machine.isDirty(), "Native dirty field was not set"); machine.serverTick();
        helper.assertTrue(!machine.isDirty() && machine.calls == 0, "Cancelled traits suppressed the native superclass or executed work");
        var copy = (NativePerformanceFixtures.TickProbe) NativePerformanceFixtures.PROBE.getBlockEntityType().create(machine.getBlockPos(), machine.getBlockState());
        copy.handleUpdateTag(machine.getUpdateTag(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertTrue(copy.savedValue == 987654, "Cancelled native machine lost S2C field data");
        copy.savedValue = 0; copy.loadAdditional(machine.saveWithoutMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertTrue(copy.savedValue == 987654, "Cancelled native machine lost new-world SaveField data");
        machine.cancelled = false; helper.succeedWhen(() -> helper.assertTrue(machine.calls > 0, "Uncancelled native block did not resume subscribed ticks"));
    }

    @GameTest(template = "empty", batch = "gtl_native_tick_budget", timeoutTicks = 100)
    public static void nativeStartupBudgetsSeparateAeAndNormalAndCriticalWorkBypassesTimeLimit(GameTestHelper helper) {
        var level = helper.getLevel().getServer().getLevel(Level.END);
        var pos = new BlockPos(32, 100, 32);
        level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
        level.setChunkForced(pos.west().getX() >> 4, pos.getZ() >> 4, true);
        // The harness reuses its world: replace any entities saved by an interrupted previous test run.
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos.east(), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos.west(), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos, NativePerformanceFixtures.PROBE.getBlock().defaultBlockState());
        level.setBlockAndUpdate(pos.east(), NativeAePatternFixtures.BUFFER.getBlock().defaultBlockState());
        level.setBlockAndUpdate(pos.west(), NativePerformanceFixtures.CRITICAL.getBlock().defaultBlockState());
        var normal = (NativePerformanceFixtures.TickProbe) level.getBlockEntity(pos);
        var ae = (com.gregtechceu.gtceu.api.machine.MetaMachine) level.getBlockEntity(pos.east());
        var critical = (NativePerformanceFixtures.CriticalProbe) level.getBlockEntity(pos.west());
        var config = ConfigHolder.INSTANCE;
        for (int i = 0; i < config.machineStartupTickBudgetPerLevel; i++)
            helper.assertTrue(MachineStartupTickBudget.tryAcquire(level, normal), "Normal startup budget prematurely exhausted");
        helper.assertTrue(!MachineStartupTickBudget.tryAcquire(level, normal), "Normal startup exceeded its per-level count");
        for (int i = 0; i < config.machineStartupAeTickBudgetPerLevel; i++)
            helper.assertTrue(MachineStartupTickBudget.tryAcquire(level, ae), "Normal starts consumed the separate AE budget");
        helper.assertTrue(!MachineStartupTickBudget.tryAcquire(level, ae), "AE startup exceeded its per-level count");
        MachineStartupTickBudget.recordExecution(level, config.machineStartupTickTimeBudgetMillis * 1_000_000L);
        helper.assertTrue(!MachineStartupTickBudget.tryAcquire(level, normal) && !MachineStartupTickBudget.tryAcquire(level, ae)
                && MachineStartupTickBudget.tryAcquire(level, critical), "Time budget failed to defer normal/AE work or blocked critical work");
        normal.savedValue = 77; normal.markAsChanged(); normal.serverTick(); critical.serverTick();
        helper.assertTrue(normal.calls == 0 && !normal.isDirty() && critical.calls == 1,
                "Invalid deferred native tick: normal=" + normal.calls + " dirty=" + normal.isDirty() + " critical=" + critical.calls);
        long tick = level.getGameTime();
        helper.succeedWhen(() -> {
            helper.assertTrue(level.getGameTime() > tick && normal.calls > 0, "Deferred startup did not resume: time=" + level.getGameTime()
                    + "/" + tick + " calls=" + normal.calls + " removed=" + normal.isRemoved()
                    + " blockTicking=" + level.shouldTickBlocksAt(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4)));
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()); level.setBlockAndUpdate(pos.east(), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(pos.west(), Blocks.AIR.defaultBlockState());
            level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, false);
            level.setChunkForced(pos.west().getX() >> 4, pos.getZ() >> 4, false);
        });
    }

    @GameTest(template = "empty", batch = "gtl_native_ticks", timeoutTicks = 100)
    public static void realWireCutterTogglesBatchAndSoftMalletPausesAfterOneActualRecipeCycle(GameTestHelper helper) {
        var pos = new BlockPos(3, 3, 3); helper.setBlock(pos, NativeMachineFixtures.BATCH.getBlock());
        var machine = (StorageMachine) helper.getBlockEntity(pos); machine.setFrontFacing(Direction.NORTH); machine.checkAndFormStructure();
        helper.assertTrue(machine.isFormed(), "Tool recipe controller failed native formation");
        var player = FakePlayerFactory.getMinecraft(helper.getLevel()); player.setPos(machine.getBlockPos().getCenter()); player.setShiftKeyDown(true);
        var hit = new BlockHitResult(machine.getBlockPos().getCenter(), Direction.NORTH, machine.getBlockPos(), false);
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, ToolHelper.get(GTToolType.WIRE_CUTTER, GTMaterials.Steel));
            helper.assertTrue(!player.getMainHandItem().isEmpty(), "Registered native steel wire cutter was missing");
            var batch = (IBatchMachine) machine; boolean before = batch.isBatchEnabled();
            var result = machine.onToolClick(new ExtendedUseOnContext(player, InteractionHand.MAIN_HAND, hit));
            helper.assertTrue(result.getSecond() == InteractionResult.CONSUME && batch.isBatchEnabled() != before,
                    "Actual ExtendedUseOnContext did not toggle batch processing"); batch.setBatchEnabled(false);
            var input = machine.getTraits(NotifiableItemStackHandler.class).stream().filter(t -> t.getHandlerIO() == IO.IN).findFirst().orElseThrow();
            input.storage.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
            var builder = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_soft_mallet_cycle").inputItems(Items.IRON_INGOT)
                    .outputFluids(SizedFluidIngredient.of(Fluids.WATER, 100)).duration(4); builder.tickInput.clear();
            var logic = machine.getRecipeLogic(); helper.assertTrue(logic.checkMatchedRecipeAvailable(builder.build()), "Native tool cycle did not start");
            player.setItemInHand(InteractionHand.MAIN_HAND, ToolHelper.get(GTToolType.SOFT_MALLET, GTMaterials.Wood));
            helper.assertTrue(!player.getMainHandItem().isEmpty(), "Registered native wood soft mallet was missing");
            result = machine.onToolClick(new ExtendedUseOnContext(player, InteractionHand.MAIN_HAND, hit));
            var suspend = (ISuspendableMachine) (Object) machine;
            helper.assertTrue(result.getSecond() == InteractionResult.CONSUME && suspend.gtlcore$isSuspendAfterFinish()
                    && machine.isWorkingEnabled(), "Shift mallet disabled work immediately instead of arming cycle pause");
            helper.assertTrue(machine.onToolClick(new ExtendedUseOnContext(player, InteractionHand.MAIN_HAND, hit)).getSecond() == InteractionResult.FAIL,
                    "Repeated shift mallet click should reject an already armed pause");
            for (int i = 0; i < 4; i++) logic.serverTick();
            helper.assertTrue(machine.getTraits(NotifiableFluidTank.class).getFirst().getStorages()[0].getFluidAmount() == 100
                    && logic.getStatus() == RecipeLogic.Status.SUSPEND && !suspend.gtlcore$isSuspendAfterFinish()
                    && input.getStackInSlot(0).getCount() == 1, "Shift mallet did not complete and pause exactly one recipe cycle");
            player.setShiftKeyDown(false); machine.onToolClick(new ExtendedUseOnContext(player, InteractionHand.MAIN_HAND, hit));
            helper.assertTrue(machine.isWorkingEnabled() && !suspend.gtlcore$isSuspendAfterFinish(), "Normal mallet failed native resume");
        } finally { player.setShiftKeyDown(false); player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_ticks", timeoutTicks = 100)
    public static void ldlibAsyncDirtyChangesAreQueuedAndDiscardedForReplacedNativeHosts(GameTestHelper helper) {
        var pos = new BlockPos(3, 3, 3); helper.setBlock(pos, NativePerformanceFixtures.PROBE.getBlock()); helper.setBlock(pos.east(), NativePerformanceFixtures.PROBE.getBlock());
        var live = (NativePerformanceFixtures.TickProbe) helper.getBlockEntity(pos); var removed = (NativePerformanceFixtures.TickProbe) helper.getBlockEntity(pos.east());
        live.cancelled = removed.cancelled = true; live.setDirty(false); removed.setDirty(false);
        var async = AsyncThreadData.getOrCreate(helper.getLevel()); var once = new AtomicBoolean(); var completed = new AtomicBoolean();
        var signalled = new CountDownLatch(1); var queued = new AtomicBoolean();
        IAsyncLogic logic = period -> {
            if (!once.compareAndSet(false, true)) return;
            live.markAsChanged(); removed.markAsChanged();
            queued.set(AsyncThreadData.isThreadService() && !live.isDirty() && !removed.isDirty());
            helper.getLevel().getServer().execute(() -> completed.set(live.isDirty() && !removed.isDirty())); signalled.countDown();
        };
        async.addAsyncLogic(logic);
        try {
            helper.assertTrue(signalled.await(2, TimeUnit.SECONDS) && queued.get(), "LDLib async service mutated native dirty fields off the server thread");
            helper.setBlock(pos.east(), Blocks.AIR);
        } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
        finally { async.removeAsyncLogic(logic); }
        helper.succeedWhen(() -> helper.assertTrue(completed.get(), "Queued native host change was lost or applied to a removed block entity"));
    }

    @GameTest(template = "empty", batch = "gtl_native_performance", timeoutTicks = 120)
    public static void monitorRetainsEqualTimingsAndPublishesActualNativeTickObservations(GameTestHelper helper) {
        var pos = new BlockPos(3, 3, 3); helper.setBlock(pos, NativePerformanceFixtures.MONITOR.getBlock());
        helper.setBlock(pos.east(), NativePerformanceFixtures.PROBE.getBlock()); helper.setBlock(pos.west(), NativePerformanceFixtures.PROBE.getBlock());
        var monitor = (PerformanceMonitorMachine) helper.getBlockEntity(pos);
        var first = (NativePerformanceFixtures.TickProbe) helper.getBlockEntity(pos.east()); var second = (NativePerformanceFixtures.TickProbe) helper.getBlockEntity(pos.west());
        PerformanceMonitorMachine.PERFORMANCE_MAP.clear(); PerformanceMonitorMachine.PERFORMANCE_MAP.put(first, 98765); PerformanceMonitorMachine.PERFORMANCE_MAP.put(second, 98765);
        var rows = monitor.getDisplayRows();
        helper.assertTrue(rows.size() == 2 && rows.getFirst().micros() == 98765 && rows.getLast().micros() == 98765
                && !rows.getFirst().position().equals(rows.getLast().position()) && monitor.getDisplayRows() == rows,
                "Native monitor omitted equal-time machines or refreshed its cached snapshot too early");
        helper.runAfterDelay(45, () -> {
            var updated = monitor.getDisplayRows();
            helper.assertTrue(first.calls > 0 && second.calls > 0 && updated != rows
                    && updated.stream().anyMatch(row -> row.position().equals(first.getBlockPos()))
                    && updated.stream().anyMatch(row -> row.position().equals(second.getBlockPos()))
                    && (Object) first instanceof IPerformanceDisplayMachine, "Actual native ticks did not refresh server-owned monitor rows");
            PerformanceMonitorMachine.observe = false; PerformanceMonitorMachine.PERFORMANCE_MAP.clear(); helper.succeed();
        });
    }
}
