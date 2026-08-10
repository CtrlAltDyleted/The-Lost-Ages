package org.ctrlaltdyleted.forgefrontierlostages;

import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public final class JeiBlacklistManager
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String AUTO_TRADER_ENTRY = "easy_villagers:auto_trader";

    private JeiBlacklistManager()
    {
    }

    /**
     * Removes the easy_villagers:auto_trader entry from config/jei/blacklist.cfg
     * so that the Auto Trader is visible in JEI.
     * Runs only on the physical client; no-ops on dedicated servers.
     */
    public static void removeAutoTraderBlacklistEntry()
    {
        if (FMLEnvironment.dist != Dist.CLIENT)
        {
            return;
        }

        final Path blacklistPath = FMLPaths.CONFIGDIR.get()
                .resolve("jei")
                .resolve("blacklist.cfg");

        if (Files.notExists(blacklistPath))
        {
            LOGGER.debug("JEI blacklist not found, skipping Auto Trader removal: {}", blacklistPath);
            return;
        }

        try
        {
            final String original = Files.readString(blacklistPath, StandardCharsets.UTF_8);
            final boolean endsWithNewline = original.endsWith("\n") || original.endsWith("\r\n");
            final String[] lines = original.split("\\r?\\n", -1);

            final List<String> retained = new ArrayList<>();
            boolean removed = false;

            for (final String line : lines)
            {
                if (line.trim().equals(AUTO_TRADER_ENTRY))
                {
                    removed = true;
                }
                else
                {
                    retained.add(line);
                }
            }

            if (!removed)
            {
                LOGGER.debug("JEI blacklist: Auto Trader entry already absent, nothing to do.");
                return;
            }

            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < retained.size(); i++)
            {
                sb.append(retained.get(i));
                if (i < retained.size() - 1)
                {
                    sb.append('\n');
                }
            }
            if (endsWithNewline && sb.length() > 0)
            {
                sb.append('\n');
            }

            Files.writeString(
                    blacklistPath,
                    sb.toString(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );

            LOGGER.info("JEI blacklist: removed '{}' entry so the Auto Trader appears in JEI.", AUTO_TRADER_ENTRY);
        }
        catch (IOException e)
        {
            LOGGER.warn("JEI blacklist: failed to patch {} — Auto Trader may remain hidden in JEI.", blacklistPath, e);
        }
    }
}
