package org.ctrlaltdyleted.forgefrontierlostages.compat.curios;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.items.tools.powered.WirelessTerminalItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.ArrayList;
import java.util.List;

/** Independent export-card NBT; the card's existing inventory selection is untouched. */
public final class CuriosSelections {
    public static final String KEY = "lostAgesCuriosExportSlots";
    private static final ResourceLocation EXPORT_CARD = new ResourceLocation("ae2insertexportcard", "export_card");
    private CuriosSelections() {}

    public record Selection(String type, int index, int filter) {}
    public record Card(IUpgradeInventory inventory, int slot, ItemStack stack) {}

    public static Card find(ItemStack terminal) {
        if (!(terminal.getItem() instanceof WirelessTerminalItem wireless)) return null;
        IUpgradeInventory inventory = wireless.getUpgrades(terminal);
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (EXPORT_CARD.equals(ForgeRegistries.ITEMS.getKey(stack.getItem()))) {
                return new Card(inventory, i, stack);
            }
        }
        return null;
    }

    public static List<Selection> read(ItemStack card) {
        List<Selection> result = new ArrayList<>();
        CompoundTag tag = card.getTag();
        if (tag == null) return result;
        ListTag list = tag.getList(KEY, 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String type = entry.getString("type");
            int index = entry.getInt("index");
            int filter = entry.getInt("filter");
            if (!type.isEmpty() && type.length() <= 64 && index >= 0 && filter >= 0 && filter < 18) {
                result.add(new Selection(type, index, filter));
            }
        }
        return result;
    }

    public static void update(Card card, String type, int index, int filter) {
        ItemStack copy = card.stack().copy();
        List<Selection> values = read(copy);
        values.removeIf(value -> value.type().equals(type) && value.index() == index);
        if (filter >= 0) values.add(new Selection(type, index, filter));
        ListTag list = new ListTag();
        for (Selection value : values) {
            CompoundTag entry = new CompoundTag();
            entry.putString("type", value.type());
            entry.putInt("index", value.index());
            entry.putInt("filter", value.filter());
            list.add(entry);
        }
        copy.getOrCreateTag().put(KEY, list);
        card.inventory().setItemDirect(card.slot(), copy);
    }
}
