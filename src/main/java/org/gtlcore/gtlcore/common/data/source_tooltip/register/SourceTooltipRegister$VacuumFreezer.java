package org.gtlcore.gtlcore.common.data.source_tooltip.register;

import org.gtlcore.gtlcore.api.event.SourceTooltipRegistrationEvent;
import org.gtlcore.gtlcore.common.data.source_tooltip.SpecialComponent;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.fluids.store.FluidStorageKeys;

import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.awt.*;

@OnlyIn(Dist.CLIENT)
public class SourceTooltipRegister$VacuumFreezer {

    private static final Component component_vacuum_freezer = Component.translatable("gtceu.vacuum_freezer").withStyle(SpecialComponent.color_recipe_type);

    public static void register(SourceTooltipRegistrationEvent event) {
        com.gregtechceu.gtceu.api.registry.GTRegistries.MATERIALS.stream()
                .filter(it -> it.hasProperty(PropertyKey.INGOT))
                .forEach(material -> {
                    if (TagPrefix.ingotHot.doGenerateItem(material) || (material.hasFluid() && material.getFluid(FluidStorageKeys.MOLTEN) != null)) {
                        if (material.getBlastTemperature() <= 1800) return;
                        var stack = ChemicalHelper.get(TagPrefix.ingot, material);
                        if (stack != null) {
                            var item = stack.getItem();
                            var tooltip = event.register(item);
                            if (tooltip != null) {
                                tooltip.get_or_create$control(SpecialComponent.component_default$control)
                                        .add(component_vacuum_freezer);
                            }
                        }
                    }
                });
    }
}
