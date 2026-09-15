package org.ctrlaltdyleted.forgefrontierlostages.item;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

public class NonPlaceableBlockItem extends BlockItem
{
    public NonPlaceableBlockItem(Block block, Item.Properties properties)
    {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context)
    {
        return InteractionResult.FAIL;
    }
}
