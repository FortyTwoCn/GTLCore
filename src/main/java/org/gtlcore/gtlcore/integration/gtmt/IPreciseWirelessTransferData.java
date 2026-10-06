package org.gtlcore.gtlcore.integration.gtmt;

import com.hepdd.gtmthings.api.misc.ITransferData;
import java.math.BigInteger;

/** Native GTMThings snapshots can retain exact throughput supplied by a Core addon. */
public interface IPreciseWirelessTransferData extends ITransferData {
    BigInteger preciseThroughput();
}
