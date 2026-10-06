package org.gtlcore.gtlcore.common.machine.multiblock.part.maintenance;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.multiblock.CleanroomType;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.machine.trait.CleanroomProviderTrait;
import net.minecraft.util.Mth;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.SyncHandlers;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.layout.Flow;
import brachy.modularui.utils.Alignment;
import brachy.modularui.widgets.textfield.TextFieldWidget;
import java.util.Set;

public class GravityCleaningConfigurationMaintenancePartMachine extends AutoConfigurationMaintenanceHatchPartMachine
        implements IAutoConfiguratioGravityPart {
    @SaveField @SyncToClient private int gravity;
    private final boolean isConfig;
    private final CleanroomProviderTrait cleanroomProvider;
    public GravityCleaningConfigurationMaintenancePartMachine(BlockEntityCreationInfo info) { this(info, true, Set.of()); }
    public GravityCleaningConfigurationMaintenancePartMachine(BlockEntityCreationInfo info, boolean configurable) { this(info, configurable, Set.of()); }
    public GravityCleaningConfigurationMaintenancePartMachine(BlockEntityCreationInfo info, Set<CleanroomType> types) { this(info, true, types); }
    private GravityCleaningConfigurationMaintenancePartMachine(BlockEntityCreationInfo info, boolean configurable, Set<CleanroomType> types) {
        super(info); isConfig = configurable;
        cleanroomProvider = types.isEmpty() ? null : attachTrait(new CleanroomProviderTrait(types));
        if (cleanroomProvider != null) cleanroomProvider.setActive(true);
    }
    @Override public void addedToController(MultiblockControllerMachine controller, String name) {
        super.addedToController(controller, name); ICleaningRoom.addedToController(controller, cleanroomProvider);
    }
    @Override public void removedFromController(MultiblockControllerMachine controller) {
        ICleaningRoom.removedFromController(controller, cleanroomProvider); super.removedFromController(controller);
    }
    @Override public int getTier() { return GTValues.UXV; }
    @Override public int getCurrentGravity() { return gravity; }
    @Override public void setCurrentGravity(int value) { gravity = Mth.clamp(value, 0, 100); markAsChanged(); syncDataHolder.markClientSyncFieldDirty("gravity"); }
    @Override public void setDurationMultiplier(float value) { if (isConfig) super.setDurationMultiplier(value); }
    @Override public float getDurationMultiplier() { return isConfig ? super.getDurationMultiplier() : 1f; }
    @Override public void buildMainUI(ParentWidget<?> contents, PosGuiData data, PanelSyncManager sync, UISettings settings) {
        var fields = Flow.col().coverChildren().childPadding(6).crossAxisAlignment(Alignment.CrossAxis.CENTER).center();
        if (isConfig) fields.child(durationControls(sync));
        var value = SyncHandlers.intNumber(this::getCurrentGravity, this::setCurrentGravity).allowC2S(); sync.syncValue("maintenanceGravity", value);
        fields.child(new TextFieldWidget().name("maintenanceGravityField").value(value).setNumbersLong(number -> Math.clamp(number, 0, 100))
                .setDefaultNumber(0).size(80, 18).tooltip(t -> t.addLine(Text.lang("gtlcore.condition.gravity"))));
        contents.child(fields);
    }
}
