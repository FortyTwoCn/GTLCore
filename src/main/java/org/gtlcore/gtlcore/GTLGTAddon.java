package org.gtlcore.gtlcore;

import org.gtlcore.gtlcore.api.data.tag.GTLTagPrefix;
import org.gtlcore.gtlcore.api.registries.GTLRegistration;
import org.gtlcore.gtlcore.common.data.*;
import org.gtlcore.gtlcore.data.recipe.*;
import org.gtlcore.gtlcore.data.recipe.chemistry.MixerRecipes;
import org.gtlcore.gtlcore.data.recipe.processing.Lanthanidetreatment;
import org.gtlcore.gtlcore.data.recipe.processing.SkyTearsAndGregHeart;
import org.gtlcore.gtlcore.data.recipe.processing.StoneDustProcess;

import com.gregtechceu.gtceu.api.addon.GTAddon;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

@GTAddon(GTLCore.MOD_ID)
public class GTLGTAddon implements IGTAddon {

    public String addonModId() {
        return GTLCore.MOD_ID;
    }

    @Override
    public GTRegistrate getRegistrate() {
        return GTLRegistration.REGISTRATE;
    }

    @Override
    public boolean requiresHighTier() {
        return true;
    }

    public void initializeAddon() {
        GTLItems.init();
        GTLBlocks.init();
    }

    public void registerSounds() {
        GTLSoundEntries.init();
    }

    public void registerCovers() {
        GTLCovers.init();
    }

    public void registerElements() {
        GTLElements.init();
    }

    public void registerTagPrefixes() {
        GTLTagPrefix.init();
    }

    @Override
    public void addRecipes(RecipeOutput provider) {
        GCyMRecipes.init(provider);
        FuelRecipes.init(provider);
        MachineRecipe.init(provider);
        Misc.init(provider);
        ElementCopying.init(provider);
        StoneDustProcess.init(provider);
        Lanthanidetreatment.init(provider);
        CircuitRecipes.init(provider);
        MixerRecipes.init(provider);
        SkyTearsAndGregHeart.init(provider);
    }

    @Override
    public void removeRecipes(Consumer<ResourceLocation> consumer) {
        RemoveRecipe.init(consumer);
    }

}
