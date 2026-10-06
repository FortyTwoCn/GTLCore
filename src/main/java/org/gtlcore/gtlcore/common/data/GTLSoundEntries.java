package org.gtlcore.gtlcore.common.data;

import com.gregtechceu.gtceu.api.sound.SoundEntry;
import net.neoforged.neoforge.registries.DeferredHolder;

import static com.gregtechceu.gtceu.common.registry.GTRegistration.REGISTRATE;

public class GTLSoundEntries {

    public static final DeferredHolder<SoundEntry, SoundEntry> DTPF = REGISTRATE.sound("dtpf").register();
    public static final DeferredHolder<SoundEntry, SoundEntry> FUSIONLOOP = REGISTRATE.sound("fusionloop").register();

    public static void init() {}
}
