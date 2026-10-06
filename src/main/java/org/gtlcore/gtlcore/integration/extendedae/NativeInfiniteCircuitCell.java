package org.gtlcore.gtlcore.integration.extendedae;

import appeng.api.stacks.AEItemKey;
import com.glodblock.github.extendedae.common.items.ItemInfinityCell;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;
import net.minecraft.world.item.Item;

/** ExtendedAE 1.21 records the supplied key on the item type instead of accepting a legacy record NBT tag. */
public final class NativeInfiniteCircuitCell {
    public static Item create(Item.Properties properties) {
        var circuit = GTItems.PROGRAMMED_CIRCUIT.asStack();
        IntCircuitBehaviour.setCircuitConfiguration(circuit, 0);
        var key = AEItemKey.of(circuit);
        return new ItemInfinityCell(() -> key, properties.stacksTo(1));
    }

    private NativeInfiniteCircuitCell() {}
}
