package org.ctrlaltdyleted.thelostages.compat.ae2;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLModContainer;
import org.slf4j.Logger;

public final class ExtendedAeConfigIntegration {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MOD_ID = "expatternprovider";

    private ExtendedAeConfigIntegration() {}

    public static void initialize() {
        if (!ModList.get().isLoaded(MOD_ID)) return;
        ModList.get().getModContainerById(MOD_ID).ifPresent(container -> {
            if (!(container instanceof FMLModContainer forgeContainer)) {
                LOGGER.error("Could not register ExtendedAE config compatibility: unsupported mod container");
                return;
            }
            forgeContainer.getEventBus().addListener(EventPriority.HIGHEST, false,
                    ModConfigEvent.Loading.class, ExtendedAeConfigIntegration::onConfigLoading);
            forgeContainer.getEventBus().addListener(EventPriority.HIGHEST, false,
                    ModConfigEvent.Reloading.class, ExtendedAeConfigIntegration::onConfigReloading);
        });
    }

    private static void onConfigLoading(ModConfigEvent.Loading event) {
        apply(event.getConfig());
    }

    private static void onConfigReloading(ModConfigEvent.Reloading event) {
        apply(event.getConfig());
    }

    private static void apply(ModConfig config) {
        if (!MOD_ID.equals(config.getModId()) || config.getType() != ModConfig.Type.COMMON) return;
        if (!(config.getSpec() instanceof ForgeConfigSpec spec)) return;
        try {
            setExactValue(spec, "device.assembler_matrix_max_size", 7);
        } catch (RuntimeException error) {
            LOGGER.error("Could not apply ExtendedAE Assembler Matrix compatibility", error);
        }
    }

    private static void setExactValue(ForgeConfigSpec spec, String path, int expected) {
        Object entry = spec.getValues().get(path);
        if (!(entry instanceof ForgeConfigSpec.IntValue value)) {
            throw new IllegalStateException("Expected ExtendedAE integer config entry: " + path);
        }
        if (value.get() == expected) return;
        value.set(expected);
        LOGGER.info("Adjusted ExtendedAE {} to {}", path, expected);
    }
}
