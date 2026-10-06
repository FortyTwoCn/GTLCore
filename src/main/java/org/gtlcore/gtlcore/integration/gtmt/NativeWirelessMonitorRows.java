package org.gtlcore.gtlcore.integration.gtmt;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.hepdd.gtmthings.api.misc.ITransferData;
import com.hepdd.gtmthings.api.misc.WirelessEnergyContainer;
import com.hepdd.gtmthings.common.item.IWirelessMonitor;
import com.hepdd.gtmthings.config.ConfigHolder;
import com.hepdd.gtmthings.utils.TeamUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;
import static com.hepdd.gtmthings.utils.FormatUtil.formatWithConstantWidth;

/** Native GTMThings statistics and highlight actions, with a bounded list for large networks. */
public final class NativeWirelessMonitorRows {
    public record Transfers(List<ITransferData> entries, int hidden) {}
    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);
    private static final Comparator<ITransferData> ORDER = Comparator.comparing(NativeWirelessMonitorRows::throughput)
            .thenComparing(data -> data.machine().getLevel().dimension().location().toString())
            .thenComparingLong(data -> data.machine().getBlockPos().asLong());

    private NativeWirelessMonitorRows() {}

    public static BigInteger throughput(ITransferData data) {
        return data instanceof IPreciseWirelessTransferData precise ? precise.preciseThroughput() :
                BigInteger.valueOf(data.Throughput());
    }

    public static Transfers transfers(Map<MetaMachine, ITransferData> snapshot, UUID team, boolean all) {
        int limit = WirelessEnergyDisplayTextLimiter.DEFAULT_MAX_VISIBLE_ENTRIES;
        var selected = new PriorityQueue<ITransferData>(limit, ORDER.reversed());
        int matching = 0;
        for (var data : snapshot.values()) {
            if (data == null || data.UUID() == null || data.machine() == null || data.machine().isRemoved() ||
                    data.machine().getLevel() == null || (!all && !data.UUID().equals(team))) continue;
            matching++;
            if (selected.size() < limit) selected.offer(data);
            else if (ORDER.compare(data, selected.peek()) < 0) {
                selected.poll();
                selected.offer(data);
            }
        }
        var entries = new ArrayList<>(selected);
        entries.sort(ORDER);
        return new Transfers(List.copyOf(entries), Math.max(0, matching - entries.size()));
    }

    public static List<Component> build(IWirelessMonitor monitor, boolean all) {
        var container = monitor.getWirelessEnergyContainer();
        if (container == null || monitor.getLevel() == null || monitor.getUUID() == null) return List.of();
        var rows = new ArrayList<Component>();
        var stored = container.getStorage();
        rows.add(Component.translatable("gtmthings.machine.wireless_energy_monitor.tooltip.0",
                TeamUtil.GetName(monitor.getLevel(), monitor.getUUID())).withStyle(ChatFormatting.AQUA));
        rows.add(formatWithConstantWidth("gtmthings.machine.wireless_energy_monitor.tooltip.1",
                Component.literal(formatDecimal(new BigDecimal(stored)))).withStyle(ChatFormatting.GOLD));
        if (ConfigHolder.INSTANCE.isWirelessRateEnable) {
            var rate = BigDecimal.valueOf(container.getRate());
            rows.add(formatWithConstantWidth("gtmthings.machine.wireless_energy_monitor.tooltip.2",
                    Component.literal(formatDecimal(rate)), Component.literal(amperage(rate)), voltageName(rate))
                    .withStyle(ChatFormatting.GRAY));
        }
        var stat = container.getEnergyStat();
        rows.add(Component.translatable("gtmthings.machine.wireless_energy_monitor.tooltip.net_power"));
        appendAverage(rows, "last_minute", stat.getMinuteAvg(), ChatFormatting.DARK_AQUA);
        appendAverage(rows, "last_hour", stat.getHourAvg(), ChatFormatting.YELLOW);
        appendAverage(rows, "last_day", stat.getDayAvg(), ChatFormatting.DARK_GREEN);
        var current = stat.getAvgEnergy();
        appendAverage(rows, "now", current, ChatFormatting.DARK_PURPLE);
        if (current.signum() != 0) {
            var perSecond = current.abs().multiply(BigDecimal.valueOf(20)).setScale(0, RoundingMode.CEILING)
                    .toBigInteger().max(BigInteger.ONE);
            if (current.signum() > 0) {
                Component time = container.getCapacity() == null ?
                        Component.translatable("gtmthings.machine.wireless_energy_monitor.tooltip.time_to_fill") :
                        timeText(container.getCapacity().subtract(stored).max(BigInteger.ZERO).divide(perSecond));
                rows.add(Component.translatable("gtceu.multiblock.power_substation.time_to_fill", time)
                        .withStyle(ChatFormatting.GRAY));
            } else {
                rows.add(Component.translatable("gtceu.multiblock.power_substation.time_to_drain",
                        timeText(stored.max(BigInteger.ZERO).divide(perSecond))).withStyle(ChatFormatting.GRAY));
            }
        }
        if (ConfigHolder.INSTANCE.isWirelessRateEnable && container.getBindPos() != null) {
            var binding = container.getBindPos();
            rows.add(Component.translatable("gtmthings.machine.wireless_energy_hatch.tooltip.2",
                    Component.translatable("recipe.condition.dimension.tooltip", binding.dimension().location().toString())
                            .append(" [").append(binding.pos().toShortString()).append("] "))
                    .withStyle(ChatFormatting.GRAY));
        }
        // The native snapshot is shared by all monitors. Reading one monitor must not drain another's list.
        var transfers = transfers(WirelessEnergyContainer.TRANSFER_SNAPSHOT, TeamUtil.getTeamUUID(monitor.getUUID()), all);
        for (var data : transfers.entries()) rows.add(transferText(data));
        if (transfers.hidden() > 0) rows.add(Component.translatable(
                "gtlcore.machine.wireless_energy_monitor.tooltip.hidden_entries", transfers.hidden())
                .withStyle(ChatFormatting.DARK_GRAY));
        return List.copyOf(rows);
    }

    private static Component transferText(ITransferData data) {
        var machine = data.machine();
        var dimension = machine.getLevel().dimension().location().toString();
        var power = new BigDecimal(throughput(data));
        var hover = Component.translatable("recipe.condition.dimension.tooltip", dimension)
                .append(" [").append(machine.getBlockPos().toShortString()).append("] ")
                .append(Component.translatable("gtmthings.machine.wireless_energy_monitor.tooltip.0",
                        TeamUtil.GetName(machine.getLevel(), data.UUID())));
        return Component.translatable(machine.getBlockState().getBlock().getDescriptionId())
                .withStyle(Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)))
                .append(power.signum() > 0 ? " +" : " ").append(formatDecimal(power)).append(" EU/t (")
                .append(GTValues.VNF[tier(power)]).append(")")
                .append(Component.literal(" [ ] ").withStyle(style -> style.withClickEvent(new ClickEvent(
                        ClickEvent.Action.CHANGE_PAGE, "gtmthings:highlight:" + dimension + ";" + machine.getBlockPos().asLong()))));
    }

    private static void appendAverage(List<Component> rows, String period, BigDecimal average, ChatFormatting color) {
        rows.add(formatWithConstantWidth("gtmthings.machine.wireless_energy_monitor.tooltip." + period,
                Component.literal(formatDecimal(average)).withStyle(color), Component.literal(amperage(average)),
                voltageName(average)));
    }

    public static int tier(BigDecimal power) {
        var absolute = power.abs();
        long voltage = absolute.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) >= 0 ? Long.MAX_VALUE : absolute.longValue();
        return Math.max(0, Math.min(GTValues.VNF.length - 1, GTUtil.getFloorTierByVoltage(voltage)));
    }

    public static String amperage(BigDecimal power) {
        return power.abs().divide(BigDecimal.valueOf(GTValues.VEX[tier(power)]), 1, RoundingMode.FLOOR).toEngineeringString();
    }

    private static Component voltageName(BigDecimal power) {
        return Component.literal(GTValues.VNF[tier(power)]);
    }

    public static String formatDecimal(BigDecimal value) {
        return value.abs().compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0 ?
                FormattingUtil.DECIMAL_FORMAT_SIC_2F.format(value) : FormattingUtil.formatNumberReadable(value.longValue());
    }

    public static Component timeText(BigInteger seconds) {
        var duration = Duration.ofSeconds(seconds.max(BigInteger.ZERO).min(MAX_LONG).longValueExact());
        String key;
        long value;
        if (duration.getSeconds() <= 180) { value = duration.getSeconds(); key = "seconds"; }
        else if (duration.toMinutes() <= 180) { value = duration.toMinutes(); key = "minutes"; }
        else if (duration.toHours() <= 72) { value = duration.toHours(); key = "hours"; }
        else if (duration.toDays() <= 730) { value = duration.toDays(); key = "days"; }
        else if (duration.toDays() / 365 < 1_000_000) { value = duration.toDays() / 365; key = "years"; }
        else return Component.translatable("gtceu.multiblock.power_substation.time_forever");
        return Component.translatable("gtceu.multiblock.power_substation.time_" + key, FormattingUtil.formatNumbers(value));
    }
}
