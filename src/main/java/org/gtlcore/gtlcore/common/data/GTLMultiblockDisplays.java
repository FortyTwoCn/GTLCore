package org.gtlcore.gtlcore.common.data;

import org.gtlcore.gtlcore.common.machine.multiblock.electric.CoilWorkableElectricMultipleRecipesMultiblockMachine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.multiblock.CoilWorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;

import brachy.modularui.api.drawable.Text;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.value.sync.BooleanSyncValue;
import brachy.modularui.value.sync.IntSyncValue;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.StringSyncValue;
import brachy.modularui.value.sync.DynamicLinkedSyncHandler;
import brachy.modularui.widgets.dynamic.DynamicWidget;
import brachy.modularui.widgets.layout.Flow;
import brachy.modularui.utils.Alignment;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;

import java.util.List;
import java.util.ArrayList;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/** Native menu displays, independent of machine registration and backed by server-owned sync values. */
public final class GTLMultiblockDisplays {
    private GTLMultiblockDisplays() {}

    /** Read legacy text collectors on the server, then rebuild native rows when the synced text changes. */
    @SuppressWarnings("unchecked")
    public static BiFunction<MultiblockControllerMachine, PanelSyncManager, List<IWidget>> syncedLines(
            BiConsumer<MultiblockControllerMachine, List<Component>> collector) {
        return syncedLines("gtlAdditional", collector);
    }

    public static BiFunction<MultiblockControllerMachine, PanelSyncManager, List<IWidget>> syncedLines(
            String syncKey, BiConsumer<MultiblockControllerMachine, List<Component>> collector) {
        return syncedLines(syncKey, -1, collector);
    }

    /** An explicit viewport width lets long translated rows calculate their native wrapped height. */
    @SuppressWarnings("unchecked")
    public static BiFunction<MultiblockControllerMachine, PanelSyncManager, List<IWidget>> syncedLines(
            String syncKey, int textWidth, BiConsumer<MultiblockControllerMachine, List<Component>> collector) {
        return (controller, sync) -> {
            var registries = controller.hasLevel() ? controller.getLevel().registryAccess() :
                    RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            var text = sync.getOrCreateSyncHandler(syncKey + "Text", StringSyncValue.class, () -> new StringSyncValue(() -> {
                if (sync.isClient()) return "[]";
                var components = new ArrayList<Component>();
                collector.accept(controller, components);
                var array = new JsonArray();
                for (var component : components) array.add(JsonParser.parseString(Component.Serializer.toJson(component, registries)));
                return array.toString();
            }));
            DynamicLinkedSyncHandler<ByteBuf, StringSyncValue> rows = sync.getOrCreateSyncHandler(
                    syncKey + "Rows", DynamicLinkedSyncHandler.class,
                    () -> new DynamicLinkedSyncHandler<>(text).widgetProvider((panelSync, value) -> {
                        var column = Flow.col().coverChildren();
                        if (textWidth > 0) column.width(textWidth).crossAxisAlignment(Alignment.CrossAxis.START);
                        for (var element : JsonParser.parseString(value.getStringValue()).getAsJsonArray()) {
                            var row = Text.of(Component.Serializer.fromJson(element, registries)).asWidget();
                            if (textWidth > 0) row.width(textWidth).maxWidth(textWidth).alignment(Alignment.TopLeft);
                            column.child(row);
                        }
                        return column;
                    }).allowC2S(false));
            var display = new DynamicWidget<>().syncHandler(rows).coverChildren();
            if (textWidth > 0) display.width(textWidth);
            return List.of(display);
        };
    }

    public static final BiFunction<MultiblockControllerMachine, PanelSyncManager, List<IWidget>> CHEMICAL_PLANT = (controller, sync) -> {
        if (!(controller instanceof CoilWorkableElectricMultiblockMachine coil)) return List.of();
        var formed = formed(controller, sync);
        var tier = sync.getOrCreateSyncHandler("gtlChemicalCoilTier", IntSyncValue.class,
                () -> new IntSyncValue(coil::getCoilTier));
        return List.of(
                Text.dynamic(() -> Component.translatable("gtceu.machine.eut_multiplier.tooltip",
                        FormattingUtil.formatPercent((1 - tier.getIntValue() * .05) * .8)))
                        .asWidget().setEnabledIf(w -> formed.getBoolValue()),
                Text.dynamic(() -> Component.translatable("gtceu.machine.duration_multiplier.tooltip",
                        FormattingUtil.formatPercent((1 - tier.getIntValue() * .05) * .6)))
                        .asWidget().setEnabledIf(w -> formed.getBoolValue()));
    };

    public static final BiFunction<MultiblockControllerMachine, PanelSyncManager, List<IWidget>> MAX_TEMPERATURE =
            (controller, sync) -> temperature(controller, sync, true);
    public static final BiFunction<MultiblockControllerMachine, PanelSyncManager, List<IWidget>> TEMPERATURE =
            (controller, sync) -> temperature(controller, sync, false);

    public static final BiFunction<MultiblockControllerMachine, PanelSyncManager, List<IWidget>> COIL_PARALLEL = (controller, sync) -> {
        if (!(controller instanceof CoilWorkableElectricMultiblockMachine coil)) return List.of();
        var temperature = sync.getOrCreateSyncHandler("gtlParallelCoilTemperature", IntSyncValue.class,
                () -> new IntSyncValue(() -> coil.getCoilType().getCoilTemperature()));
        return parallel(controller, sync, () -> (int) Math.min(Integer.MAX_VALUE, Math.pow(2, temperature.getIntValue() / 900.0)));
    };

    public static final BiFunction<MultiblockControllerMachine, PanelSyncManager, List<IWidget>> MULTIPLERECIPES_COIL_PARALLEL = (controller, sync) -> {
        if (!(controller instanceof CoilWorkableElectricMultipleRecipesMultiblockMachine coil)) return List.of();
        var temperature = sync.getOrCreateSyncHandler("gtlCrossParallelCoilTemperature", IntSyncValue.class,
                () -> new IntSyncValue(() -> coil.getCoilType().getCoilTemperature()));
        return parallel(controller, sync, () -> (int) Math.min(Integer.MAX_VALUE, Math.pow(2, temperature.getIntValue() / 900.0)));
    };

    public static final BiFunction<MultiblockControllerMachine, PanelSyncManager, List<IWidget>> PROCESSING_PLANT_PARALLEL = (controller, sync) -> {
        if (!(controller instanceof WorkableElectricMultiblockMachine electric)) return List.of();
        var tier = sync.getOrCreateSyncHandler("gtlProcessingTier", IntSyncValue.class,
                () -> new IntSyncValue(electric::getTier));
        return parallel(controller, sync, () -> 4 * tier.getIntValue() - 1);
    };

    private static BooleanSyncValue formed(MultiblockControllerMachine controller, PanelSyncManager sync) {
        return sync.getOrCreateSyncHandler("isFormed", BooleanSyncValue.class,
                () -> new BooleanSyncValue(controller::isFormed));
    }

    private static List<IWidget> temperature(MultiblockControllerMachine controller, PanelSyncManager sync, boolean tierBonus) {
        if (!(controller instanceof CoilWorkableElectricMultiblockMachine coil)) return List.of();
        var formed = formed(controller, sync);
        var temperature = sync.getOrCreateSyncHandler("gtlCoilTemperature", IntSyncValue.class,
                () -> new IntSyncValue(() -> coil.getCoilType().getCoilTemperature()));
        var tier = sync.getOrCreateSyncHandler("gtlTemperatureMachineTier", IntSyncValue.class,
                () -> new IntSyncValue(coil::getTier));
        return List.of(Text.dynamic(() -> Component.translatable("gtceu.multiblock.blast_furnace.max_temperature",
                Component.literal(FormattingUtil.formatNumbers(temperature.getIntValue() +
                        (tierBonus ? 100L * Math.max(0, tier.getIntValue() - GTValues.MV) : 0)) + "K")
                        .withStyle(ChatFormatting.RED)))
                .asWidget().setEnabledIf(w -> formed.getBoolValue()));
    }

    private static List<IWidget> parallel(MultiblockControllerMachine controller, PanelSyncManager sync,
                                         java.util.function.IntSupplier amount) {
        var formed = formed(controller, sync);
        return List.of(Text.dynamic(() -> Component.translatable("gtceu.multiblock.parallel",
                Component.literal(FormattingUtil.formatNumbers(amount.getAsInt())).withStyle(ChatFormatting.DARK_PURPLE))
                .withStyle(ChatFormatting.GRAY)).asWidget().setEnabledIf(w -> formed.getBoolValue()));
    }
}
