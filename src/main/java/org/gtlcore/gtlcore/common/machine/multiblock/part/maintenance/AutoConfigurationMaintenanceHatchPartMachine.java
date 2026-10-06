package org.gtlcore.gtlcore.common.machine.multiblock.part.maintenance;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.machine.multiblock.part.AutoMaintenanceHatchPartMachine;
import com.gregtechceu.gtceu.config.ConfigHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.*;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.layout.Flow;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Extending the native maintenance part makes the controller discover automatic maintenance normally. */
public class AutoConfigurationMaintenanceHatchPartMachine extends AutoMaintenanceHatchPartMachine
        implements IAutoConfigurationMaintenanceHatch {
    public static final float MIN_DURATION_MULTIPLIER = 0.2f, MAX_DURATION_MULTIPLIER = 1.2f;
    @SaveField @SyncToClient private float gtlDurationMultiplier = 1f;
    public AutoConfigurationMaintenanceHatchPartMachine(BlockEntityCreationInfo info) { super(info); }
    @Override public int getTier() { return GTValues.IV; }
    @Override public float getDurationMultiplier() { return gtlDurationMultiplier; }
    @Override public void setDurationMultiplier(float value) {
        if (!Float.isFinite(value)) return;
        gtlDurationMultiplier = Math.clamp(value, MIN_DURATION_MULTIPLIER, MAX_DURATION_MULTIPLIER);
        markAsChanged(); syncDataHolder.markClientSyncFieldDirty("gtlDurationMultiplier");
    }
    @Override public float getTimeMultiplier() {
        float value = getDurationMultiplier();
        return BigDecimal.valueOf(value < 1 ? -20 * value + 21 : -8 * value + 9).setScale(2, RoundingMode.HALF_UP).floatValue();
    }
    @Override public GTRecipe modifyRecipe(GTRecipe recipe) {
        if (!ConfigHolder.INSTANCE.machines.enableMaintenance || getDurationMultiplier() == 1f) return recipe;
        var copy = recipe.copy(); copy.duration = (int) Math.max(1, recipe.duration * getDurationMultiplier()); return copy;
    }
    @Override public boolean shouldOpenUI(Player player, InteractionHand hand, BlockHitResult hit) { return true; }
    @Override public void buildMainUI(ParentWidget<?> contents, PosGuiData data, PanelSyncManager sync, UISettings settings) {
        contents.child(durationControls(sync).center());
    }
    protected Flow durationControls(PanelSyncManager sync) {
        var multiplier = new FloatSyncValue(this::getDurationMultiplier); sync.syncValue("maintenanceDuration", multiplier);
        var subtract = new InteractionSyncHandler().setOnMousePressed(mouse -> {
            if (mouse.isLeftMouseButton()) setDurationMultiplier(getDurationMultiplier() - 0.01f * (mouse.ctrl() ? 100 : mouse.shift() ? 10 : 1));
        });
        var add = new InteractionSyncHandler().setOnMousePressed(mouse -> {
            if (mouse.isLeftMouseButton()) setDurationMultiplier(getDurationMultiplier() + 0.01f * (mouse.ctrl() ? 100 : mouse.shift() ? 10 : 1));
        });
        sync.syncValue("maintenanceDecrease", subtract); sync.syncValue("maintenanceIncrease", add);
        return Flow.col().name("maintenanceDurationControls").coverChildren().childPadding(6)
                .child(Text.dynamic(() -> Component.translatable("gtceu.maintenance.configurable_duration", multiplier.getFloatValue()))
                        .asWidget().width(150).tooltip(t -> t.addLine(Text.dynamic(() -> multiplier.getFloatValue() == 1f ?
                                Component.translatable("gtceu.maintenance.configurable_duration.unchanged_description") :
                                Component.translatable("gtceu.maintenance.configurable_duration.changed_description", multiplier.getFloatValue())))))
                .child(Flow.row().coverChildren().childPadding(4)
                        .child(Text.lang("gtceu.maintenance.configurable_duration.modify").asWidget())
                        .child(new ButtonWidget<>().name("maintenanceDecreaseButton").size(18).overlay(Text.str("−")).syncHandler(subtract))
                        .child(new ButtonWidget<>().name("maintenanceIncreaseButton").size(18).overlay(Text.str("+")).syncHandler(add)));
    }
}
