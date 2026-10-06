package org.gtlcore.gtlcore.client.renderer.machine;

import org.gtlcore.gtlcore.api.GTLConstants;

import com.gregtechceu.gtceu.client.renderer.machine.DynamicRenderManager;

import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ModelEvent;

/** Native GT model codecs and the original standalone OBJ dependencies. */
public final class GTLDynamicRenders {

    private GTLDynamicRenders() {}

    public static void register(IEventBus modBus) {
        DynamicRenderManager.register(GTLConstants.id("annihilate_generator"), AnnihilateGeneratorRenderer.TYPE);
        DynamicRenderManager.register(GTLConstants.id("eye_of_harmony"), EyeOfHarmonyRenderer.TYPE);
        DynamicRenderManager.register(GTLConstants.id("space_elevator"), SpaceElevatorRenderer.TYPE);
        modBus.addListener(GTLDynamicRenders::additionalModels);
    }

    private static void additionalModels(ModelEvent.RegisterAdditional event) {
        event.register(ModelResourceLocation.standalone(EyeOfHarmonyRenderer.STAR_MODEL));
        event.register(ModelResourceLocation.standalone(EyeOfHarmonyRenderer.SPACE_MODEL));
        EyeOfHarmonyRenderer.ORBIT_OBJECTS.forEach(id -> event.register(ModelResourceLocation.standalone(id)));
        event.register(ModelResourceLocation.standalone(SpaceElevatorRenderer.CLIMBER_MODEL));
    }
}
