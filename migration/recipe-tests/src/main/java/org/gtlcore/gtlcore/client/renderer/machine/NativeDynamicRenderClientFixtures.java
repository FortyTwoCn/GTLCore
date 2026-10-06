package org.gtlcore.gtlcore.client.renderer.machine;

import org.gtlcore.gtlcore.client.ClientUtil;
import org.gtlcore.gtlcore.migration.NativeDynamicRenderFixtures;
import org.gtlcore.gtlcore.migration.NativeDynamicRenderFixtures.RenderMachine;

import com.gregtechceu.gtceu.client.model.machine.MachineModel;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRender;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import com.mojang.serialization.JsonOps;
import org.apache.logging.log4j.LogManager;

import java.util.List;

/** Actual GT machine model codecs, original OBJ assets, shader and native draw/upload/reload paths. */
@EventBusSubscriber(modid = "gtlcore_recipe_test", value = Dist.CLIENT)
public final class NativeDynamicRenderClientFixtures {
    private static volatile List<BlockPos> positions;
    private static boolean loading, prepared, opened;
    private static long started;

    public static void register(IEventBus modBus) {
        GTLDynamicRenders.register(modBus);
        HarmonyAnimationRenderer.register(modBus);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("gtlcore.dynamicRenderValidation.enabled")) return;
        var client = Minecraft.getInstance();
        if (started == 0) started = System.nanoTime();
        if (System.nanoTime() - started > java.util.concurrent.TimeUnit.MINUTES.toNanos(5))
            throw new AssertionError("Native machine renderer client timed out");
        if (!loading && client.getOverlay() == null && client.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
            loading = true;
            client.createWorldOpenFlows().openWorld("Native Recipe Validation", () -> { throw new AssertionError("Native render world did not load"); });
            return;
        }
        if (client.level == null || client.player == null || client.player.tickCount < 40 || client.screen != null || client.getOverlay() != null) return;
        var server = client.getSingleplayerServer();
        if (!prepared) { prepared = true; var id = client.player.getUUID(); server.execute(() -> prepare(server, id)); return; }
        if (positions == null || opened) return;
        for (var pos : positions) {
            if (!(client.level.getBlockEntity(pos) instanceof RenderMachine machine) || !machine.isActive() || !machine.isFormed()) return;
        }
        opened = true; validate(client); client.setScreen(new RenderScreen());
    }

    private static void prepare(MinecraftServer server, java.util.UUID id) {
        var player = server.getPlayerList().getPlayer(id);
        var level = player.serverLevel();
        var anchor = player.blockPosition().offset(6, 0, 0);
        var result = new java.util.ArrayList<BlockPos>();
        for (int index = 0; index < 3; ++index) {
            var pos = anchor.offset(index * 3, 0, 0);
            level.setBlockAndUpdate(pos, NativeDynamicRenderFixtures.MACHINES.get(index).getBlock().defaultBlockState());
            var machine = (RenderMachine) level.getBlockEntity(pos);
            machine.setFrontFacing(Direction.NORTH); machine.setWorkingEnabled(false);
            machine.checkAndFormStructure();
            if (!machine.isFormed()) throw new AssertionError("Native renderer fixture did not form");
            machine.activate(); result.add(pos);
        }
        positions = List.copyOf(result);
    }

    private static MachineModel model(Minecraft client, RenderMachine machine) {
        var model = client.getBlockRenderer().getBlockModel(machine.getBlockState());
        if (!(model instanceof MachineModel nativeModel)) throw new AssertionError("Native machine model binding is missing");
        if (nativeModel.getDynamicRenders().size() != 1) throw new AssertionError("Native model lost its production dynamic renderer");
        return nativeModel;
    }

    private static void validate(Minecraft client) {
        for (String name : new String[] { "star_layer", "space_layer", "climber_overlay" }) {
            var id = org.gtlcore.gtlcore.api.GTLConstants.id("block/obj/" + name);
            var sprite = client.getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS).apply(id);
            if (!sprite.contents().name().equals(id)) throw new AssertionError("Production OBJ texture is missing: " + id);
        }
        for (String profile : new String[] { "native-textures.json", "native-additions-textures.json" }) {
            try (var input = NativeDynamicRenderClientFixtures.class.getResourceAsStream("/" + profile);
                 var reader = new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8)) {
                var textures = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("textures");
                for (var entry : textures.entrySet()) {
                    String[] path = entry.getKey().substring("src/main/resources/assets/".length()).split("/", 2);
                    var id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(path[0], path[1]);
                    var expected = entry.getValue().getAsJsonObject();
                    try (var bytes = client.getResourceManager().getResource(id).orElseThrow().open();
                         var image = com.mojang.blaze3d.platform.NativeImage.read(bytes)) {
                        if (image.getWidth() != expected.get("width").getAsInt() || image.getHeight() != expected.get("height").getAsInt())
                            throw new AssertionError("Production texture dimensions changed: " + id);
                    }
                }
            } catch (Exception error) { throw new AssertionError("Native PNG decoder did not load preserved textures: " + profile, error); }
        }
        var objects = new java.util.ArrayList<>(EyeOfHarmonyRenderer.ORBIT_OBJECTS);
        objects.add(EyeOfHarmonyRenderer.STAR_MODEL); objects.add(EyeOfHarmonyRenderer.SPACE_MODEL); objects.add(SpaceElevatorRenderer.CLIMBER_MODEL);
        for (var id : objects) {
            if (ClientUtil.getBakedModel(id) == client.getModelManager().getMissingModel())
                throw new AssertionError("Production standalone OBJ is missing: " + id);
        }
        try {
            var field = HarmonyAnimationRenderer.class.getDeclaredField("animationShader"); field.setAccessible(true);
            var shader = (net.minecraft.client.renderer.ShaderInstance) field.get(null);
            if (shader == null || shader.getUniform("AnimationTransform") == null) throw new AssertionError("Native harmony shader did not compile");
            // The test mod packages Core resources under its separate ID. Exercise the actual
            // geometry compiler directly; original-pack admission and world batching are separate.
            var compile = HarmonyModelCache.class.getDeclaredMethod("compile", net.minecraft.client.renderer.block.ModelBlockRenderer.class);
            compile.setAccessible(true);
            for (String name : HarmonyModelCache.NAMES) {
                try (var mesh = new HarmonyModelCache(name, ClientUtil.getBakedModel(org.gtlcore.gtlcore.api.GTLConstants.id("obj/" + name)))) {
                    compile.invoke(mesh, client.getBlockRenderer().getModelRenderer());
                    var vertices = HarmonyModelCache.class.getDeclaredField("vertices"); vertices.setAccessible(true);
                    if (vertices.get(mesh) == null) throw new AssertionError("Native OBJ mesh upload did not produce a GPU buffer: " + name);
                }
            }
        } catch (ReflectiveOperationException error) { throw new AssertionError("Native geometry/shader validation failed", error); }
        for (int index = 0; index < positions.size(); ++index) {
            var machine = (RenderMachine) client.level.getBlockEntity(positions.get(index));
            var renderer = model(client, machine).getDynamicRenders().getFirst();
            var json = DynamicRender.CODEC.encodeStart(JsonOps.INSTANCE, renderer).getOrThrow();
            var restored = DynamicRender.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
            if (restored.getType() != renderer.getType()) throw new AssertionError("Production renderer codec lost its native type");
            Vec3 near = Vec3.atCenterOf(machine.getBlockPos()), far = near.add(600, 0, 0);
            if (index == 0) verifyAdmission(new AnnihilateGeneratorRenderer(), machine, near, far);
            if (index == 1) verifyAdmission(new EyeOfHarmonyRenderer(), machine, near, far);
            if (index == 2) verifyAdmission(new SpaceElevatorRenderer(), machine, near, far);
        }
    }

    private static <S extends DynamicRender<com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine, S>>
            void verifyAdmission(S render, RenderMachine machine, Vec3 near, Vec3 far) {
        if (!render.shouldRender(machine, near) || render.shouldRender(machine, far) || !render.shouldRenderOffScreen(machine))
            throw new AssertionError("Native active renderer range/global behavior changed");
        if (!render.getRenderBoundingBox(machine).contains(near.add(0, 360, 0)) && render.getViewDistance() == 512)
            throw new AssertionError("Native renderer bounds cut off the original tall/distant animation");
        machine.fixtureState(false, false);
        try {
            if (render.shouldRender(machine, near) || render.shouldRenderOffScreen(machine))
                throw new AssertionError("Native idle/unformed renderer admission changed");
        } finally { machine.fixtureState(true, true); }
    }

    private static final class RenderScreen extends Screen {
        private int frames, phase;
        private boolean capturing;
        private RenderScreen() { super(Component.literal("Native GT machine animations")); }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void render(GuiGraphics graphics, int x, int y, float partial) {
            graphics.fill(0, 0, width, height, 0xff18202c);
            graphics.drawCenteredString(font, title, width / 2, 8, 0xffffffff);
            String[] labels = { "Annihilate", "Eye of Harmony", "Space Elevator" };
            for (int index = 0; index < 3; ++index) {
                int left = index * width / 3, right = (index + 1) * width / 3;
                graphics.drawCenteredString(font, labels[index], (left + right) / 2, 25, 0xffdbe4f0);
                graphics.flush();
                var machine = (RenderMachine) minecraft.level.getBlockEntity(positions.get(index));
                graphics.pose().pushPose();
                try {
                    graphics.pose().translate((left + right) / 2.0, index == 2 ? height - 20 : height / 2.0, 100);
                    float scale = index == 0 ? 1.5f : index == 1 ? 4f : .45f;
                    graphics.pose().scale(scale, -scale, scale);
                    if (index == 0) graphics.pose().translate(-.5, -36.5, -39.5);
                    if (index == 1) graphics.pose().translate(-.5, -.5, -16.5);
                    if (index == 2) graphics.pose().translate(-.5, 0, -3.5);
                    model(minecraft, machine).render(machine, partial, graphics.pose(), graphics.bufferSource(),
                            LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                } finally { graphics.pose().popPose(); }
                graphics.bufferSource().endBatch();
            }
            graphics.flush();
            if (++frames >= 60 && !capturing) {
                capturing = true;
                String file = phase == 0 ? "native-machine-animations.png" : "native-machine-animations-reloaded.png";
                net.minecraft.client.Screenshot.grab(minecraft.gameDirectory, file, minecraft.getMainRenderTarget(), message -> minecraft.execute(() -> {
                    if (phase++ == 0) {
                        minecraft.reloadResourcePacks().thenRun(() -> minecraft.execute(() -> {
                            validate(minecraft); frames = 0; capturing = false;
                        }));
                    } else {
                        LogManager.getLogger().info("GTL_NATIVE_DYNAMIC_RENDER_CLIENT=PASS renderers=3 obj_models=6 native_models=true codecs=true shader=true mesh_upload=true admission=true resource_reload=true fixture_states=true textures=true");
                        minecraft.stop();
                    }
                }));
            }
        }
    }
}
