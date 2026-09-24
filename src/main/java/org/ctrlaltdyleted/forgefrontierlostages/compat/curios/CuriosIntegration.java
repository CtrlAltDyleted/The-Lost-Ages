package org.ctrlaltdyleted.forgefrontierlostages.compat.curios;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.api.distmarker.Dist;
import org.slf4j.Logger;

/** Loads the optional bridge only after its three dependencies pass API checks. */
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
            MinecraftForge.EVENT_BUS.register(new CuriosServerEvents());
            if (FMLEnvironment.dist == Dist.CLIENT) {
                CuriosClientBootstrap.initialize();
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
