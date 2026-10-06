package org.gtlcore.gtlcore.common.machine.multiblock.part.ae;

import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEIOPartMachine;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEIOTrait;
import org.gtlcore.gtlcore.integration.ae2.AEGridMachineSettings;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.integration.ae2.machine.feature.IGridConnectedMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.trait.GridNodeHolder;

import net.minecraft.core.Direction;

import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class MEIOPartMachine extends MultiblockPartMachine implements IGridConnectedMachine, IMEIOPartMachine {

    @SaveField
    @Getter
    protected final GridNodeHolder nodeHolder;

    @Getter
    @SyncToClient
    protected boolean isOnline;

    protected final IMEIOTrait meTrait;

    protected final IActionSource actionSource;

    protected final IO io;

    protected boolean isSleeping = true;

    public MEIOPartMachine(BlockEntityCreationInfo holder, IO io) {
        super(holder);
        this.nodeHolder = attachTrait(createNodeHolder());
        this.actionSource = IActionSource.ofMachine(nodeHolder.getMainNode()::getNode);
        this.io = io;
        this.meTrait = createMETrait();
        if (!(meTrait instanceof MachineTrait trait)) {
            throw new IllegalStateException("ME IO behaviour must be a native machine trait");
        }
        attachPersistentTrait("meTrait", trait);
    }

    protected @NotNull IMEIOTrait createMETrait() {
        return new MEIOTrait();
    }

    @Override
    public @NotNull IMEIOTrait getMETrait() {
        return meTrait;
    }

    @Override
    public boolean isOnline() { return isOnline; }

    @Override
    public void setOnline(boolean online) {
        if (isOnline == online) return;
        isOnline = online;
        syncDataHolder.markClientSyncFieldDirty("isOnline");
    }

    @Override
    public void onLoad() {
        AEGridMachineSettings.applyExposedSides(this, getFrontFacing());
        super.onLoad();
    }

    @Override
    public void onUnload() {
        super.onUnload();
        isSleeping = true;
        setOnline(false);
    }

    @Override
    public void saveChanges() {
        markAsChanged();
    }

    @Override
    public IManagedGridNode getMainNode() {
        return nodeHolder.getMainNode();
    }

    @Override
    public void onRotated(@NotNull Direction oldFacing, @NotNull Direction newFacing) {
        super.onRotated(oldFacing, newFacing);
        AEGridMachineSettings.applyExposedSides(this, newFacing);
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.@NotNull State reason) {
        IGridConnectedMachine.super.onMainNodeStateChanged(reason);
        if (isOnline()) meTrait.notifySelfIO();
    }

    protected GridNodeHolder createNodeHolder() {
        return new GridNodeHolder(this);
    }

    public class MEIOTrait extends MachineTrait implements IMEIOTrait {

        @Override
        protected List<Class<?>> validMachineClasses() {
            return List.of(MEIOPartMachine.class);
        }

        @Override
        public MEIOPartMachine getMachine() {
            return (MEIOPartMachine) super.getMachine();
        }

        @Override
        public void notifySelfIO() {
            if (isSleeping) {
                if (getMainNode().isActive()) {
                    getMainNode().ifPresent((grid, node) -> {
                        grid.getTickManager().wakeDevice(node);
                        isSleeping = false;
                    });
                }
            }
        }

        @Override
        public IO getIO() {
            return io;
        }

    }
}
