package org.gtlcore.gtlcore.migration;

import committee.nova.mods.avaritia.client.model.loader.CosmicBakeModel;
import committee.nova.mods.avaritia.client.model.loader.HaloBakedModel;
import committee.nova.mods.avaritia.client.shader.AvaritiaShaders;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Bakes and draws the unchanged production JSON through the actual native Avaritia loaders and renderer. */
@EventBusSubscriber(modid = "gtlcore_recipe_test", value = Dist.CLIENT)
public final class NativeAvaritiaClientFixtures {
    private record Model(ResourceLocation id, String loader, String sha256) {}
    private static final List<Model> MODELS = models();
    private static boolean loading, opened;
    private static long started;

    private static List<Model> models() {
        try (var stream = NativeAvaritiaClientFixtures.class.getResourceAsStream("/native-avaritia-api.json");
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var profile = JsonParser.parseReader(reader).getAsJsonObject();
            var models = new ArrayList<Model>();
            profile.getAsJsonObject("models").entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(entry -> {
                String[] path = entry.getKey().split("/", 4); var data = entry.getValue().getAsJsonObject();
                String model = path[3].substring(0, path[3].length() - 5);
                models.add(new Model(ResourceLocation.fromNamespaceAndPath(path[1], model),
                        data.get("loader").getAsString(), data.get("sha256").getAsString()));
            });
            if (models.size() != 50) throw new AssertionError("Incomplete production Avaritia model profile");
            return List.copyOf(models);
        } catch (Exception error) { throw new AssertionError("Native model profile did not load", error); }
    }

    @EventBusSubscriber(modid = "gtlcore_recipe_test", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Models {
        @SubscribeEvent public static void additional(ModelEvent.RegisterAdditional event) {
            MODELS.forEach(model -> event.register(ModelResourceLocation.standalone(model.id())));
        }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("gtlcore.avaritiaValidation.enabled")) return;
        var client = Minecraft.getInstance();
        if (started == 0) started = System.nanoTime();
        if (System.nanoTime() - started > java.util.concurrent.TimeUnit.MINUTES.toNanos(5))
            throw new AssertionError("Native Avaritia client timed out");
        if (!loading && client.getOverlay() == null && client.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
            loading = true;
            client.createWorldOpenFlows().openWorld("Native Recipe Validation", () -> { throw new AssertionError("Avaritia world did not load"); });
            return;
        }
        if (!opened && client.level != null && client.player != null && client.player.tickCount >= 40 &&
                client.screen == null && client.getOverlay() == null) {
            opened = true; validate(client); client.setScreen(new RendererScreen());
        }
    }

    private static void validate(Minecraft client) {
        int cosmic = 0, halo = 0;
        for (var model : MODELS) {
            var baked = client.getModelManager().getModel(ModelResourceLocation.standalone(model.id()));
            if (model.loader().equals("avaritia:cosmic")) {
                if (!(baked instanceof CosmicBakeModel)) throw new AssertionError("Production cosmic JSON did not bake through its native loader: " + model.id());
                ++cosmic;
            } else {
                if (!(baked instanceof HaloBakedModel)) throw new AssertionError("Production halo JSON did not bake through its native loader: " + model.id());
                ++halo;
            }
            var path = ResourceLocation.fromNamespaceAndPath(model.id().getNamespace(), "models/" + model.id().getPath() + ".json");
            try (var input = client.getResourceManager().getResource(path).orElseThrow().open()) {
                var digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
                if (!digest.equals(model.sha256())) throw new AssertionError("Runtime pack replaced original production model JSON: " + model.id());
            } catch (Exception error) { throw new AssertionError("Production model resource was not preserved: " + model.id(), error); }
        }
        if (cosmic != 11 || halo != 39 || AvaritiaShaders.COSMIC_SHADER == null || AvaritiaShaders.cosmicTime == null)
            throw new AssertionError("Incomplete native model bake or cosmic shader registration");
        LogManager.getLogger().info("GTL_NATIVE_AVARITIA_MODEL_BAKE=PASS models=50 cosmic=11 halo=39 original_json=true");
    }

    private static final class RendererScreen extends Screen {
        private int frames;
        private boolean captured;
        private RendererScreen() { super(Component.literal("Native Avaritia production models")); }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float tick) {
            graphics.fill(0, 0, width, height, 0xff18202c);
            graphics.drawCenteredString(font, title, width / 2, 8, 0xffffffff);
            graphics.drawCenteredString(font, "11 cosmic / 39 halo — unchanged source JSON", width / 2, 21, 0xffdbe4f0);
            graphics.flush();
            int rows = (MODELS.size() + 7) / 8;
            int cell = Math.min(48, Math.min((width - 12) / 8, (height - 40) / rows - 8));
            if (cell < 12) throw new AssertionError("Native model validation window is too small");
            int first = (width - 8 * cell) / 2;
            var stack = new ItemStack(Items.PAPER);
            for (int index = 0; index < MODELS.size(); ++index) {
                var model = MODELS.get(index); var baked = minecraft.getModelManager().getModel(ModelResourceLocation.standalone(model.id()));
                int x = first + (index % 8) * cell, y = 36 + (index / 8) * (cell + 8);
                if (x < 0 || x + cell > width || y < 0 || y + cell + 6 > height)
                    throw new AssertionError("Production model is outside the validation window: " + model.id());
                graphics.pose().pushPose();
                try {
                    graphics.pose().translate(x + cell / 2.0, y + cell / 2.0, 100);
                    graphics.pose().scale(cell - 8, -(cell - 8), cell - 8);
                    minecraft.getItemRenderer().render(stack, ItemDisplayContext.GUI, false, graphics.pose(), graphics.bufferSource(),
                            LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, baked);
                } finally { graphics.pose().popPose(); }
                graphics.bufferSource().endBatch();
                graphics.drawString(font, Integer.toString(index + 1), x, y + cell - 3, 0xffdbe4f0, false);
            }
            graphics.flush();
            if (++frames >= 60 && !captured) {
                captured = true;
                net.minecraft.client.Screenshot.grab(minecraft.gameDirectory, "native-avaritia-models.png", minecraft.getMainRenderTarget(), message -> minecraft.execute(() -> {
                    LogManager.getLogger().info("GTL_NATIVE_AVARITIA_CLIENT=PASS models=50 cosmic=11 halo=39 native_loaders=true native_render=true source_json_unchanged=true bounded=true screenshot={}", message.getString());
                    minecraft.stop();
                }));
            }
        }
    }
}
