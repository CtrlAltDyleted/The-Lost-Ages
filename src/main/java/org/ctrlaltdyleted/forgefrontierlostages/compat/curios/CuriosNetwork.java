package org.ctrlaltdyleted.forgefrontierlostages.compat.curios;

import com.ultramega.ae2insertexportcard.container.UpgradeContainerMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import java.util.function.Supplier;

final class CuriosNetwork {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("forgefrontierlostages", "curios_export"),
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
