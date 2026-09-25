package org.ctrlaltdyleted.forgefrontierlostages.compat.curios;

import appeng.api.config.Actionable;
import appeng.api.config.PowerUnits;
import appeng.api.implementations.items.IAEItemPowerStorage;
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
import com.glodblock.github.appflux.common.me.key.FluxKey;
import com.glodblock.github.appflux.common.me.key.type.EnergyType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.FluidStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

final class CuriosServerEvents {
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!CuriosIntegration.enabled()) return;
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide
                || !(event.player instanceof ServerPlayer player)) return;
        try {
            CuriosApi.getCuriosInventory(player).ifPresent(curios -> {
                Set<ItemStack> visited = Collections.newSetFromMap(new IdentityHashMap<>());
                for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                    ItemStack terminal = player.getInventory().getItem(slot);
                    if (terminal.getItem() instanceof WirelessTerminalItem wireless && visited.add(terminal)) {
                        transfer(player, curios, wireless, terminal, slot);
                    }
                }
                ICurioStacksHandler terminals = curios.getCurios().get("terminals");
                if (terminals != null) {
                    for (int slot = 0; slot < terminals.getSlots(); slot++) {
                        ItemStack terminal = terminals.getStacks().getStackInSlot(slot);
                        if (terminal.getItem() instanceof WirelessTerminalItem wireless && visited.add(terminal)) {
                            wireless.inventoryTick(terminal, player.level(), player, -1, false);
                            transfer(player, curios, wireless, terminal, -1);
                        }
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
        int[] inventorySelections = card.stack().getTag() == null ? new int[0]
                : card.stack().getTag().getIntArray("SelectedInventorySlots");
        if (selections.isEmpty() && inventorySelections.length == 0) return;
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
        boolean changed = false;
        for (int slot = 0; slot < inventorySelections.length && slot < player.getInventory().getContainerSize(); slot++) {
            int filter = inventorySelections[slot] - 1;
            if (filter < 0 || filter >= filters.size()) continue;
            AEKey key = filters.getKey(filter);
            if (!isFe(key)) continue;
            ItemStack targetStack = player.getInventory().getItem(slot);
            if (targetStack.isEmpty()) continue;
            changed |= charge(targetStack, key, limit, power, storage, action);
        }
        for (CuriosSelections.Selection selected : selections) {
            ICurioStacksHandler handler = curios.getCurios().get(selected.type());
            if (handler == null || !handler.isVisible() || selected.index() >= handler.getSlots()) continue;
            ItemStack targetStack = handler.getStacks().getStackInSlot(selected.index());
            if (targetStack.isEmpty()) continue;
            AEKey key = filters.getKey(selected.filter());
            if (isFe(key)) {
                ItemStack charged = targetStack == terminal ? targetStack : targetStack.copy();
                if (charge(charged, key, limit, power, storage, action)) {
                    curios.setEquippedCurio(selected.type(), selected.index(), charged);
                    changed = true;
                }
                continue;
            }
            if (!(key instanceof AEFluidKey fluid)) continue;
            if (targetStack == terminal) continue;
            ItemStack copy = targetStack.copy();
            IFluidHandlerItem target = copy.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
            if (target == null) continue;
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
            if (filled > 0) {
                curios.setEquippedCurio(selected.type(), selected.index(), target.getContainer());
                changed = true;
            }
            if (filled < extracted) {
                storage.insert(fluid, extracted - filled, Actionable.MODULATE, action);
            }
        }
        if (changed) player.containerMenu.broadcastChanges();
    }

    private static boolean isFe(AEKey key) {
        return ModList.get().isLoaded("appflux") && key instanceof FluxKey flux
                && flux.getEnergyType() == EnergyType.FE;
    }

    private static boolean charge(ItemStack stack, AEKey key, int limit, ChannelPowerSrc power,
                                  MEStorage storage, IActionSource action) {
        EnergyTransfer.Target target;
        if (stack.getItem() instanceof IAEItemPowerStorage aeTarget
                && aeTarget.getPowerFlow(stack).isAllowInsertion()) {
            target = new AeEnergyTarget(new AeEnergyTarget.Storage() {
                @Override
                public double maxPower() { return aeTarget.getAEMaxPower(stack); }

                @Override
                public double currentPower() { return aeTarget.getAECurrentPower(stack); }

                @Override
                public double chargeRate() { return aeTarget.getChargeRate(stack); }

                @Override
                public double inject(double ae, boolean simulate) {
                    return aeTarget.injectAEPower(stack, ae,
                            simulate ? Actionable.SIMULATE : Actionable.MODULATE);
                }
            }, PowerUnits.FE.convertTo(PowerUnits.AE, 1));
        } else {
            IEnergyStorage feTarget = stack.getCapability(ForgeCapabilities.ENERGY).orElse(null);
            if (feTarget == null || !feTarget.canReceive()) return false;
            target = feTarget::receiveEnergy;
        }
        return EnergyTransfer.charge(limit, new EnergyTransfer.Source() {
            @Override
            public long extract(int amount, boolean simulate) {
                return StorageHelper.poweredExtraction(power, storage, key, amount, action,
                        simulate ? Actionable.SIMULATE : Actionable.MODULATE);
            }

            @Override
            public void refund(long amount) {
                storage.insert(key, amount, Actionable.MODULATE, action);
            }
        }, target) > 0;
    }

}
