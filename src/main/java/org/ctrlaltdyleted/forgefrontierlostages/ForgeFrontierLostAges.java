package org.ctrlaltdyleted.forgefrontierlostages;

import com.mojang.logging.LogUtils;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.ctrlaltdyleted.forgefrontierlostages.config.Ae2ClientConfigPatcher;
import org.ctrlaltdyleted.forgefrontierlostages.config.ExtendedAeInfinityConfigPatcher;
import org.ctrlaltdyleted.forgefrontierlostages.compat.curios.CuriosIntegration;
import org.ctrlaltdyleted.forgefrontierlostages.config.JeiBlacklistManager;
import org.ctrlaltdyleted.forgefrontierlostages.config.LogBegoneConfigPatcher;
import org.ctrlaltdyleted.forgefrontierlostages.config.ResourceVentsConfigPatcher;
import org.ctrlaltdyleted.forgefrontierlostages.install.KubeJsScriptManager;
import org.ctrlaltdyleted.forgefrontierlostages.install.ManagedFileInstaller;
import org.ctrlaltdyleted.forgefrontierlostages.item.NonPlaceableBlockItem;
import org.ctrlaltdyleted.forgefrontierlostages.quests.AppliedEnergisticsQuestPatcher;
import org.ctrlaltdyleted.forgefrontierlostages.quests.QuestMetadataPatcher;
import org.slf4j.Logger;

@Mod(ForgeFrontierLostAges.MOD_ID)
public class ForgeFrontierLostAges
{
    public static final String MOD_ID = "forgefrontierlostages";
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);

    public static final RegistryObject<Block> CERTUSITE = BLOCKS.register("certusite", () -> new Block(
            BlockBehaviour.Properties.of()
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.STONE)
    ));
    public static final RegistryObject<Block> SKYSTONIUM = BLOCKS.register("skystonium", () -> new Block(
            BlockBehaviour.Properties.of()
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.STONE)
    ));
    public static final RegistryObject<Block> INCOMPLETE_ACTIVE_CERTUSITE_VENT = BLOCKS.register("incomplete_active_certusite_vent", () -> new Block(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .requiresCorrectToolForDrops()
                    .strength(2.0F, 3.0F)
                    .sound(SoundType.STONE)
    ));
    public static final RegistryObject<Block> INCOMPLETE_ACTIVE_SKYSTONIUM_VENT = BLOCKS.register("incomplete_active_skystonium_vent", () -> new Block(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .requiresCorrectToolForDrops()
                    .strength(2.0F, 3.0F)
                    .sound(SoundType.STONE)
    ));

    public static final RegistryObject<Item> CERTUSITE_ITEM = ITEMS.register("certusite", () -> new BlockItem(CERTUSITE.get(), new Item.Properties()));
    public static final RegistryObject<Item> SKYSTONIUM_ITEM = ITEMS.register("skystonium", () -> new BlockItem(SKYSTONIUM.get(), new Item.Properties()));
    public static final RegistryObject<Item> INCOMPLETE_ACTIVE_CERTUSITE_VENT_ITEM = ITEMS.register("incomplete_active_certusite_vent", () -> new NonPlaceableBlockItem(INCOMPLETE_ACTIVE_CERTUSITE_VENT.get(), new Item.Properties()));
    public static final RegistryObject<Item> INCOMPLETE_ACTIVE_SKYSTONIUM_VENT_ITEM = ITEMS.register("incomplete_active_skystonium_vent", () -> new NonPlaceableBlockItem(INCOMPLETE_ACTIVE_SKYSTONIUM_VENT.get(), new Item.Properties()));

    public ForgeFrontierLostAges(FMLJavaModLoadingContext context)
    {
        IEventBus modEventBus = context.getModEventBus();
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);

        JeiBlacklistManager.removeAutoTraderBlacklistEntry();
        ResourceVentsConfigPatcher.patch();
        LogBegoneConfigPatcher.patch();
        KubeJsScriptManager.patchExternalCompactingRecipes();
        KubeJsScriptManager.restoreInfinityCobblestoneRecipe();
        ExtendedAeInfinityConfigPatcher.ensureLavaType();
        ManagedFileInstaller.install();
        QuestMetadataPatcher.patch();
        AppliedEnergisticsQuestPatcher.patch();
        Ae2ClientConfigPatcher.patch();
        CuriosIntegration.initialize();
        LOGGER.info("Initializing The Lost Ages");
    }
}
