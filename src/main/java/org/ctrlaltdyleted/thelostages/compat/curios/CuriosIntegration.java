package org.ctrlaltdyleted.thelostages.compat.curios;

import com.mojang.logging.LogUtils;
import com.ultramega.ae2insertexportcard.container.UpgradeContainerMenu;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.slf4j.Logger;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

public final class CuriosIntegration {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile boolean enabled;
    private CuriosIntegration() {}

    public static void initialize() {
        if (!ModList.get().isLoaded("ae2") || !ModList.get().isLoaded("curios")
                || !ModList.get().isLoaded("ae2insertexportcard")) {
            LOGGER.info("AE2 Export Card Curios integration inactive: optional mods are absent");
            return;
        }
        try {
            verify("com.ultramega.ae2insertexportcard.container.UpgradeContainerMenu", "getUpgradeHost");
            verify("com.ultramega.ae2insertexportcard.item.UpgradeHost", "getSelectedInventorySlots");
            verify("appeng.items.tools.powered.WirelessTerminalItem", "getLinkedGrid",
                    net.minecraft.world.item.ItemStack.class, net.minecraft.world.level.Level.class,
                    net.minecraft.world.entity.player.Player.class);
            verify("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler", "getCurios");
            verify("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler", "setEquippedCurio",
                    String.class, int.class, net.minecraft.world.item.ItemStack.class);
            verify("top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler", "isVisible");
            verify("appeng.api.storage.StorageHelper", "poweredExtraction",
                    appeng.api.networking.energy.IEnergySource.class, appeng.api.storage.MEStorage.class,
                    appeng.api.stacks.AEKey.class, long.class,
                    appeng.api.networking.security.IActionSource.class, appeng.api.config.Actionable.class);
            if (FMLEnvironment.dist == Dist.CLIENT) {
                verify("com.ultramega.ae2insertexportcard.screen.UpgradeScreen", "drawBG",
                        net.minecraft.client.gui.GuiGraphics.class, int.class, int.class, int.class, int.class, float.class);
            }
            CuriosNetwork.initialize();
            MinecraftForge.EVENT_BUS.register(new ExportCardTransfer());
            if (FMLEnvironment.dist == Dist.CLIENT) {
                CuriosExportScreenEvents.initialize();
            }
            enabled = true;
            LOGGER.info("AE2 Export Card Curios integration enabled; verified API baseline: AE2 15.4.10, Curios 5.14.1, card 1.3.0");
        } catch (LinkageError | ReflectiveOperationException | RuntimeException error) {
            LOGGER.error("AE2 Export Card Curios integration disabled: incompatible optional implementation", error);
        }
    }

    static boolean enabled() { return enabled; }

    static void disable(Throwable error) {
        if (!enabled) return;
        enabled = false;
        LOGGER.error("AE2 Export Card Curios integration disabled after optional API failure", error);
    }

    private static void verify(String className, String methodName, Class<?>... parameters)
            throws ReflectiveOperationException {
        Class.forName(className, false, CuriosIntegration.class.getClassLoader())
                .getMethod(methodName, parameters);
    }
}

final class CuriosNetwork {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("thelostages", "curios_export"),
            () -> PROTOCOL, CuriosNetwork::acceptVersion, CuriosNetwork::acceptVersion);
    private CuriosNetwork() {}

    private static boolean acceptVersion(String version) {
        return PROTOCOL.equals(version) || NetworkRegistry.ABSENT.equals(version)
                || NetworkRegistry.ACCEPTVANILLA.equals(version);
    }

    static void initialize() {
        CHANNEL.messageBuilder(SelectionMessage.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SelectionMessage::encode).decoder(SelectionMessage::decode)
                .consumerMainThread(SelectionMessage::handle).add();
    }

    static void send(int containerId, String type, int index, int filter) {
        CHANNEL.sendToServer(new SelectionMessage(containerId, type, index, filter));
    }

    private record SelectionMessage(int containerId, String type, int index, int filter) {
        void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(containerId);
            buffer.writeUtf(type, 64);
            buffer.writeVarInt(index);
            buffer.writeVarInt(filter);
        }

        static SelectionMessage decode(FriendlyByteBuf buffer) {
            return new SelectionMessage(buffer.readVarInt(), buffer.readUtf(64),
                    buffer.readVarInt(), buffer.readVarInt());
        }

        static void handle(SelectionMessage message, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            if (!CuriosIntegration.enabled()) {
                if (context.getSender() != null) context.getSender().displayClientMessage(
                        net.minecraft.network.chat.Component.literal(
                                "The Lost Ages Curios export integration is disabled; see the server log."), false);
                context.setPacketHandled(true);
                return;
            }
            ServerPlayer player = context.getSender();
            if (player == null || !(player.containerMenu instanceof UpgradeContainerMenu menu)
                    || menu.containerId != message.containerId || menu.getType() != UpgradeContainerMenu.TYPE_EXPORT
                    || message.type.isEmpty() || message.type.length() > 64 || message.index < 0
                    || message.filter < -1 || message.filter >= 18) {
                context.setPacketHandled(true);
                return;
            }
            CuriosApi.getCuriosInventory(player).ifPresent(curios -> {
                ICurioStacksHandler handler = curios.getCurios().get(message.type);
                if (handler == null || !handler.isVisible() || message.index >= handler.getSlots()) return;
                ItemStack terminal = menu.getHost() instanceof appeng.helpers.WirelessTerminalMenuHost host
                        ? host.getItemStack() : ItemStack.EMPTY;
                CuriosSelections.Card card = CuriosSelections.find(terminal);
                if (card != null) CuriosSelections.update(card, message.type, message.index, message.filter);
            });
            context.setPacketHandled(true);
        }
    }
}
