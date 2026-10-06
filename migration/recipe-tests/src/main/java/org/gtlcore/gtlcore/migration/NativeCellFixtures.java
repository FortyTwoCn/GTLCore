package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.integration.ae2.InfinityCell;
import org.gtlcore.gtlcore.integration.ae2.FastInfinityCell;
import com.tterrag.registrate.util.entry.ItemEntry;
import appeng.api.stacks.AEKeyType;

public final class NativeCellFixtures {
    public static final ItemEntry<InfinityCell> NORMAL = NativeMachineFixtures.REGISTRATE
            .item("native_infinity_cell", properties -> new InfinityCell(AEKeyType.items())).register();
    public static final ItemEntry<FastInfinityCell> FAST = NativeMachineFixtures.REGISTRATE
            .item("native_fast_infinity_cell", properties -> new FastInfinityCell()).register();
    public static void init(net.neoforged.bus.api.IEventBus modBus) {
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            appeng.api.storage.StorageCells.addCellHandler(org.gtlcore.gtlcore.integration.ae2.storage.InfinityCellHandler.INSTANCE);
            appeng.api.storage.StorageCells.addCellHandler(org.gtlcore.gtlcore.integration.ae2.storage.FastInfinityCellHandler.INSTANCE);
        }));
    }
}
