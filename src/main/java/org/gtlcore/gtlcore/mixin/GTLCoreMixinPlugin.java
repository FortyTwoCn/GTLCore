package org.gtlcore.gtlcore.mixin;

import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import java.util.List;
import java.util.Set;

/** Select optional integrations before Mixin resolves references to their absent classes. */
public final class GTLCoreMixinPlugin implements IMixinConfigPlugin {
    private static boolean loaded(String mod) {
        return FMLLoader.getLoadingModList().getModFileById(mod) != null;
    }

    @Override public boolean shouldApplyMixin(String target, String mixin) {
        if (target.startsWith("dev.emi.")) return loaded("emi");
        if (target.startsWith("com.neuvillette.ae2ct.")) return loaded("ae2ct");
        if (target.startsWith("dev.ftb.mods.ftbchunks.")
                || mixin.endsWith(".ProspectorScannerBehaviorMixin")
                || mixin.endsWith(".ProspectingTextureMixin")
                || mixin.endsWith(".ProspectingMapWidgetMixin")) return loaded("ftbchunks");
        if (target.startsWith("dev.ftb.mods.ftbxmodcompat.")) return loaded("ftbxmodcompat");
        if (target.startsWith("com.buuz135.functionalstorage.")) return loaded("functionalstorage");
        return true;
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> own, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
