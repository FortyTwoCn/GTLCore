package org.gtlcore.gtlcore.common.machine.multiblock.part.ae;

import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEFilterIOTrait;
import org.gtlcore.gtlcore.integration.ae2.AEBufferedOutput;
import org.gtlcore.gtlcore.integration.ae2.AEOutputIngredients;
import org.gtlcore.gtlcore.integration.ae2.async.AEAccumulator;
import org.gtlcore.gtlcore.integration.ae2.async.AEWriteService;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import brachy.modularui.screen.UISettings;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.*;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.StringSyncValue;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.layout.Flow;
import brachy.modularui.widgets.textfield.TextFieldWidget;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.jetbrains.annotations.NotNull;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public class MEExtendedAsyncOutputPartMachine extends MEExtendedOutputPartMachineBase {
    private final AEAccumulator accumulator = new AEAccumulator();
    private final WeakReference<AEAccumulator> accRef = new WeakReference<>(accumulator);
    private final Queue<Object2LongOpenHashMap<AEKey>> pendingQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean drainRequested = new AtomicBoolean(false);
    public MEExtendedAsyncOutputPartMachine(BlockEntityCreationInfo info) { super(info); }

    private void requestAsyncDrain() {
        if (drainRequested.compareAndSet(false, true)) AEWriteService.INSTANCE.prepareDrainedData(accRef, pendingQueue, drainRequested);
    }
    private boolean mergeFromPendingData() {
        boolean merged = false;
        synchronized (accumulator) {
            Object2LongOpenHashMap<AEKey> data;
            while ((data = pendingQueue.peek()) != null) {
                for (var entry : data.object2LongEntrySet()) Math.addExact(buffer.getLong(entry.getKey()), entry.getLongValue());
                for (var entry : data.object2LongEntrySet()) buffer.put(entry.getKey(), buffer.getLong(entry.getKey()) + entry.getLongValue());
                pendingQueue.remove(); merged |= !data.isEmpty();
            }
        }
        return merged;
    }
    @Override protected void flushAsyncQueue() {
        AEWriteService.INSTANCE.flushBlocking(accRef, pendingQueue, drainRequested, AEWriteService.FLUSH_TIMEOUT);
        mergeFromPendingData();
    }
    @Override public void onUnload() { flushAsyncQueue(); super.onUnload(); }
    @Override public void onMachineDestroyed() { flushAsyncQueue(); super.onMachineDestroyed(); }

    private <T> List<T> accept(List<T> left, boolean simulate, Function<T, GenericStack> resolver) {
        var accepted = new Object2LongOpenHashMap<AEKey>();
        try {
            for (T ingredient : left) {
                var output = resolver.apply(ingredient);
                if (output != null) accepted.put(output.what(), Math.addExact(accepted.getLong(output.what()), output.amount()));
            }
            synchronized (accumulator) {
                var total = new Object2LongOpenHashMap<AEKey>(buffer);
                accumulator.copyTo(total);
                for (var queued : pendingQueue) for (var entry : queued.object2LongEntrySet())
                    total.put(entry.getKey(), Math.addExact(total.getLong(entry.getKey()), entry.getLongValue()));
                for (var entry : accepted.object2LongEntrySet()) Math.addExact(total.getLong(entry.getKey()), entry.getLongValue());
                if (!simulate) accumulator.addAll(accepted);
            }
        } catch (ArithmeticException overflow) { return left; }
        if (!simulate && !accepted.isEmpty()) { markAsChanged(); getMETrait().notifySelfIO(); }
        return List.of();
    }
    @Override public void buildMainUI(ParentWidget<?> mainWidget, PosGuiData data, PanelSyncManager manager, UISettings settings) {
        var value = new StringSyncValue(() -> Integer.toString(priority), text -> {
            try { setPriority(Integer.parseInt(text)); } catch (NumberFormatException ignored) {}
        }).allowC2S();
        manager.syncValue("asyncOutputPriority", value);
        mainWidget.child(Flow.col().coverChildren().child(buildOutputDisplay(manager)).child(Flow.row().coverChildren().marginBottom(2)
                .child(Text.lang("field.gtlcore.me_chamber_manager_terminal.priority").asWidget().width(65).height(16))
                .child(new TextFieldWidget().size(85, 16).value(value).setNumbers(i -> Math.max(10, Math.min(100_000, i))))));
    }
    @Override protected NotifiableMERecipeHandlerTrait<SizedIngredient, ItemStack> createItemOutputHandler() {
        return new MEItemOutputHandler() {
            @Override public List<SizedIngredient> meHandleRecipeOutputInner(List<SizedIngredient> left, boolean simulate) {
                return accept(left, simulate, ingredient -> AEOutputIngredients.item(ingredient, simulate));
            }
        };
    }
    @Override protected NotifiableMERecipeHandlerTrait<SizedFluidIngredient, FluidStack> createFluidOutputHandler() {
        return new MEFluidOutputHandler() {
            @Override public List<SizedFluidIngredient> meHandleRecipeOutputInner(List<SizedFluidIngredient> left, boolean simulate) {
                return accept(left, simulate, ingredient -> AEOutputIngredients.fluid(ingredient, simulate));
            }
        };
    }
    @Override protected @NotNull IMEFilterIOTrait createMETrait() { return new MEAsyncFilterIOTrait(); }
    @Override public @NotNull IMEFilterIOTrait getMETrait() { return (IMEFilterIOTrait) meTrait; }
    @Override protected void registerDefaultServices() { getMainNode().addService(IGridTickable.class, new Ticker()); }
    protected class Ticker implements IGridTickable {
        @Override public TickingRequest getTickingRequest(IGridNode node) { return new TickingRequest(MIN_FREQUENCY, MAX_FREQUENCY, false); }
        @Override public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
            boolean merged = mergeFromPendingData(); if (merged) markAsChanged();
            boolean pending;
            synchronized (accumulator) {
                pending = !pendingQueue.isEmpty() || !accumulator.isEmpty();
            }
            if (pending) requestAsyncDrain();
            if (!getMainNode().isActive() || buffer.isEmpty()) {
                if (pending) return TickRateModulation.FASTER;
                if (ticksSinceLastCall >= MAX_FREQUENCY) { isSleeping = true; return TickRateModulation.SLEEP; }
                return TickRateModulation.SLOWER;
            }
            if (AEBufferedOutput.flush(buffer, getMainNode().getGrid(), actionSource)) { markAsChanged(); return TickRateModulation.URGENT; }
            return TickRateModulation.SLOWER;
        }
    }
    protected class MEAsyncFilterIOTrait extends MEIOTrait implements IMEFilterIOTrait {
        @Override public void notifySelfIO() {
            super.notifySelfIO();
            getMainNode().ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
        }
    }
}
