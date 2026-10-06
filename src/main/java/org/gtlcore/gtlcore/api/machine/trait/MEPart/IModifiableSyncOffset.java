package org.gtlcore.gtlcore.api.machine.trait.MEPart;

public interface IModifiableSyncOffset {

    default int getMEUpdateInterval() {
        return 0;
    }

    default void setMEUpdateInterval(int offset) {}
}
