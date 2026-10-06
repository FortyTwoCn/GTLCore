package org.gtlcore.gtlcore.mixin.gtm;

import org.gtlcore.gtlcore.api.gui.preview.NativePreviewShapes;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.client.mui.schema.MutableSchema;
import com.gregtechceu.gtceu.client.renderer.PatternPreviewRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Registered visual shells retain their controller anchor and native facing in world previews. */
@Mixin(value = PatternPreviewRenderer.class, remap = false)
public abstract class MultiblockInWorldPreviewRendererMixin {
    @Unique private boolean gtlcore$visualCall;

    @Inject(method = "showPreviewCycleLevel", at = @At("HEAD"), cancellable = true)
    private void gtlcore$visual(BlockPos origin, MutableSchema schema, int duration, CallbackInfo ci) {
        if (gtlcore$visualCall || !(schema.getLevel().getBlockEntity(schema.getControllerPos()) instanceof MultiblockControllerMachine prototype)) return;
        var definition = prototype.getDefinition();
        if (!definition.isRenderWorldPreview()) { ci.cancel(); return; }
        if (!NativePreviewShapes.hasVisuals(definition)) return;
        var pages = NativePreviewShapes.get(definition);
        if (pages.isEmpty()) return;
        var controllerPos = origin.offset(schema.getControllerPos());
        var world = Minecraft.getInstance().level;
        var controller = world != null && world.hasChunkAt(controllerPos)
                && world.getBlockEntity(controllerPos) instanceof MultiblockControllerMachine actual ? actual : prototype;
        var geometry = pages.getFirst().oriented(controller.getFrontFacing(), controller.getUpwardsFacing(), controller.isFlipped());
        var blocks = new Long2ReferenceOpenHashMap<BlockState>();
        geometry.states().forEach((pos, state) -> blocks.put(pos.asLong(), state));
        var visual = new MutableSchema(blocks);
        gtlcore$visualCall = true;
        try {
            ((PatternPreviewRenderer) (Object) this).showPreviewCycleLevel(controllerPos, visual, duration);
        } finally { gtlcore$visualCall = false; }
        ci.cancel();
    }
}
