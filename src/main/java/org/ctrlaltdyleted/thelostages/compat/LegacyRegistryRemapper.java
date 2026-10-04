package org.ctrlaltdyleted.thelostages.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.MissingMappingsEvent;
import org.ctrlaltdyleted.thelostages.TheLostAges;

public final class LegacyRegistryRemapper {
    private static final String OLD_NAMESPACE = "forgefrontierlostages";

    private LegacyRegistryRemapper() {}

    public static void onMissingMappings(MissingMappingsEvent event) {
        for (MissingMappingsEvent.Mapping<Block> mapping :
                event.getMappings(ForgeRegistries.Keys.BLOCKS, OLD_NAMESPACE)) {
            ResourceLocation replacement = new ResourceLocation(TheLostAges.MOD_ID, mapping.getKey().getPath());
            if (ForgeRegistries.BLOCKS.containsKey(replacement)) {
                mapping.remap(ForgeRegistries.BLOCKS.getValue(replacement));
            }
        }

        for (MissingMappingsEvent.Mapping<Item> mapping :
                event.getMappings(ForgeRegistries.Keys.ITEMS, OLD_NAMESPACE)) {
            ResourceLocation replacement = new ResourceLocation(TheLostAges.MOD_ID, mapping.getKey().getPath());
            if (ForgeRegistries.ITEMS.containsKey(replacement)) {
                mapping.remap(ForgeRegistries.ITEMS.getValue(replacement));
            }
        }
    }
}
