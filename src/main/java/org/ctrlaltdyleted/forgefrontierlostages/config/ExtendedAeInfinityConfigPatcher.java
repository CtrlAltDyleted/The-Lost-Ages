package org.ctrlaltdyleted.forgefrontierlostages.config;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Adds one supported fluid type without replacing the user's ExtendedAE config. */
public final class ExtendedAeInfinityConfigPatcher {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Pattern TYPES = Pattern.compile("(?m)^([\\t ]*types[\\t ]*=[\\t ]*\\[)([^\\]\\r\\n]*)(\\])");

    private ExtendedAeInfinityConfigPatcher() {}

    public static void ensureLavaType() {
        if (!ModList.get().isLoaded("expatternprovider")) return;
        Path config = FMLPaths.CONFIGDIR.get().resolve("expatternprovider-common.toml").toAbsolutePath().normalize();
        if (!Files.isRegularFile(config)) {
            LOGGER.info("ExtendedAE config not present yet; lava variant will be added on a later startup: {}", config);
            return;
        }
        try {
            String original = Files.readString(config, StandardCharsets.UTF_8);
            Matcher matcher = TYPES.matcher(original);
            if (!matcher.find()) {
                LOGGER.warn("Could not identify ExtendedAE Infinity Cell types in {}", config);
                return;
            }
            if (matcher.group(2).contains("\"minecraft:lava\"")) return;
            String values = matcher.group(2);
            String separator = values.isBlank() ? "" : ", ";
            String replacement = matcher.group(1) + values + separator + "\"minecraft:lava\"" + matcher.group(3);
            Files.writeString(config, matcher.replaceFirst(Matcher.quoteReplacement(replacement)),
                    StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
            LOGGER.info("Added minecraft:lava to ExtendedAE Infinity Cell types in {}", config);
        } catch (IOException error) {
            LOGGER.error("Could not add the Infinity Lava Cell type to {}", config, error);
        }
    }
}
