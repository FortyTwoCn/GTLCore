package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.client.renderer.item.StereoscopicItemRenderer;
import org.gtlcore.gtlcore.common.item.VirtualIngredientBehavior;
import com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialIconSet;
import com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialIconType;
import com.gregtechceu.gtceu.client.model.runtimegen.TagPrefixItemModelGenerator;
import com.lowdragmc.lowdraglib.client.renderer.IItemRendererProvider;
import com.lowdragmc.lowdraglib.client.model.ModelFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import org.apache.logging.log4j.LogManager;
import java.util.Set;

/** Draws the real production providers through native ItemRenderer/LDLib, then saves proof and exits. */
@EventBusSubscriber(modid = "gtlcore_recipe_test", value = Dist.CLIENT)
public final class NativeMaterialRendererClientFixtures {
    private static boolean opened;
    @SubscribeEvent public static void ready(ClientTickEvent.Post event) {
        var client = Minecraft.getInstance();
        if (!opened && Boolean.getBoolean("gtlcore.rendererValidation.enabled") && client.screen instanceof TitleScreen) {
            opened = true; verifyModelProtection(); client.setScreen(new RendererScreen());
        }
    }

    private static void verifyModelProtection() {
        try {
            var field = TagPrefixItemModelGenerator.class.getDeclaredField("MODELS"); field.setAccessible(true);
            var models = (Set<?>) field.get(null); int before = models.size();
            for (var icon : NativeMaterialRendererFixtures.SPECIAL)
                TagPrefixItemModelGenerator.add(NativeMaterialRendererFixtures.ROTATING_ITEM.get(), MaterialIconType.ingot, icon);
            if (models.size() != before) throw new AssertionError("A special packaged material model was overwritten by native runtime generation");
            TagPrefixItemModelGenerator.add(NativeMaterialRendererFixtures.ROTATING_ITEM.get(), MaterialIconType.ingot, MaterialIconSet.METALLIC);
            if (models.size() != before + 1) throw new AssertionError("Ordinary native material model generation was suppressed");
            var stack = NativeMaterialRendererFixtures.ROTATING_ITEM.asStack();
            var client = Minecraft.getInstance();
            var model = client.getItemRenderer().getItemModelShaper().getItemModel(stack.getItem());
            if (model == client.getModelManager().getMissingModel()) throw new AssertionError("The rotating fixture's vanilla item model is missing");
            if (((IItemRendererProvider) stack.getItem()).getRenderer(stack) != StereoscopicItemRenderer.INSTANCE)
                throw new AssertionError("The native material item's constructor did not attach its production renderer");
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static final class RendererScreen extends Screen {
        private final ItemStack[] items = { NativeMaterialRendererFixtures.ROTATING_ITEM.asStack(),
                VirtualIngredientBehavior.wrap(new ItemStack(Items.DIAMOND)),
                VirtualIngredientBehavior.wrap(new FluidStack(Fluids.WATER, 1000)), NativeAePatternFixtures.VIRTUAL.asStack() };
        private final String[] labels = { "Rotating material", "Virtual item", "Virtual fluid", "Unbound wrapper" };
        private int frames;
        private RendererScreen() { super(Component.literal("Native material renderer validation")); }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xff18202c);
            graphics.drawCenteredString(font, title, width / 2, 20, 0xffffffff);
            int start = width / 2 - 160;
            for (int i = 0; i < items.length; i++) {
                graphics.pose().pushPose();
                try { graphics.pose().translate(start + i * 86, height / 2 - 32, 0); graphics.pose().scale(4, 4, 1); graphics.renderItem(items[i], 0, 0); }
                finally { graphics.pose().popPose(); }
                if (IItemRendererProvider.disabled.get()) throw new AssertionError("Production rendering left the LDLib provider disabled");
                graphics.drawCenteredString(font, labels[i], start + i * 86 + 32, height / 2 + 42, 0xffdbe4f0);
            }
            graphics.flush();
            if (++frames == 60) {
                var frameId = ResourceLocation.fromNamespaceAndPath("gtlcore", "item/virtual_ingredient");
                if (!ModelFactory.getBlockSprite(frameId).contents().name().equals(frameId)) throw new AssertionError("Production virtual ingredient frame texture was not loaded");
                Screenshot.grab(minecraft.gameDirectory, "native-material-renderers.png", minecraft.getMainRenderTarget(), message -> {
                    LogManager.getLogger().info("GTL_NATIVE_MATERIAL_RENDERERS=PASS providers=true special_models=true item=true fluid=true unbound=true frames=60 screenshot={}", message.getString());
                    minecraft.execute(minecraft::stop);
                });
            }
        }
    }
}
