package org.ctrlaltdyleted.thelostages.compat;

import appeng.recipes.handlers.InscriberRecipe;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import org.ctrlaltdyleted.thelostages.TheLostAges;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mod.EventBusSubscriber(modid = TheLostAges.MOD_ID)
public final class InscriberRecipeIntegration {
    private static final ResourceLocation ASSEMBLY = new ResourceLocation(TheLostAges.MOD_ID, "sequenced_assembly/inscriber");
    private static final ResourceLocation ORIGINAL_CRAFT = new ResourceLocation("ae2", "network/blocks/inscribers");
    private static final List<ResourceLocation> PRESS_COPIES = List.of(
            new ResourceLocation(TheLostAges.MOD_ID, "inscriber/advanced_ae/quantum_processor_press_duplicate"),
            new ResourceLocation(TheLostAges.MOD_ID, "inscriber/megacells/accumulation_processor_press_duplicate"));

    private InscriberRecipeIntegration() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStarted(ServerStartedEvent event) {
        reconcile(event.getServer());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        var server = event.getPlayerList().getServer();
        reconcile(server);
    }

    private static void reconcile(MinecraftServer server) {
        RecipeManager manager = server.getRecipeManager();
        var registries = server.registryAccess();
        List<Recipe<?>> recipes = new ArrayList<>(manager.getRecipes());
        boolean changed = false;
        for (ResourceLocation id : PRESS_COPIES) {
            if (manager.byKey(id).isPresent()) continue;
            ResourceLocation resource = new ResourceLocation(id.getNamespace(), "recipes/" + id.getPath() + ".json");
            try (var reader = server.getResourceManager().getResourceOrThrow(resource).openAsReader()) {
                Recipe<?> recipe = RecipeManager.fromJson(id, JsonParser.parseReader(reader).getAsJsonObject());
                boolean equivalent = recipes.stream().anyMatch(existing -> sameInscriberOperation(existing, recipe, registries));
                if (!equivalent) {
                    recipes.add(recipe);
                    changed = true;
                }
            } catch (java.io.IOException | RuntimeException exception) {
                throw new IllegalStateException("Could not restore Inscriber press-copy recipe " + id, exception);
            }
        }
        Set<ResourceLocation> redundant = new HashSet<>();
        if (manager.byKey(ASSEMBLY).isPresent()) {
            manager.byKey(ORIGINAL_CRAFT).ifPresent(recipe -> {
                if (recipe.getType() == net.minecraft.world.item.crafting.RecipeType.CRAFTING) {
                    redundant.add(ORIGINAL_CRAFT);
                }
            });
        }
        if (changed || !redundant.isEmpty()) {
            manager.replaceRecipes(recipes.stream()
                    .filter(recipe -> !redundant.contains(recipe.getId())).toList());
        }
    }

    private static boolean sameInscriberOperation(Recipe<?> first, Recipe<?> second,
                                                  net.minecraft.core.RegistryAccess registries) {
        return first instanceof InscriberRecipe a && second instanceof InscriberRecipe b
                && a.getProcessType() == b.getProcessType()
                && ItemStack.matches(a.getResultItem(registries), b.getResultItem(registries))
                && a.getTopOptional().toJson().equals(b.getTopOptional().toJson())
                && a.getMiddleInput().toJson().equals(b.getMiddleInput().toJson())
                && a.getBottomOptional().toJson().equals(b.getBottomOptional().toJson());
    }
}
