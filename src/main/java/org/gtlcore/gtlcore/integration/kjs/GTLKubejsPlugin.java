package org.gtlcore.gtlcore.integration.kjs;

import org.gtlcore.gtlcore.api.data.chemical.material.info.GTLMaterialFlags;
import org.gtlcore.gtlcore.api.data.chemical.material.info.GTLMaterialIconSet;
import org.gtlcore.gtlcore.api.item.tool.GTLToolType;
import org.gtlcore.gtlcore.api.machine.multiblock.GTLCleanroomType;
import org.gtlcore.gtlcore.common.data.*;
import org.gtlcore.gtlcore.common.recipe.condition.GravityCondition;
import org.gtlcore.gtlcore.utils.Registries;
import org.gtlcore.gtlcore.utils.TextUtil;

import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.plugin.ClassFilter;

public class GTLKubejsPlugin implements KubeJSPlugin {

    @Override
    public void registerClasses(ClassFilter filter) {
        filter.allow("org.gtlcore.gtlcore");
    }

    @Override
    public void registerBindings(BindingRegistry event) {

        event.add("GTLMaterials", GTLMaterials.class);
        event.add("GTLBlocks", GTLBlocks.class);
        event.add("GTLItems", GTLItems.class);
        event.add("GTLMachines", GTLMachines.class);
        event.add("GTLRecipeTypes", GTLRecipeTypes.class);
        event.add("GTLRecipeModifiers", GTLRecipeModifiers.class);
        event.add("GTLCleanroomType", GTLCleanroomType.class);
        event.add("GTLToolType", GTLToolType.class);
        event.add("GTLMaterialIconSet", GTLMaterialIconSet.class);
        event.add("GTLMaterialFlags", GTLMaterialFlags.class);
        event.add("TextUtil", TextUtil.class);
        event.add("Registries", Registries.class);
        event.add("GravityCondition", GravityCondition.class);
    }
}
