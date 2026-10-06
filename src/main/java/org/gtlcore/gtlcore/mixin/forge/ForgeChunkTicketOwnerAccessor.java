package org.gtlcore.gtlcore.mixin.forge;

import net.minecraft.resources.ResourceLocation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.neoforged.neoforge.common.world.chunk.ForcedChunkManager$TicketOwner", remap = false)
public interface ForgeChunkTicketOwnerAccessor {

    @Accessor("id")
    ResourceLocation gtlcore$getTicketControllerId();

}
