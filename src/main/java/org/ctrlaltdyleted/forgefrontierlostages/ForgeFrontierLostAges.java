package org.ctrlaltdyleted.forgefrontierlostages;

import com.mojang.logging.LogUtils;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.ctrlaltdyleted.forgefrontierlostages.config.Ae2ClientConfigPatcher;
import org.ctrlaltdyleted.forgefrontierlostages.config.JeiBlacklistManager;
import org.ctrlaltdyleted.forgefrontierlostages.config.LogBegoneConfigPatcher;
import org.ctrlaltdyleted.forgefrontierlostages.config.ResourceVentsConfigPatcher;
import org.ctrlaltdyleted.forgefrontierlostages.install.KubeJsScriptManager;
import org.ctrlaltdyleted.forgefrontierlostages.install.ManagedFileInstaller;
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

    public static final RegistryObject<Item> CERTUSITE_ITEM = ITEMS.register("certusite", () -> new BlockItem(CERTUSITE.get(), new Item.Properties()));
    public static final RegistryObject<Item> SKYSTONIUM_ITEM = ITEMS.register("skystonium", () -> new BlockItem(SKYSTONIUM.get(), new Item.Properties()));

    public ForgeFrontierLostAges()
    {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);

        JeiBlacklistManager.removeAutoTraderBlacklistEntry();
        ResourceVentsConfigPatcher.patch();
        LogBegoneConfigPatcher.patch();
        KubeJsScriptManager.patchExternalCompactingRecipes();
        ManagedFileInstaller.install();
        QuestMetadataPatcher.patch();
        AppliedEnergisticsQuestPatcher.patch();
        Ae2ClientConfigPatcher.patch();
        LOGGER.info("Initializing The Lost Ages");
    }
}
