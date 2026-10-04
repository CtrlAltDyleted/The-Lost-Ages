package org.ctrlaltdyleted.thelostages.compat.jei;

import appeng.api.integrations.jei.IngredientConverter;
import appeng.api.integrations.jei.IngredientConverters;
import appeng.api.stacks.GenericStack;
import com.mojang.logging.LogUtils;
import com.glodblock.github.appflux.client.render.FluxKeyRenderHandler;
import com.glodblock.github.appflux.common.me.key.FluxKey;
import com.glodblock.github.appflux.common.me.key.type.EnergyType;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.IModIngredientRegistration;
import mezz.jei.api.runtime.IEditModeConfig;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.List;

@JeiPlugin
public final class LostAgesJeiPlugin implements IModPlugin {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation UID = new ResourceLocation("thelostages", "applied_flux_fe");
    private static final ResourceLocation INFINITY_CELL = new ResourceLocation("expatternprovider", "infinity_cell");
    private static final ResourceLocation EXTENDED_INSCRIBER = new ResourceLocation("expatternprovider", "ex_inscriber");

    public LostAgesJeiPlugin() {
        if (ModList.get().isLoaded("appflux")) IngredientConverters.register(FeIngredient.CONVERTER);
    }

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerIngredients(IModIngredientRegistration registration) {
        if (!ModList.get().isLoaded("appflux")) return;
        registration.register(FeIngredient.TYPE, List.of(FeIngredient.KEY),
                FeIngredient.HELPER, FeIngredient.RENDERER);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        if (!ModList.get().isLoaded("expatternprovider")) return;
        hideUnconfiguredInfinityCell(runtime);
        showExtendedInscriber(runtime);
    }

    private static void hideUnconfiguredInfinityCell(IJeiRuntime runtime) {
        var ingredients = runtime.getIngredientManager();
        List<ItemStack> unconfigured = ingredients.getAllIngredients(VanillaTypes.ITEM_STACK).stream()
                .filter(stack -> INFINITY_CELL.equals(ForgeRegistries.ITEMS.getKey(stack.getItem())))
                .filter(stack -> !stack.hasTag())
                .toList();
        if (unconfigured.isEmpty()) return;
        ingredients.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, unconfigured);
        LOGGER.info("Removed {} unconfigured Infinity Cell entry from JEI; recorded variants remain visible",
                unconfigured.size());
    }

    private static void showExtendedInscriber(IJeiRuntime runtime) {
        var item = ForgeRegistries.ITEMS.getValue(EXTENDED_INSCRIBER);
        if (item == null) return;
        runtime.getIngredientManager().createTypedIngredient(VanillaTypes.ITEM_STACK, new ItemStack(item))
                .ifPresent(ingredient -> {
                    for (IEditModeConfig.HideMode mode : IEditModeConfig.HideMode.values()) {
                        runtime.getEditModeConfig().showIngredientUsingConfigFile(ingredient, mode);
                    }
                });
    }

    private static final class FeIngredient {
        private static final FluxKey KEY = FluxKey.of(EnergyType.FE);
        private static final IIngredientType<FluxKey> TYPE = new IIngredientType<>() {
            @Override
            public Class<? extends FluxKey> getIngredientClass() {
                return FluxKey.class;
            }

            @Override
            public String getUid() {
                return "thelostages:forge_energy";
            }
        };

        private static final IIngredientHelper<FluxKey> HELPER = new IIngredientHelper<>() {
            @Override
            public IIngredientType<FluxKey> getIngredientType() {
                return TYPE;
            }

            @Override
            public String getDisplayName(FluxKey ingredient) {
                return "Forge Energy (FE)";
            }

            @Override
            public String getUniqueId(FluxKey ingredient, UidContext context) {
                return "appflux:fe";
            }

            @Override
            public ResourceLocation getResourceLocation(FluxKey ingredient) {
                return EnergyType.FE.id();
            }

            @Override
            public FluxKey copyIngredient(FluxKey ingredient) {
                return KEY;
            }

            @Override
            public FluxKey normalizeIngredient(FluxKey ingredient) {
                return KEY;
            }

            @Override
            public boolean isValidIngredient(FluxKey ingredient) {
                return ingredient != null && ingredient.getEnergyType() == EnergyType.FE;
            }

            @Override
            public String getErrorInfo(FluxKey ingredient) {
                return ingredient == null ? "null Forge Energy ingredient" : "Applied Flux key: " + ingredient;
            }
        };

        private static final IIngredientRenderer<FluxKey> RENDERER = new IIngredientRenderer<>() {
            @Override
            public void render(GuiGraphics graphics, FluxKey ingredient) {
                FluxKeyRenderHandler.INSTANCE.drawInGui(Minecraft.getInstance(), graphics, 0, 0, ingredient);
            }

            @Override
            public List<Component> getTooltip(FluxKey ingredient, TooltipFlag flag) {
                return List.of(FluxKeyRenderHandler.INSTANCE.getDisplayName(ingredient),
                        Component.literal("Forge Energy (FE)"), Component.literal("Applied Flux"));
            }
        };

        private static final IngredientConverter<FluxKey> CONVERTER = new IngredientConverter<>() {
            @Override
            public IIngredientType<FluxKey> getIngredientType() {
                return TYPE;
            }

            @Override
            public FluxKey getIngredientFromStack(GenericStack stack) {
                return stack != null && stack.what() instanceof FluxKey key
                        && key.getEnergyType() == EnergyType.FE ? KEY : null;
            }

            @Override
            public GenericStack getStackFromIngredient(FluxKey ingredient) {
                return ingredient != null && ingredient.getEnergyType() == EnergyType.FE
                        ? new GenericStack(KEY, 1) : null;
            }
        };
    }
}
