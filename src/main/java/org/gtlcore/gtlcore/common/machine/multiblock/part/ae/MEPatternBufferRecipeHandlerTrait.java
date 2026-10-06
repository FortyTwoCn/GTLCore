package org.gtlcore.gtlcore.common.machine.multiblock.part.ae;

import com.gregtechceu.gtceu.api.capability.recipe.*;


public class MEPatternBufferRecipeHandlerTrait extends MEPatternBufferRecipeHandlerTraitBase {

    public MEPatternBufferRecipeHandlerTrait(MEPatternBufferPartMachine ioBuffer, IO io) {
        super(ioBuffer, io);
    }

    @Override
    protected MEItemInputHandlerBase createMEItemHandler(IO io) {
        return new MEItemInputHandler(getMachine(), io);
    }

    @Override
    protected MEFluidHandlerBase createMEFluidHandler(IO io) {
        return new MEFluidHandler(getMachine(), io);
    }

    @Override
    public MEPatternBufferPartMachine getMachine() {
        return (MEPatternBufferPartMachine) super.getMachine();
    }

    private static class MEItemInputHandler extends MEItemInputHandlerBase {

        public MEItemInputHandler(MEPatternBufferPartMachine machine, IO io) {
            super(machine, io);
        }

        public MEPatternBufferPartMachine getMachine() {
            return (MEPatternBufferPartMachine) super.getMachine();
        }
    }

    private static class MEFluidHandler extends MEFluidHandlerBase {

        public MEFluidHandler(MEPatternBufferPartMachine machine, IO io) {
            super(machine, io);
        }

        public MEPatternBufferPartMachine getMachine() {
            return (MEPatternBufferPartMachine) super.getMachine();
        }
    }
}
