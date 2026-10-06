package org.gtlcore.gtlcore.mixin.extendedae;

import appeng.api.stacks.AEKey;
import com.glodblock.github.extendedae.common.items.ItemInfinityCell;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(ItemInfinityCell.class)
public abstract class InfinityCellMixin {

    /**
     * @author Dragons
     * @reason 无限元件提供9.2P下单能力
     */
    @Overwrite(remap = false)
    public static long getAsIntMax(AEKey key) {
        return Long.MAX_VALUE;
    }
}
