package org.gtlcore.gtlcore.mixin.gtm.api.machine;

import org.gtlcore.gtlcore.api.machine.IPerformanceDisplayMachine;
import org.gtlcore.gtlcore.api.machine.ISuspendableMachine;
import org.gtlcore.gtlcore.api.machine.MachineStartupTickBudget;
import org.gtlcore.gtlcore.api.machine.PerformanceMonitorMachine;
import org.gtlcore.gtlcore.api.machine.trait.IBatchMachine;
import org.gtlcore.gtlcore.integration.ae2.AEGridMachineSettings;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.item.tool.GTToolType;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.integration.ae2.machine.feature.IGridConnectedMachine;
import com.gregtechceu.gtceu.utils.ExtendedUseOnContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import com.mojang.datafixers.util.Pair;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;
import java.util.Set;

/** Native tick/interaction adaptation of the original Core/GTO machine extensions. */
@Mixin(value = MetaMachine.class, remap = false)
public abstract class MetaMachineMixin implements IPerformanceDisplayMachine, ISuspendableMachine {
    @Unique private int gtlcore$averageTickTime;
    @Unique private long gtlcore$totalTickNanos;
    @Unique private int gtlcore$observedTicks;
    @Unique private boolean gtlcore$observe;
    @Unique @SaveField(nbtKey = "gtlcore_finish_pause") private boolean gtlcore$suspendAfterFinish;
    @Unique private boolean gtlcore$startupTickAdmitted;
    @Shadow @Final private List<TickableSubscription> serverTicks;
    @Shadow @Final private List<TickableSubscription> waitingToAdd;
    @Shadow private void executeTick() { throw new AssertionError(); }

    @Unique private MetaMachine gtlcore$self() { return (MetaMachine) (Object) this; }
    @Override public int gtlcore$getTickTime() { return gtlcore$averageTickTime; }
    @Override public void gtlcore$observe() { gtlcore$observe = true; }
    @Override public void runTick() { executeTick(); }

    // Wrap trait execution only: the native superclass still saves and publishes dirty fields every server tick.
    // Native chunk ticking supplies the cadence; the removed SERVER_TICK block property/wall-clock loop is not reused.
    @WrapOperation(method = "serverTick", at = @At(value = "INVOKE",
            target = "Lcom/gregtechceu/gtceu/api/machine/MetaMachine;executeTick()V"), require = 1)
    private void gtlcore$runBudgetedObservedTick(MetaMachine machine, Operation<Void> original) {
        if (cancelTick()) return;
        boolean startup = !gtlcore$startupTickAdmitted;
        var level = machine.getLevel() instanceof ServerLevel server ? server : null;
        if (startup && level != null && !MachineStartupTickBudget.tryAcquire(level, machine)) return;
        gtlcore$startupTickAdmitted = true;
        boolean observing = PerformanceMonitorMachine.observe || gtlcore$observe;
        long started = observing || startup ? System.nanoTime() : 0;
        original.call(machine);
        long elapsed = started == 0 ? 0 : System.nanoTime() - started;
        if (startup && level != null) MachineStartupTickBudget.recordExecution(level, elapsed);
        if (observing) {
            gtlcore$totalTickNanos += elapsed;
            gtlcore$observedTicks++;
            if (machine.getOffsetTimer() % 40 == 0) {
                gtlcore$averageTickTime = (int) Math.clamp(gtlcore$totalTickNanos / (1000L * gtlcore$observedTicks), 0L, Integer.MAX_VALUE);
                gtlcore$totalTickNanos = 0; gtlcore$observedTicks = 0; gtlcore$observe = false;
            }
            if (PerformanceMonitorMachine.observe) PerformanceMonitorMachine.PERFORMANCE_MAP.put(machine, gtlcore$averageTickTime);
        } else if (!keepTick() && serverTicks.isEmpty() && waitingToAdd.isEmpty()) {
            gtlcore$averageTickTime = 0; gtlcore$totalTickNanos = 0; gtlcore$observedTicks = 0;
        }
    }

    @Inject(method = "onToolClick", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtlcore$toggleBatchWithWireCutter(ExtendedUseOnContext context,
                                                CallbackInfoReturnable<Pair<GTToolType, InteractionResult>> cir) {
        var player = context.getPlayer(); var machine = gtlcore$self();
        if (!Set.of(GTToolType.WIRE_CUTTER).equals(context.getToolType()) || player == null || !player.isShiftKeyDown()
                || !(machine instanceof IBatchMachine batch) || !batch.canConfigureBatchProcessing()) return;
        var result = InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.swing(context.getHand());
            if (!batch.supportsBatchProcessing()) {
                serverPlayer.sendSystemMessage(Component.translatable("gui.gtlcore.batch_processing.unsupported_mode"), true);
            } else {
                boolean enabled = !batch.isBatchEnabled(); batch.setBatchEnabled(enabled);
                serverPlayer.sendSystemMessage(Component.translatable(enabled ? "gui.gtlcore.batch_processing.enabled" : "gui.gtlcore.batch_processing.disabled"), true);
            }
            result = InteractionResult.CONSUME;
        }
        cir.setReturnValue(Pair.of(GTToolType.WIRE_CUTTER, result));
    }

    @Inject(method = "onToolClick", at = @At("RETURN"), cancellable = true, require = 1)
    private void gtlcore$configureAeFacing(ExtendedUseOnContext context,
                                         CallbackInfoReturnable<Pair<GTToolType, InteractionResult>> cir) {
        if (cir.getReturnValue().getSecond() != InteractionResult.PASS || !context.getToolType().contains(GTToolType.WIRE_CUTTER)) return;
        if (context.getPlayer() instanceof ServerPlayer player && gtlcore$self() instanceof IGridConnectedMachine grid) {
            player.swing(context.getHand()); var host = grid.self();
            boolean all = !host.getPersistentData().getBoolean("isAllFacing");
            host.getPersistentData().putBoolean("isAllFacing", all);
            AEGridMachineSettings.applyExposedSides(grid, host.getFrontFacing()); host.markAsChanged();
            player.sendSystemMessage(Component.translatable(all ? "gtlcore.me_any" : "gtlcore.me_front"), true);
            cir.setReturnValue(Pair.of(GTToolType.WIRE_CUTTER, InteractionResult.CONSUME));
        }
    }

    @Inject(method = "shouldRenderGrid", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtlcore$renderAeWireCutterGrid(Player player, BlockPos pos, BlockState state, ItemStack held,
                                              Set<GTToolType> tools, CallbackInfoReturnable<Boolean> cir) {
        if (tools.contains(GTToolType.WIRE_CUTTER) && gtlcore$self() instanceof IGridConnectedMachine) cir.setReturnValue(true);
    }

    @Inject(method = "onSoftMalletClick", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtlcore$pauseAfterCurrentCycle(ExtendedUseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        var machine = gtlcore$self(); var player = context.getPlayer();
        if (machine.isRemote() || player == null) return;
        if (!player.isShiftKeyDown()) { gtlcore$setSuspendAfterFinish(false); return; }
        var controllable = GTCapabilityHelper.getControllable(machine.getLevel(), machine.getBlockPos(), context.getGridSide());
        if (controllable == null) return;
        if (!controllable.isWorkingEnabled() || gtlcore$isSuspendAfterFinish()) { cir.setReturnValue(InteractionResult.FAIL); return; }
        gtlcore$setSuspendAfterFinish(true);
        player.sendSystemMessage(Component.translatable("gtlcore.behaviour.soft_hammer.idle_after_cycle")); player.swing(context.getHand());
        cir.setReturnValue(InteractionResult.CONSUME);
    }

    @Override public boolean gtlcore$isSuspendAfterFinish() { return gtlcore$suspendAfterFinish; }
    @Override public void gtlcore$setSuspendAfterFinish(boolean value) {
        gtlcore$suspendAfterFinish = value;
        if (!gtlcore$self().isRemote()) gtlcore$self().markAsChanged();
    }
}
