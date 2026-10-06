package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.integration.ae2.throughput.METhroughputMonitorPart;
import org.gtlcore.gtlcore.integration.ae2.throughput.ThroughputMonitorStorageTracker;
import org.gtlcore.gtlcore.integration.ae2.patternrelay.PatternRelayPart;
import appeng.items.parts.PartItem;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

/** Independent monitor item; the configurator supplies only the original runtime item identity. */
public final class NativeThroughputFixtures {
    private static final GTRegistrate CONFIG = GTRegistrate.create("gtlcore", false);
    public static final ItemEntry<Item> CONFIGURATOR = CONFIG.item("throughput_monitor_configurator", Item::new).register();
    public static final ItemEntry<PartItem<METhroughputMonitorPart>> MONITOR = NativeMachineFixtures.REGISTRATE
            .item("native_ae_throughput_monitor", p -> new PartItem<>(p, METhroughputMonitorPart.class, METhroughputMonitorPart::new)).register();
    public static final ItemEntry<PartItem<PatternRelayPart>> RELAY = NativeMachineFixtures.REGISTRATE
            .item("native_ae_pattern_relay", p -> new PartItem<>(p, PatternRelayPart.class, PatternRelayPart::new)).register();
    public static void init(IEventBus bus) {
        CONFIG.registerEventListeners(bus);
        NeoForge.EVENT_BUS.addListener(ThroughputMonitorStorageTracker::onServerTickStart);
        NeoForge.EVENT_BUS.addListener(ThroughputMonitorStorageTracker::onServerTickEnd);
        NeoForge.EVENT_BUS.addListener(ThroughputMonitorStorageTracker::onServerStopped);
    }
}
