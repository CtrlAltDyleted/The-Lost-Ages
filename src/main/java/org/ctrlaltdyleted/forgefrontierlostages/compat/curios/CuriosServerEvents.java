package org.ctrlaltdyleted.forgefrontierlostages.compat.curios;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.core.definitions.AEItems;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.me.helpers.ChannelPowerSrc;
import appeng.util.ConfigInventory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.FluidStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

/** One supplemental pass per wireless terminal tick; the card's normal inventory logic remains upstream. */
final class CuriosServerEvents {
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!CuriosIntegration.enabled()) return;
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide
                || !(event.player instanceof ServerPlayer player)) return;
        try {
            CuriosApi.getCuriosInventory(player).ifPresent(curios -> {
                for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                    ItemStack terminal = player.getInventory().getItem(slot);
                    if (terminal.getItem() instanceof WirelessTerminalItem wireless) {
                        transfer(player, curios, wireless, terminal, slot);
                    }
                }
            });
        } catch (LinkageError | RuntimeException error) {
            CuriosIntegration.disable(error);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "The Lost Ages Curios export integration was disabled; see the server log."), false);
        }
    }

    private void transfer(ServerPlayer player, ICuriosItemHandler curios,
                          WirelessTerminalItem wireless, ItemStack terminal, int terminalSlot) {
        CuriosSelections.Card card = CuriosSelections.find(terminal);
        if (card == null) return;
        var selections = CuriosSelections.read(card.stack());
        if (selections.isEmpty()) return;
        IGrid grid = wireless.getLinkedGrid(terminal, player.level(), player);
        if (grid == null) return;
        if (!(wireless.getMenuHost(player, terminalSlot, terminal, null) instanceof WirelessTerminalMenuHost host)
                || !host.rangeCheck()) return;
        IGridNode node = host.getActionableNode();
        if (node == null || !node.isActive()) return;
        MEStorage storage = grid.getStorageService().getInventory();
        ChannelPowerSrc power = new ChannelPowerSrc(node, grid.getEnergyService());
        IActionSource action = IActionSource.ofPlayer(player, host);
        ConfigInventory filters = ConfigInventory.configTypes(18, () -> {});
        if (card.stack().getTag() == null) return;
        filters.readFromChildTag(card.stack().getTag(), "filterConfig");
        IUpgradeInventory upgrades = appeng.api.upgrades.UpgradeInventories.forItem(card.stack(), 3, null);
        int limit = upgrades.isInstalled(AEItems.SPEED_CARD) ? 64000 : AEFluidKey.AMOUNT_BUCKET;
        for (CuriosSelections.Selection selected : selections) {
            ICurioStacksHandler handler = curios.getCurios().get(selected.type());
            if (handler == null || !handler.isVisible() || selected.index() >= handler.getSlots()) continue;
            ItemStack equipped = handler.getStacks().getStackInSlot(selected.index());
            if (equipped.isEmpty()) continue;
            AEKey key = filters.getKey(selected.filter());
            if (!(key instanceof AEFluidKey fluid)) continue;
            ItemStack copy = equipped.copy();
            IFluidHandlerItem target = copy.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
            if (target == null) continue;
            // Create Stuff Additions' handler overwrites its stored fluid on fill.
            // Reject mixed contents before asking it to simulate insertion.
            if (target.getTanks() == 1) {
                FluidStack stored = target.getFluidInTank(0);
                if (!stored.isEmpty() && !stored.isFluidEqual(fluid.toStack(1))) continue;
            }
            int accepted = target.fill(fluid.toStack(limit), IFluidHandler.FluidAction.SIMULATE);
            if (accepted <= 0) continue;
            long available = StorageHelper.poweredExtraction(power, storage, fluid, accepted, action, Actionable.SIMULATE);
            if (available <= 0) continue;
            long extracted = StorageHelper.poweredExtraction(power, storage, fluid, available, action, Actionable.MODULATE);
            if (extracted <= 0) continue;
            int filled = target.fill(fluid.toStack((int) extracted), IFluidHandler.FluidAction.EXECUTE);
            if (filled > 0) curios.setEquippedCurio(selected.type(), selected.index(), target.getContainer());
            if (filled < extracted) {
                storage.insert(fluid, extracted - filled, Actionable.MODULATE, action);
            }
        }
    }
}
