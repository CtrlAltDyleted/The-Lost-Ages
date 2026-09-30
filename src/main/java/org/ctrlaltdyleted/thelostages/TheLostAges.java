package org.ctrlaltdyleted.thelostages;

import com.mojang.logging.LogUtils;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.ctrlaltdyleted.thelostages.config.Ae2ClientConfigPatcher;
import org.ctrlaltdyleted.thelostages.config.ExtendedAeInfinityConfigPatcher;
import org.ctrlaltdyleted.thelostages.compat.curios.CuriosIntegration;
import org.ctrlaltdyleted.thelostages.config.JeiBlacklistManager;
import org.ctrlaltdyleted.thelostages.config.LogBegoneConfigPatcher;
import org.ctrlaltdyleted.thelostages.config.ResourceVentsConfigPatcher;
import org.ctrlaltdyleted.thelostages.install.KubeJsScriptManager;
import org.ctrlaltdyleted.thelostages.install.ManagedFileInstaller;
import org.ctrlaltdyleted.thelostages.item.NonPlaceableBlockItem;
import org.ctrlaltdyleted.thelostages.compat.LegacyRegistryRemapper;
import org.ctrlaltdyleted.thelostages.quests.AppliedEnergisticsQuestPatcher;
import org.ctrlaltdyleted.thelostages.quests.QuestMetadataPatcher;
import org.slf4j.Logger;

@Mod(TheLostAges.MOD_ID)
public class TheLostAges
{
    public static final String MOD_ID = "thelostages";
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
                    .noLootTable()
                    .strength(2.0F, 3.0F)
                    .sound(SoundType.STONE)
    ));
    public static final RegistryObject<Block> INCOMPLETE_ACTIVE_SKYSTONIUM_VENT = BLOCKS.register("incomplete_active_skystonium_vent", () -> new Block(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .noLootTable()
                    .strength(2.0F, 3.0F)
                    .sound(SoundType.STONE)
    ));

    public static final RegistryObject<Item> CERTUSITE_ITEM = ITEMS.register("certusite", () -> new BlockItem(CERTUSITE.get(), new Item.Properties()));
    public static final RegistryObject<Item> SKYSTONIUM_ITEM = ITEMS.register("skystonium", () -> new BlockItem(SKYSTONIUM.get(), new Item.Properties()));
    public static final RegistryObject<Item> INCOMPLETE_ACTIVE_CERTUSITE_VENT_ITEM = ITEMS.register("incomplete_active_certusite_vent", () -> new NonPlaceableBlockItem(INCOMPLETE_ACTIVE_CERTUSITE_VENT.get(), new Item.Properties()));
    public static final RegistryObject<Item> INCOMPLETE_ACTIVE_SKYSTONIUM_VENT_ITEM = ITEMS.register("incomplete_active_skystonium_vent", () -> new NonPlaceableBlockItem(INCOMPLETE_ACTIVE_SKYSTONIUM_VENT.get(), new Item.Properties()));
    public static final RegistryObject<Item> INCOMPLETE_UNIVERSAL_PIPE = ITEMS.register("incomplete_universal_pipe", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> INCOMPLETE_BASIC_UPGRADE = ITEMS.register("incomplete_basic_upgrade", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> INCOMPLETE_IMPROVED_UPGRADE = ITEMS.register("incomplete_improved_upgrade", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> INCOMPLETE_ADVANCED_UPGRADE = ITEMS.register("incomplete_advanced_upgrade", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> INCOMPLETE_ULTIMATE_UPGRADE = ITEMS.register("incomplete_ultimate_upgrade", () -> new Item(new Item.Properties()));

    public TheLostAges(FMLJavaModLoadingContext context)
    {
        IEventBus modEventBus = context.getModEventBus();
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        MinecraftForge.EVENT_BUS.addListener(LegacyRegistryRemapper::onMissingMappings);

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
