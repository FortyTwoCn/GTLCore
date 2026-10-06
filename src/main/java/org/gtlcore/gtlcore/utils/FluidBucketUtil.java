package org.gtlcore.gtlcore.utils;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;

public final class FluidBucketUtil {

    private FluidBucketUtil() {}

    public static ItemStack getFilledBucket(FluidStack fluid) {
        ItemStack bucket = FluidUtil.getFilledBucket(fluid);
        if (!bucket.isEmpty()) {
            return bucket;
        }

        Item registeredBucket = fluid.getFluid().getBucket();
        return registeredBucket == Items.AIR ? ItemStack.EMPTY : registeredBucket.getDefaultInstance();
    }
}
