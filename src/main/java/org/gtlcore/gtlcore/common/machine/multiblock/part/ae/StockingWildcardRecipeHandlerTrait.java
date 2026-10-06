package org.gtlcore.gtlcore.common.machine.multiblock.part.ae;

import org.gtlcore.gtlcore.integration.wildcard.MEStockingWildcardPatternBufferPartMachine;

import com.gregtechceu.gtceu.api.capability.recipe.IO;


public class StockingWildcardRecipeHandlerTrait extends MEPatternBufferRecipeHandlerTraitBase {

    public StockingWildcardRecipeHandlerTrait(MEPatternBufferPartMachineBase ioBuffer, IO io) {
        super(ioBuffer, io);
    }

    @Override
    protected MEItemInputHandlerBase createMEItemHandler(IO io) {
        return new WildcardItemInputHandler(getMachine(), io);
    }

    @Override
    protected MEFluidHandlerBase createMEFluidHandler(IO io) {
        return new WildcardFluidHandler(getMachine(), io);
    }

    @Override
    public MEStockingWildcardPatternBufferPartMachine getMachine() {
        return (MEStockingWildcardPatternBufferPartMachine) super.getMachine();
    }

    @Override
    public WildcardItemInputHandler getMeItemHandler() {
        return (WildcardItemInputHandler) this.meItemHandler;
    }

    @Override
    public WildcardFluidHandler getMeFluidHandler() {
        return (WildcardFluidHandler) this.meFluidHandler;
    }

    public static class WildcardItemInputHandler extends MEItemInputHandlerBase {

        public WildcardItemInputHandler(MEStockingWildcardPatternBufferPartMachine machine, IO io) {
            super(machine, io);
        }

        @Override
        public MEStockingWildcardPatternBufferPartMachine getMachine() {
            return (MEStockingWildcardPatternBufferPartMachine) super.getMachine();
        }
    }

    public static class WildcardFluidHandler extends MEFluidHandlerBase {

        public WildcardFluidHandler(MEStockingWildcardPatternBufferPartMachine machine, IO io) {
            super(machine, io);
        }

        @Override
        public MEStockingWildcardPatternBufferPartMachine getMachine() {
            return (MEStockingWildcardPatternBufferPartMachine) super.getMachine();
        }
    }
}
