package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.gui.NativePatternPreviewWidget;
import org.gtlcore.gtlcore.api.gui.preview.NativePreviewShapes;
import org.gtlcore.gtlcore.client.preview.*;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.ModularScreen;
import brachy.modularui.screen.ScreenWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.apache.logging.log4j.LogManager;

/** Uses production previews and native GT definitions in an actual NeoForge client and loaded world. */
@EventBusSubscriber(modid = "gtlcore_recipe_test", value = Dist.CLIENT)
public final class NativePreviewClientFixtures {
    private static long started;
    private static boolean loading, capturing;
    private static int stage, ticks;
    private static NativePatternPreviewWidget widget;
    private static net.minecraft.client.gui.screens.Screen parent;
    private static int worldPositions;
    private static net.minecraft.core.BlockPos worldOrigin;
    private static com.gregtechceu.gtceu.client.mui.schema.MutableSchema worldSchema;
    private static int customPositions;
    private static java.util.concurrent.CompletableFuture<Void> reload;

    @SubscribeEvent public static void frame(RenderFrameEvent.Pre event) {
        if (Boolean.getBoolean("gtlcore.previewValidation.enabled")) PreviewLifecycle.frame(event);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("gtlcore.previewValidation.enabled")) return;
        var client = Minecraft.getInstance();
        if (started == 0) started = System.nanoTime();
        if (System.nanoTime() - started > java.util.concurrent.TimeUnit.MINUTES.toNanos(5))
            throw new AssertionError("Native preview client timed out at stage " + stage);
        if (!loading && client.getOverlay() == null && client.screen instanceof TitleScreen) {
            loading = true;
            client.createWorldOpenFlows().openWorld("Native Recipe Validation", () -> { throw new AssertionError("Preview validation world did not load"); });
            return;
        }
        if (client.level == null || client.player == null || capturing) return;
        if (widget == null) {
            org.gtlcore.gtlcore.config.ConfigHolder.INSTANCE.multiblockPreview.minPositions = 1;
            com.gregtechceu.gtceu.config.ConfigHolder.INSTANCE.client.useVBO = true;
            open(client, false);
            return;
        }
        if (++ticks < 60 || stage < 4 && widget.getPreview().getPreviewControls().scene().getRenderer().isCompiling()) return;
        if (stage == 0) {
            checkScene();
            capture(client, "native-preview-ebf.png", () -> { stage = 1; ticks = 0; open(client, true); });
        } else if (stage == 1) {
            checkScene();
            var shapes = NativePreviewShapes.get(GTMultiMachines.DISTILLATION_TOWER.get());
            if (shapes.size() < 2) throw new AssertionError("Native expandable tower has no preview variants");
            click(client, 147, 39);
            capture(client, "native-preview-tower.png", () -> { stage = 2; ticks = 0; });
        } else if (stage == 2) {
            checkScene();
            click(client, 147, 19);
            if (!(client.screen instanceof FullscreenPreviewScreen) || !widget.getPreview().getPreviewControls().fullscreen())
                throw new AssertionError("Actual native screen click did not open fullscreen preview");
            stage = 3; ticks = 0;
        } else if (stage == 3) {
            checkScene();
            var scene = widget.getPreview().getPreviewControls().scene();
            var before = new org.joml.Vector3f(scene.getCenter());
            var screen = client.screen;
            if (!screen.mouseClicked(screen.width / 2.0, screen.height / 2.0, 2)) throw new AssertionError("Fullscreen rejected middle mouse pan");
            screen.mouseDragged(screen.width / 2.0, screen.height / 2.0, 2, 12, 8);
            screen.mouseReleased(screen.width / 2.0, screen.height / 2.0, 2);
            if (scene.getCenter().equals(before)) throw new AssertionError("Fullscreen camera did not pan");
            screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, 0, 1);
            capture(client, "native-preview-fullscreen.png", () -> {
                client.screen.onClose();
                if (client.screen != parent || widget.getPreview().getPreviewControls().fullscreen()
                        || widget.getPreview().getSizeWidth() != 160 || widget.getPreview().getSizeHeight() != 160)
                    throw new AssertionError("Fullscreen preview did not restore its native parent and geometry");
                stage = 4; ticks = 0; openJei(client);
            });
        } else if (stage == 4) {
            var screen = (JeiLayoutScreen) client.screen;
            var bridge = screen.bridge(); widget = bridge.getWidget();
            checkJeiMaterials(screen);
            widget.getPreview().setPage(1, null);
            stage = 5; ticks = 0;
        } else if (stage == 5) {
            var screen = (JeiLayoutScreen) client.screen;
            checkJeiMaterials(screen);
            if (widget.getPreview().getPageIndex() != 1) throw new AssertionError("Native JEI factory did not retain the requested page");
            capture(client, "native-preview-jei.png", () -> {
                widget.dispose(); client.setScreen(null); stage = 6; ticks = 0;
                var definition = GTMultiMachines.ELECTRIC_BLAST_FURNACE.get();
                var geometry = NativePreviewShapes.get(definition).getFirst();
                var blocks = new it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap<net.minecraft.world.level.block.state.BlockState>();
                geometry.states().forEach((pos, state) -> blocks.put(pos.asLong(), state));
                worldSchema = new com.gregtechceu.gtceu.client.mui.schema.MutableSchema(blocks);
                var ahead = client.player.position().add(client.player.getLookAngle().scale(6));
                worldOrigin = net.minecraft.core.BlockPos.containing(ahead).subtract(worldSchema.getControllerPos());
                com.gregtechceu.gtceu.client.renderer.PatternPreviewRenderer.INSTANCE.showPreviewCycleLevel(worldOrigin, worldSchema, 200);
                worldPositions = geometry.states().size();
            });
        } else if (stage == 6) {
            if (!WorldPreview.ready()) return;
            if (WorldPreview.positionCount() != worldPositions) throw new AssertionError("Native world ghost lost full-pattern blocks");
            capture(client, "native-preview-world.png", () -> {
                com.gregtechceu.gtceu.client.renderer.PatternPreviewRenderer.INSTANCE.showPreviewCycleLevel(worldOrigin, worldSchema, 80);
                if (WorldPreview.positionCount() >= worldPositions) throw new AssertionError("Native ghost activation did not cycle to one layer");
                stage = 7; ticks = 0;
            });
        } else if (stage == 7) {
            if (WorldPreview.active()) return;
            if (((org.gtlcore.gtlcore.mixin.gtm.client.WorldPreviewAccessor) com.gregtechceu.gtceu.client.renderer.PatternPreviewRenderer.INSTANCE).gtlcore$getNativeSchema() != null)
                throw new AssertionError("Expired world ghost retained its native schema");
            var definition = GTMultiMachines.ELECTRIC_BLAST_FURNACE.get();
            var original = NativePreviewShapes.get(definition).getFirst();
            var visual = new java.util.LinkedHashMap<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState>();
            original.states().forEach((pos, state) -> visual.put(pos, state.hasBlockEntity() ? state : net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState()));
            var custom = org.gtlcore.gtlcore.api.gui.preview.NativePreviewGeometry.fromStates(visual, original.controllerPos());
            NativePreviewShapes.register(definition, () -> java.util.List.of(custom));
            customPositions = visual.size();
            com.gregtechceu.gtceu.client.renderer.PatternPreviewRenderer.INSTANCE.showPreviewCycleLevel(worldOrigin, worldSchema, 200);
            if (WorldPreview.positionCount() != customPositions) throw new AssertionError("Registered custom shell lost blocks in its native world transform");
            stage = 8; ticks = 0;
        } else if (stage == 8) {
            if (!WorldPreview.ready()) return;
            var renderer = com.gregtechceu.gtceu.client.renderer.PatternPreviewRenderer.INSTANCE;
            var schema = ((org.gtlcore.gtlcore.mixin.gtm.client.WorldPreviewAccessor) renderer).gtlcore$getNativeSchema();
            long glass = schema.getBlocks().values().stream().filter(state -> state.is(net.minecraft.world.level.block.Blocks.GLASS)).count();
            if (glass == 0 || !schema.getControllerPos().equals(net.minecraft.core.BlockPos.ZERO)) throw new AssertionError("Native world preview did not use its registered controller-relative custom shell");
            capture(client, "native-preview-world-custom.png", () -> {
                if (stage != 8) return;
                stage = 9; ticks = 0;
                reload = client.reloadResourcePacks();
            });
        } else if (stage == 9) {
            if (!reload.isDone() || client.getOverlay() != null) return;
            reload.join();
            if (WorldPreview.active() || ((org.gtlcore.gtlcore.mixin.gtm.client.WorldPreviewAccessor)
                    com.gregtechceu.gtceu.client.renderer.PatternPreviewRenderer.INSTANCE).gtlcore$getNativeSchema() != null)
                throw new AssertionError("Native resource reload retained world preview buffers/schema");
            checkJeiRegistration();
            open(client, true); stage = 10; ticks = 0;
        } else if (stage == 10) {
            var renderer = widget.getPreview().getPreviewControls().scene().getRenderer();
            if (renderer.isCompiling()) return;
            checkScene();
            capture(client, "native-preview-reloaded.png", () -> {
                if (stage != 10) return;
                stage = 11;
                LogManager.getLogger().info("GTL_NATIVE_PREVIEW_CLIENT=PASS native_gt_definitions=2 page_click=true fullscreen_click=true pan=true restore=true meshdata=true jei_layout=true jei_live_materials=true world_ghost=true world_layer=true expiration=true custom_world_shell=true jei_registration=true resource_reload=true");
                client.setScreen(null); PreviewLifecycle.clear(); client.stop();
            });
        }
    }
    private static void open(Minecraft client, boolean tower) {
        if (widget != null) widget.dispose();
        widget = new NativePatternPreviewWidget(tower ? GTMultiMachines.DISTILLATION_TOWER.get() : GTMultiMachines.ELECTRIC_BLAST_FURNACE.get());
        var panel = new ModularPanel<>("gtl_native_preview_validation").size(180, 196).child(widget.left(10).top(24));
        var screen = new ModularScreen("gtlcore_recipe_test", panel);
        screen.getContext().setSettings(new brachy.modularui.screen.UISettings());
        parent = new ScreenWrapper(screen);
        client.setScreen(parent);
    }
    private static void checkScene() {
        var renderer = widget.getPreview().getPreviewControls().scene().getRenderer();
        if (!(renderer instanceof PreviewRendererAccess access) || access.gtlcore$previewKey() == null)
            throw new AssertionError("Production preview did not configure the native scene cache");
        if (renderer.renderedBlocksMap.size() != 1 || renderer.renderedBlocksMap.keySet().iterator().next().isEmpty())
            throw new AssertionError("Native pattern did not provide preview blocks");
    }
    private static void openJei(Minecraft client) {
        checkJeiRegistration();
        var runtime = brachy.modularui.integration.jei.ModularUIJeiPlugin.getRuntime();
        var category = runtime.getRecipeManager().getRecipeCategory(com.gregtechceu.gtceu.integration.recipeviewer.jei.MultiblockInfoJeiCategory.RECIPE_TYPE);
        var layout = runtime.getRecipeManager().createRecipeLayoutDrawable(category, GTMultiMachines.DISTILLATION_TOWER.get(),
                runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();
        client.setScreen(new JeiLayoutScreen(layout));
    }
    private static void checkJeiRegistration() {
        var manager = brachy.modularui.integration.jei.ModularUIJeiPlugin.getRuntime().getRecipeManager();
        int categories = 0, recipes = 0;
        for (var category : com.gregtechceu.gtceu.api.registry.GTRegistries.RECIPE_CATEGORIES) {
            if (!category.shouldRegisterDisplays()) continue;
            var recipeType = com.gregtechceu.gtceu.integration.recipeviewer.jei.recipe.GTRecipeJEICategory.TYPES.apply(category);
            if (manager.getRecipeCategory(recipeType) == null) throw new AssertionError("Native JEI category was lost: " + category.id);
            var expected = category.getRecipeType().getRecipesInCategory(category).stream()
                    .collect(java.util.stream.Collectors.groupingBy(com.gregtechceu.gtceu.api.recipe.GTRecipe::getId, java.util.stream.Collectors.counting()));
            var actual = manager.createRecipeLookup(recipeType).includeHidden().get()
                    .collect(java.util.stream.Collectors.groupingBy(com.gregtechceu.gtceu.api.recipe.GTRecipe::getId, java.util.stream.Collectors.counting()));
            if (!actual.equals(expected)) throw new AssertionError("Native JEI category lost recipes or representatives: " + category.id + " expected " + expected.size() + " registered " + actual.size());
            categories++; recipes += actual.values().stream().mapToInt(Long::intValue).sum();
        }
        var furnace = com.gregtechceu.gtceu.common.data.GTMachines.ELECTRIC_FURNACE[com.gregtechceu.gtceu.api.GTValues.LV].asStack();
        if (manager.createRecipeCatalystLookup(mezz.jei.api.constants.RecipeTypes.SMELTING).includeHidden().getItemStack()
                .noneMatch(stack -> net.minecraft.world.item.ItemStack.isSameItemSameComponents(stack, furnace)))
            throw new AssertionError("Native GT furnace lost its vanilla smelting catalyst link");
        LogManager.getLogger().info("GTL_NATIVE_JEI_REGISTRATION=PASS categories={} recipes={} all_category_ids=true representatives=true vanilla_smelting=true", categories, recipes);
    }
    private static void checkJeiMaterials(JeiLayoutScreen screen) {
        int checked = 0;
        for (var slot : widget.getPreview().getIngredientSlots()) {
            var bounds = PreviewMaterialHighlights.bounds(slot); if (bounds == null) continue;
            int x = screen.layout.getRect().getX() + widget.getArea().x + bounds.getX() + bounds.getWidth() / 2;
            int y = screen.layout.getRect().getY() + widget.getArea().y + bounds.getY() + bounds.getHeight() / 2;
            var drawable = screen.layout.getRecipeSlotUnderMouse(x, y).orElseThrow(() -> new AssertionError("Native JEI did not find a live material slot"));
            var stack = drawable.getDisplayedItemStack().orElseThrow(() -> new AssertionError("Native JEI material has no current ingredient"));
            if (!net.minecraft.world.item.ItemStack.isSameItemSameComponents(stack, slot.getItem()) || stack.getCount() != slot.getItem().getCount())
                throw new AssertionError("Native JEI lost material components or exact count");
            if (drawable.getAllIngredientsList().isEmpty()) throw new AssertionError("Native JEI material lookup has no alternatives");
            var page = NativePreviewShapes.get(GTMultiMachines.DISTILLATION_TOWER.get()).get(widget.getPreview().getPageIndex());
            long plainCount = page.states().values().stream().filter(state -> !state.hasBlockEntity() && state.getBlock().asItem() == stack.getItem()).count();
            if (plainCount > 0 && plainCount != stack.getCount()) throw new AssertionError("Native JEI material count differs from the actual page geometry: " + plainCount + " versus " + stack.getCount());
            checked++;
        }
        if (checked < 3) throw new AssertionError("Native JEI material strip was not populated");
    }
    private static final class JeiLayoutScreen extends net.minecraft.client.gui.screens.Screen {
        private final mezz.jei.api.gui.IRecipeLayoutDrawable<com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition> layout;
        private JeiLayoutScreen(mezz.jei.api.gui.IRecipeLayoutDrawable<com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition> layout) {
            super(net.minecraft.network.chat.Component.literal("Native JEI Preview Validation")); this.layout = layout;
        }
        @Override protected void init() { layout.setPosition((width - 160) / 2, (height - 160) / 2 + 12); }
        @Override public void tick() { layout.tick(); }
        @Override public void render(net.minecraft.client.gui.GuiGraphics graphics, int x, int y, float partialTicks) {
            graphics.fill(0, 0, width, height, 0xFF303336); layout.drawRecipe(graphics, x, y); layout.drawOverlays(graphics, x, y);
        }
        private org.gtlcore.gtlcore.integration.jei.NativePreviewJeiSlots bridge() {
            try {
                var field = layout.getClass().getDeclaredField("allWidgets"); field.setAccessible(true);
                org.gtlcore.gtlcore.integration.jei.NativePreviewJeiSlots bridge = null;
                brachy.modularui.screen.ModularScreen screen = null;
                for (var candidate : (java.util.List<?>) field.get(layout)) {
                    if (candidate instanceof org.gtlcore.gtlcore.integration.jei.NativePreviewJeiSlots result) bridge = result;
                    if (candidate instanceof brachy.modularui.integration.jei.recipe.ModularUIJeiCategory.UIWrapperWidget) {
                        var screenField = candidate.getClass().getDeclaredField("screen"); screenField.setAccessible(true);
                        screen = (brachy.modularui.screen.ModularScreen) screenField.get(candidate);
                    }
                }
                if (bridge != null && screen != null) {
                    if (brachy.modularui.widget.WidgetTree.findFirstWithName(screen.getMainPanel(), "gtlcore_native_pattern_preview") != bridge.getWidget())
                        throw new AssertionError("JEI renders a different preview than its live material bridge");
                    return bridge;
                }
            } catch (ReflectiveOperationException failure) { throw new AssertionError("Actual JEI layout could not be inspected", failure); }
            throw new AssertionError("Native GT JEI category did not use the production preview factory and live slot bridge");
        }
    }
    private static void click(Minecraft client, int x, int y) {
        var ui = (brachy.modularui.api.IMuiScreen) client.screen;
        var area = widget.getArea(); double mouseX = area.x + x, mouseY = area.y + y;
        ui.screen().getContext().updateState((int) mouseX, (int) mouseY, 0); ui.screen().onFrameUpdate();
        var screen = client.screen;
        boolean handled = net.neoforged.neoforge.client.ClientHooks.onScreenMouseClickedPre(screen, mouseX, mouseY, 0);
        if (!handled) handled = net.neoforged.neoforge.client.ClientHooks.onScreenMouseClickedPost(screen, mouseX, mouseY, 0, screen.mouseClicked(mouseX, mouseY, 0));
        if (!handled) throw new AssertionError("Native preview rejected mouse input at " + x + "," + y);
        if (!net.neoforged.neoforge.client.ClientHooks.onScreenMouseReleasedPre(screen, mouseX, mouseY, 0))
            net.neoforged.neoforge.client.ClientHooks.onScreenMouseReleasedPost(screen, mouseX, mouseY, 0, screen.mouseReleased(mouseX, mouseY, 0));
    }
    private static void capture(Minecraft client, String name, Runnable next) {
        capturing = true;
        Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), message -> client.execute(() -> {
            LogManager.getLogger().info("GTL_NATIVE_PREVIEW_SCREENSHOT {}", message.getString()); capturing = false; next.run();
        }));
    }
}
