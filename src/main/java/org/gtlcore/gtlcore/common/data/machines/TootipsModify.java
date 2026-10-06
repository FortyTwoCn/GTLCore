package org.gtlcore.gtlcore.common.data.machines;

import org.gtlcore.gtlcore.common.data.GTLRegistryTooltips;
import com.gregtechceu.gtceu.api.registry.registrate.builder.MachineBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.function.BiConsumer;

import static com.gregtechceu.gtceu.common.registry.GTRegistration.REGISTRATE;

/** Apply Core descriptions before native MachineDefinition freezes its tooltip callback. */
public final class TootipsModify {
    private TootipsModify() {}

    public static void modify(MachineBuilder<?, ?, ?> builder, BiConsumer<ItemStack, List<Component>> original) {
        if (builder.getOwner() != REGISTRATE) return;
        String name = builder.getName();
        boolean marked = switch (name) {
            case "hpca_computation_component", "hpca_advanced_computation_component", "electric_blast_furnace",
                    "steam_input_bus", "steam_output_bus", "steam_oven", "bronze_large_boiler",
                    "steel_large_boiler", "titanium_large_boiler", "tungstensteel_large_boiler" -> true;
            case "large_chemical_reactor", "research_station" -> false;
            default -> false;
        };
        if (!marked && !name.equals("large_chemical_reactor") && !name.equals("research_station")) return;
        builder.tooltipBuilder((stack, components) -> {
            if (original != null) original.accept(stack, components);
            switch (name) {
                case "hpca_computation_component", "hpca_advanced_computation_component" -> replace(components,
                        "gtceu.machine.hpca.component_type.computation_cwut", name.equals("hpca_computation_component") ? 8 : 32);
                case "electric_blast_furnace" -> {
                    insert(components, "gtceu.machine.electric_blast_furnace.tooltip.a");
                    int index = indexOf(components, "gtceu.machine.electric_blast_furnace.tooltip.1");
                    if (index >= 0) components.set(index, Component.translatable("gtceu.machine.perfect_oc"));
                    else insert(components, "gtceu.machine.perfect_oc");
                }
                case "steam_input_bus", "steam_output_bus" -> {
                    insert(components, name.equals("steam_input_bus") ? "gtceu.machine.item_bus.import.tooltip" : "gtceu.machine.item_bus.export.tooltip");
                    replace(components, "gtceu.universal.tooltip.item_storage_capacity", 16);
                }
                case "large_chemical_reactor" -> insert(components, "gtceu.machine.perfect_oc");
                case "research_station" -> {
                    for (int i = 2; i >= 0; i--) insert(components, "gtceu.machine.research_station.tooltip." + i);
                }
            }
            if (marked && indexOf(components, "gtlcore.registry.modify") < 0) GTLRegistryTooltips.MODIFY.accept(stack, components);
        });
    }

    private static int indexOf(List<Component> components, String key) {
        for (int i = 0; i < components.size(); i++)
            if (components.get(i).getContents() instanceof TranslatableContents text && text.getKey().equals(key)) return i;
        return -1;
    }
    private static void insert(List<Component> components, String key) {
        if (indexOf(components, key) < 0) components.add(Math.min(1, components.size()), Component.translatable(key));
    }
    private static void replace(List<Component> components, String key, int value) {
        int index = indexOf(components, key); var replacement = Component.translatable(key, value);
        if (index < 0) components.add(replacement);
        else components.set(index, replacement.withStyle(components.get(index).getStyle()));
    }
}
