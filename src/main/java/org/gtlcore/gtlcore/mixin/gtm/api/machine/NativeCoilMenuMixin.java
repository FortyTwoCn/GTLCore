package org.gtlcore.gtlcore.mixin.gtm.api.machine;

import com.gregtechceu.gtceu.api.machine.multiblock.CoilWorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.sync_system.ClassSyncData;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.api.sync_system.data_transformers.CodecTransformer;
import com.gregtechceu.gtceu.common.block.CoilBlock;
import net.minecraft.resources.ResourceLocation;
import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.drawable.*;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.layout.Flow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import static com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine.MULTI_UI_TEXT_PANEL_HEIGHT;

/** Uses the actual coil texture on both menu sides, including non-vanilla tiers. */
@Mixin(CoilWorkableElectricMultiblockMachine.class)
public class NativeCoilMenuMixin {
    @Unique
    @SyncToClient
    private ResourceLocation gtlcore$coilTexture = CoilBlock.CoilType.CUPRONICKEL.getTexture();

    @Inject(method = {
            "<init>(Lcom/gregtechceu/gtceu/api/blockentity/BlockEntityCreationInfo;)V",
            "<init>(Lcom/gregtechceu/gtceu/api/blockentity/BlockEntityCreationInfo;Lcom/gregtechceu/gtceu/api/machine/trait/recipe/RecipeLogic;)V"
    }, at = @At("RETURN"), require = 2, expect = 2, allow = 2, remap = false)
    private void gtlcore$registerTextureCodec(CallbackInfo callback) {
        // The fixed native GT sync registry has no ResourceLocation transformer.
        // Configure this field through its public API, including all inherited coil machines.
        ClassSyncData.getClassData(getClass()).setCustomTransformerForField("gtlcore$coilTexture",
                new CodecTransformer<>(ResourceLocation.CODEC, ResourceLocation.STREAM_CODEC));
    }

    @Inject(method = "formStructure", at = @At("TAIL"), require = 1, remap = false)
    private void gtlcore$syncCoilTexture(String substructureName, CallbackInfo callback) {
        var machine = (CoilWorkableElectricMultiblockMachine) (Object) this;
        if (!machine.isFormed()) return;
        gtlcore$coilTexture = machine.getCoilType().getTexture();
        machine.getSyncDataHolder().markClientSyncFieldDirty("gtlcore$coilTexture");
    }

    @Inject(method = "buildMainUI", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void gtlcore$nativeCoilPanel(ParentWidget<?> mainWidget, PosGuiData guiData, PanelSyncManager syncManager,
                            UISettings settings, CallbackInfo callback) {
        var Texlocation = gtlcore$coilTexture;

        IDrawable leftTop = new UITexture.Builder()
                .location(Texlocation)
                .subAreaUV(0f, 0f, 7f / 16f, 4f / 16f)
                .colorType(ColorType.DEFAULT)
                .build();

        IDrawable leftMiddle = new UITexture.Builder()
                .location(Texlocation)
                .imageSize(16, 16)
                .subAreaUV(0f, 4f / 16f, 7f / 16f, 12f / 16f)
                .colorType(ColorType.DEFAULT)
                .tiled(7, 8)
                .build();

        IDrawable leftBottom = new UITexture.Builder()
                .location(Texlocation)
                .subAreaUV(0f, 12f / 16f, 7f / 16f, 16f / 16f)
                .colorType(ColorType.DEFAULT)
                .build();

        IDrawable rightBottom = new UITexture.Builder()
                .location(Texlocation)
                .subAreaUV(7f / 16f, 4f / 16f, 0f, 0f / 16f)
                .colorType(ColorType.DEFAULT)
                .build();

        IDrawable rightMiddle = new UITexture.Builder()
                .location(Texlocation)
                .imageSize(16, 16)
                .subAreaUV(7f / 16f, 12f / 16f, 0f, 4f / 16f)
                .colorType(ColorType.DEFAULT)
                .tiled(7, 8)
                .build();

        IDrawable rightTop = new UITexture.Builder()
                .location(Texlocation)
                .subAreaUV(7f / 16f, 16f / 16f, 0f, 12f / 16f)
                .colorType(ColorType.DEFAULT)
                .build();

        mainWidget
                .child(Flow.row().height(MULTI_UI_TEXT_PANEL_HEIGHT).coverChildrenWidth()
                        .child(Flow.col().coverChildrenWidth()
                                .child(new IDrawable.DrawableWidget(leftTop).size(7, 4))
                                .child(new IDrawable.DrawableWidget(leftMiddle).size(7, MULTI_UI_TEXT_PANEL_HEIGHT - 8))
                                .child(new IDrawable.DrawableWidget(leftBottom).size(7, 4)))
                        .child(((CoilWorkableElectricMultiblockMachine) (Object) this).getMainTextPanel(syncManager))
                        .child(Flow.col().coverChildrenWidth()
                                .child(new IDrawable.DrawableWidget(rightTop).size(7, 4))
                                .child(new IDrawable.DrawableWidget(rightMiddle).size(7,
                                        MULTI_UI_TEXT_PANEL_HEIGHT - 8))
                                .child(new IDrawable.DrawableWidget(rightBottom).size(7, 4))));
        callback.cancel();
    }
}
