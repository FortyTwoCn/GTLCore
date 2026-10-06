package org.gtlcore.gtlcore.common.machine.multiblock.electric;

import org.gtlcore.gtlcore.api.machine.multiblock.IModularMachineHost;
import org.gtlcore.gtlcore.api.machine.multiblock.IModularMachineModule;
import org.gtlcore.gtlcore.utils.datastructure.ModuleRenderInfo;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import org.gtlcore.gtlcore.common.data.GTLMultiblockDisplays;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.value.sync.PanelSyncManager;

import javax.annotation.ParametersAreNonnullByDefault;

import static org.gtlcore.gtlcore.common.data.machines.AdvancedMultiBlockMachineA.SUPRACHRONAL_ASSEMBLY_LINE_MODULE;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class SuprachronalAssemblyLineMachine extends WorkableElectricMultiblockMachine
                                             implements IModularMachineHost<SuprachronalAssemblyLineMachine> {

    private final Set<IModularMachineModule<SuprachronalAssemblyLineMachine, ?>> modules = new ReferenceOpenHashSet<>();
    private int mam = 0;

    public SuprachronalAssemblyLineMachine(BlockEntityCreationInfo holder) {
        super(holder);
    }

    public SuprachronalAssemblyLineMachine(BlockEntityCreationInfo holder, RecipeLogic logic) {
        super(holder, logic);
    }

    private int getMAM() {
        if (getOffsetTimer() % 20 == 0) {
            mam = getFormedModuleCount();
        }
        return mam;
    }

    @Override
    public @NotNull Set<IModularMachineModule<SuprachronalAssemblyLineMachine, ?>> getModuleSet() {
        return modules;
    }

    @Override
    public BlockPos[] getModuleScanPositions() {
        final BlockPos pos = getBlockPos();
        return new BlockPos[] {
                pos.offset(3, 0, 0),
                pos.offset(-3, 0, 0),
                pos.offset(0, 0, 3),
                pos.offset(0, 0, -3)
        };
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public @NotNull List<ModuleRenderInfo> getModulesForRendering() {
        return List.of(
                new ModuleRenderInfo(
                        new BlockPos(0, 0, 3),
                        Direction.WEST,
                        Direction.UP,
                        Direction.WEST,
                        Direction.UP,
                        SUPRACHRONAL_ASSEMBLY_LINE_MODULE.get()),
                new ModuleRenderInfo(
                        new BlockPos(0, 0, -3),
                        Direction.WEST,
                        Direction.UP,
                        Direction.WEST,
                        Direction.UP,
                        SUPRACHRONAL_ASSEMBLY_LINE_MODULE.get()));
    }

    @Override
    public int getMaxModuleCount() {
        return 2;  // 最多连接2个模块
    }

    @Override
    public void formStructure(String name) {
        super.formStructure(name);
        safeClearModules();
        scanAndConnectModules();
    }

    @Override
    public void invalidateStructure(String name) {
        super.invalidateStructure(name);
        safeClearModules();
    }

    @Override
    public void onUnload() {
        safeClearModules();
        super.onUnload();
    }

    @Override
    public boolean onWorking() {
        boolean value = super.onWorking();
        if (getOffsetTimer() % 20 == 0) {
            if (exceedsModuleLimit()) {
                getRecipeLogic().setProgress(0);
            }
        }
        return value;
    }

    @Override
    public boolean beforeWorking(@Nullable GTRecipe recipe) {
        if (exceedsModuleLimit()) {
            getRecipeLogic().interruptRecipe();
            return false;
        }
        return super.beforeWorking(recipe);
    }

    @Override
    public List<IWidget> getWidgetsForDisplay(PanelSyncManager sync) {
        var widgets = new ArrayList<>(super.getWidgetsForDisplay(sync));
        widgets.addAll(GTLMultiblockDisplays.syncedLines("suprachronalModules", (controller, lines) -> {
            if (isFormed()) lines.add(Component.translatable("tooltip.gtlcore.installed_module_count", getMAM()));
        }).apply(this, sync));
        return widgets;
    }
}
