package org.ctrlaltdyleted.forgefrontierlostages.compat.jei;

import com.mojang.logging.LogUtils;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.List;

/** Hides only the unconfigured cell, which ExtendedAE labels as water by fallback. */
@JeiPlugin
public final class InfinityCellJeiPlugin implements IModPlugin {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation UID = new ResourceLocation("forgefrontierlostages", "infinity_cell_jei");
    private static final ResourceLocation CELL = new ResourceLocation("expatternprovider", "infinity_cell");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        if (!ModList.get().isLoaded("expatternprovider")) return;
        var ingredients = runtime.getIngredientManager();
        List<ItemStack> unconfigured = ingredients.getAllIngredients(VanillaTypes.ITEM_STACK).stream()
                .filter(stack -> CELL.equals(ForgeRegistries.ITEMS.getKey(stack.getItem())))
                .filter(stack -> !stack.hasTag())
                .toList();
        if (unconfigured.isEmpty()) return;
        ingredients.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, unconfigured);
        LOGGER.info("Removed {} unconfigured Infinity Cell entry from JEI; recorded variants remain visible",
                unconfigured.size());
    }
}
