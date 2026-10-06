package org.gtlcore.gtlcore.common.item;

import org.gtlcore.gtlcore.client.renderer.item.VirtualIngredientRenderer;
import com.gregtechceu.gtceu.api.item.ComponentItem;
import com.lowdragmc.lowdraglib.client.renderer.IItemRendererProvider;
import com.lowdragmc.lowdraglib.client.renderer.IRenderer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** The native ComponentItem keeps behaviours; LDLib dispatches the item's renderer directly. */
public class VirtualIngredientItem extends ComponentItem implements IItemRendererProvider {

    public VirtualIngredientItem(Properties properties) {
        super(properties);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public IRenderer getRenderer(ItemStack stack) {
        return VirtualIngredientRenderer.INSTANCE;
    }
}
