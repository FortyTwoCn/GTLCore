package org.gtlcore.gtlcore.common.machine.multiblock.part.ae;

import org.gtlcore.gtlcore.integration.wildcard.MEWildcardPatternBufferPartMachine;

import com.gregtechceu.gtceu.api.capability.recipe.IO;


public class WildcardRecipeHandlerTrait extends MEPatternBufferRecipeHandlerTraitBase {

    public WildcardRecipeHandlerTrait(MEPatternBufferPartMachineBase ioBuffer, IO io) {
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
    public MEWildcardPatternBufferPartMachine getMachine() {
        return (MEWildcardPatternBufferPartMachine) super.getMachine();
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

        public WildcardItemInputHandler(MEWildcardPatternBufferPartMachine machine, IO io) {
            super(machine, io);
        }

        @Override
        public MEWildcardPatternBufferPartMachine getMachine() {
            return (MEWildcardPatternBufferPartMachine) super.getMachine();
        }
    }

    public static class WildcardFluidHandler extends MEFluidHandlerBase {

        public WildcardFluidHandler(MEWildcardPatternBufferPartMachine machine, IO io) {
            super(machine, io);
        }

        @Override
        public MEWildcardPatternBufferPartMachine getMachine() {
            return (MEWildcardPatternBufferPartMachine) super.getMachine();
        }
    }
}
