package org.gtlcore.gtlcore.common.data;

import org.gtlcore.gtlcore.api.GTLConstants;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Typed settings carried by native AE2 memory cards. */
public final class GTLDataComponents {
    private static final DeferredRegister<DataComponentType<?>> TYPES =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, GTLConstants.MOD_ID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> AUTO_EXPAND =
            TYPES.register("auto_expand", () -> DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());
    private GTLDataComponents() {}
    public static void register(IEventBus bus) { TYPES.register(bus); }
}
