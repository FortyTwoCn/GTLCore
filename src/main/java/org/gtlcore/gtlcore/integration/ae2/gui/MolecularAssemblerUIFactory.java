package org.gtlcore.gtlcore.integration.ae2.gui;

import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEMolecularAssemblerIOPartMachine;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.mui.factory.MachineUIFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import brachy.modularui.api.IUIHolder;
import brachy.modularui.factory.AbstractUIFactory;
import brachy.modularui.factory.GuiManager;
import brachy.modularui.factory.PosGuiData;

/** Carries the authoritative slot count before ModularUI assigns vanilla container slot numbers. */
public final class MolecularAssemblerUIFactory extends AbstractUIFactory<PosGuiData> {
    public static final MolecularAssemblerUIFactory INSTANCE = new MolecularAssemblerUIFactory();

    private MolecularAssemblerUIFactory() {
        super(ResourceLocation.fromNamespaceAndPath("gtlcore", "molecular_assembler"));
    }

    public static void register() { GuiManager.registerFactory(INSTANCE); }

    public void open(ServerPlayer player, MEMolecularAssemblerIOPartMachine machine) {
        if (machine.isRemoved() || machine.getLevel() != player.level() || !machine.hasPatternMenu()) {
            throw new IllegalArgumentException("Cannot open an unavailable molecular assembler IO menu");
        }
        GuiManager.open(this, createData(player, machine), player);
    }

    private static Data createData(Player player, MEMolecularAssemblerIOPartMachine machine) {
        return new Data(player, machine.getBlockPos(), machine.getTerminalPatternInventory().size(), machine.getPatternBindingRevision());
    }

    @Override public IUIHolder<PosGuiData> getGuiHolder(PosGuiData data) {
        if (!(MetaMachine.getMachine(data.getLevel(), data.getBlockPos()) instanceof MEMolecularAssemblerIOPartMachine machine)) {
            throw new IllegalStateException("Molecular assembler IO is no longer present");
        }
        return machine;
    }

    @Override public boolean canInteractWith(Player player, PosGuiData guiData) {
        if (!MachineUIFactory.INSTANCE.canInteractWith(player, guiData) || !(guiData instanceof Data data) ||
                !(MetaMachine.getMachine(data.getLevel(), data.getBlockPos()) instanceof MEMolecularAssemblerIOPartMachine machine)) return false;
        return data.getLevel().isClientSide || (machine.hasPatternMenu() && machine.getPatternBindingRevision() == data.bindingRevision &&
                machine.getTerminalPatternInventory().size() == data.slotCount);
    }

    @Override public void writeGuiData(PosGuiData guiData, RegistryFriendlyByteBuf buffer) {
        if (!(guiData instanceof Data data)) throw new IllegalArgumentException("Missing molecular inventory opening data");
        buffer.writeBlockPos(data.getBlockPos());
        buffer.writeVarInt(data.slotCount);
    }

    @Override public PosGuiData readGuiData(Player player, RegistryFriendlyByteBuf buffer) {
        var pos = buffer.readBlockPos();
        int size = buffer.readVarInt();
        if (!player.level().isClientSide) {
            // A client opening request must use the current server inventory, regardless of its advertised size.
            var machine = (MEMolecularAssemblerIOPartMachine) getGuiHolder(new PosGuiData(player, pos));
            return createData(player, machine);
        }
        return new Data(player, pos, size, 0);
    }

    public static final class Data extends PosGuiData {
        private final int slotCount;
        private final long bindingRevision;
        public Data(Player player, BlockPos pos, int slotCount, long bindingRevision) {
            super(player, pos);
            if (slotCount < 0) throw new IllegalArgumentException("Negative molecular pattern inventory size");
            this.slotCount = slotCount;
            this.bindingRevision = bindingRevision;
        }
        public int slotCount() { return slotCount; }
    }
}
