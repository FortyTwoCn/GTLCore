package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.machine.IStartupCriticalMachine;
import org.gtlcore.gtlcore.api.machine.IServerTickMachine;
import org.gtlcore.gtlcore.api.machine.PerformanceMonitorMachine;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;

/** Registered native block entities exercise the production tick mixin and monitor. */
public final class NativePerformanceFixtures {
    public static final MachineEntry<MachineDefinition> MONITOR = NativeMachineFixtures.REGISTRATE
            .machine("native_performance_monitor", PerformanceMonitorMachine::new).rotationState(RotationState.ALL).register();
    public static final MachineEntry<MachineDefinition> PROBE = NativeMachineFixtures.REGISTRATE
            .machine("native_tick_probe", TickProbe::new).rotationState(RotationState.ALL).register();
    public static final MachineEntry<MachineDefinition> CRITICAL = NativeMachineFixtures.REGISTRATE
            .machine("native_critical_tick_probe", CriticalProbe::new).rotationState(RotationState.ALL).register();
    public static void init() {}

    public static class TickProbe extends MetaMachine implements IServerTickMachine {
        public int calls;
        public boolean cancelled;
        @SaveField @SyncToClient public int savedValue;
        public TickProbe(BlockEntityCreationInfo info) { super(info); subscribeServerTick(() -> calls++); }
        @Override public boolean cancelTick() { return cancelled; }
    }
    public static final class CriticalProbe extends TickProbe implements IStartupCriticalMachine {
        public CriticalProbe(BlockEntityCreationInfo info) { super(info); }
    }
}
