package org.gtlcore.gtlcore.api.machine;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.machine.mui.MachineUIPanelBuilder;
import com.gregtechceu.gtceu.common.mui.GTGuiTextures;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.DynamicLinkedSyncHandler;
import brachy.modularui.value.sync.GenericListSyncHandler;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.ListWidget;
import brachy.modularui.widgets.dynamic.DynamicWidget;
import brachy.modularui.widgets.layout.Flow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import java.util.*;

/** Native server-owned machine timings with local ExtendedAE position highlighting. */
public final class PerformanceMonitorMachine extends MetaMachine implements IMuiMachine {
    public static boolean observe;
    public static final Map<MetaMachine, Integer> PERFORMANCE_MAP = Collections.synchronizedMap(new WeakHashMap<>());
    private List<Row> cachedRows = List.of();
    private long refreshedAt = Long.MIN_VALUE;

    public PerformanceMonitorMachine(BlockEntityCreationInfo info) { super(info); }
    public static ResourceKey<Level> getDimensionKey(ResourceLocation id) { return ResourceKey.create(Registries.DIMENSION, id); }

    public record Row(ResourceLocation dimension, BlockPos position, String nameKey, int micros) {
        public Row {
            Objects.requireNonNull(dimension); position = Objects.requireNonNull(position).immutable(); Objects.requireNonNull(nameKey);
            if (micros < 0) throw new IllegalArgumentException("Negative machine tick time");
        }
        public static Row read(RegistryFriendlyByteBuf buf) {
            return new Row(buf.readResourceLocation(), buf.readBlockPos(), buf.readUtf(), buf.readVarInt());
        }
        public static void write(RegistryFriendlyByteBuf buf, Row row) {
            buf.writeResourceLocation(row.dimension); buf.writeBlockPos(row.position); buf.writeUtf(row.nameKey); buf.writeVarInt(row.micros);
        }
        public Component label() {
            return Component.translatable(nameKey).append(" ").append(Component.translatable("tooltip.jade.delay", micros)).append(" μs");
        }
        public Component locationText() {
            return Component.translatable("recipe.condition.dimension.tooltip", dimension.toString()).append(" [").append(position.toShortString()).append("]");
        }
    }

    public List<Row> getDisplayRows() {
        if (isRemote() || getLevel() == null) return List.of();
        observe = true; long tick = getLevel().getGameTime();
        if (refreshedAt != Long.MIN_VALUE && tick >= refreshedAt && tick - refreshedAt < 40) return cachedRows;
        var rows = new ArrayList<Row>();
        synchronized (PERFORMANCE_MAP) {
            for (var entry : PERFORMANCE_MAP.entrySet()) {
                var machine = entry.getKey(); var level = machine.getLevel();
                if (level == null || level.isClientSide || machine.isRemoved()) continue;
                rows.add(new Row(level.dimension().location(), machine.getBlockPos(), machine.getBlockState().getBlock().getDescriptionId(),
                        Math.max(0, entry.getValue())));
            }
            PERFORMANCE_MAP.clear();
        }
        // Sorting a list retains machines with equal timings; the old TreeMap comparator dropped them.
        rows.sort(Comparator.comparingInt(Row::micros).reversed().thenComparing(row -> row.dimension.toString()).thenComparing(Row::position));
        cachedRows = List.copyOf(rows); refreshedAt = tick; return cachedRows;
    }

    @Override public MachineUIPanelBuilder getPanelBuilder(PosGuiData data, PanelSyncManager sync, UISettings settings) {
        return MachineUIPanelBuilder.panelBuilder(this).attachInventory(false).addTitleBar(false);
    }

    @Override public void buildMainUI(ParentWidget<?> contents, PosGuiData data, PanelSyncManager sync, UISettings settings) {
        var rows = GenericListSyncHandler.<RegistryFriendlyByteBuf, Row>builder().getter(this::getDisplayRows)
                .serializer(Row::write).deserializer(Row::read).copy(row -> row).build();
        sync.syncValue("performanceRows", rows);
        var list = new DynamicLinkedSyncHandler<>(rows).widgetProvider((manager, value) -> {
            var result = new ListWidget<>().name("performanceRowsList").size(182, 96).padding(0)
                    .scrollDirection(new brachy.modularui.widget.scroll.VerticalScrollData(false, 4));
            int index = 0;
            for (var row : value.getValue()) result.child(Flow.row().name("performanceRow" + index++).width(178).height(16).padding(0)
                    .child(new PerformanceMonitorRowWidget(row).width(160).height(16)
                            .tooltipDynamic(tooltip -> { tooltip.addLine(row.label()); tooltip.addLine(row.locationText()); }))
                    .child(new ButtonWidget<>().name("performanceHighlight" + (index - 1)).size(16).overlay(Text.str("[ ]"))
                            .tooltipDynamic(tooltip -> tooltip.addLine(row.locationText()))
                            .onMousePressed((context, button) -> { if (button != 0) return false; ClientHighlight.show(row); return true; })));
            return result;
        }).allowC2S(false);
        sync.syncValue("performanceRowsWidget", list);
        contents.child(Flow.col().coverChildren().background(GTGuiTextures.DISPLAY)
                .child(Text.lang(getBlockState().getBlock().getDescriptionId()).asWidget().width(182).height(17).color(0xFFFFFFFF))
                .child(new DynamicWidget<>().syncHandler(list).size(182, 96)));
    }

    private static final class ClientHighlight {
        private static void show(Row row) {
            com.glodblock.github.extendedae.client.render.EAEHighlightHandler.highlight(row.position,
                    getDimensionKey(row.dimension), System.currentTimeMillis() + 15_000);
        }
    }
}
