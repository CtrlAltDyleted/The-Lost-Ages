package org.ctrlaltdyleted.forgefrontierlostages;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class KubeJsScriptManager
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Pattern AUTO_TRADER_ARRAY_LINE =
            Pattern.compile("(?m)^[\\t ]*['\"]easy_villagers:auto_trader['\"][\\t ]*,?[\\t ]*\\R?");
    private static final Pattern AUTO_TRADER_TOKEN =
            Pattern.compile("['\"]easy_villagers:auto_trader['\"]");
    private static final String COMPACTING_RECIPES_RELATIVE_PATH =
            "kubejs/server_scripts/Mod Adjustments/Easy Villager and Piglins/Compacting_Recipes.js";

    private KubeJsScriptManager()
    {
    }

    public static void patchExternalCompactingRecipes()
    {
        final Path gameDirectory = FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
        final Path compactingRecipesFile = gameDirectory.resolve(COMPACTING_RECIPES_RELATIVE_PATH).normalize();
        if (!compactingRecipesFile.startsWith(gameDirectory))
        {
            LOGGER.warn("Skipping Compacting_Recipes patch; resolved target escaped game directory: {}", compactingRecipesFile);
            return;
        }

        if (!Files.exists(compactingRecipesFile))
        {
            LOGGER.info("Compacting_Recipes.js not found, skipping patch: {}", compactingRecipesFile);
            return;
        }

        if (!Files.isRegularFile(compactingRecipesFile))
        {
            LOGGER.warn("Compacting_Recipes patch target exists but is not a regular file: {}", compactingRecipesFile);
            return;
        }

        try
        {
            final String content = Files.readString(compactingRecipesFile, StandardCharsets.UTF_8);
            final PatchResult patchResult = removeAutoTraderFromRemovedIds(content);

            switch (patchResult.status())
            {
                case PATCHED:
                    Files.writeString(
                            compactingRecipesFile,
                            patchResult.updatedContent(),
                            StandardCharsets.UTF_8,
                            StandardOpenOption.TRUNCATE_EXISTING
                    );
                    LOGGER.info("Removed easy_villagers:auto_trader from removedIds in {}", compactingRecipesFile);
                    break;
                case ALREADY_ABSENT:
                    LOGGER.info("easy_villagers:auto_trader already absent from removedIds in {}", compactingRecipesFile);
                    break;
                case COULD_NOT_PATCH:
                    LOGGER.warn("Could not patch removedIds array in {}", compactingRecipesFile);
                    break;
            }
        }
        catch (IOException e)
        {
            LOGGER.error("Failed to patch Compacting_Recipes.js: {}", compactingRecipesFile, e);
        }
    }

    private static PatchResult removeAutoTraderFromRemovedIds(String content)
    {
        final int removedIdsStart = content.indexOf("removedIds");
        if (removedIdsStart < 0)
        {
            return PatchResult.couldNotPatch();
        }

        final int arrayStart = content.indexOf('[', removedIdsStart);
        if (arrayStart < 0)
        {
            return PatchResult.couldNotPatch();
        }

        final int arrayEnd = findMatchingArrayEnd(content, arrayStart);
        if (arrayEnd < 0)
        {
            return PatchResult.couldNotPatch();
        }

        final String arraySection = content.substring(arrayStart, arrayEnd + 1);
        if (!AUTO_TRADER_TOKEN.matcher(arraySection).find())
        {
            return PatchResult.alreadyAbsent();
        }

        final Matcher lineMatcher = AUTO_TRADER_ARRAY_LINE.matcher(arraySection);
        if (!lineMatcher.find())
        {
            return PatchResult.couldNotPatch();
        }

        final String updatedArraySection = lineMatcher.replaceFirst("");
        final String updatedContent = content.substring(0, arrayStart)
                + updatedArraySection
                + content.substring(arrayEnd + 1);
        return PatchResult.patched(updatedContent);
    }

    private static int findMatchingArrayEnd(String content, int arrayStart)
    {
        int depth = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean escaping = false;

        for (int i = arrayStart; i < content.length(); i++)
        {
            final char c = content.charAt(i);
            if (escaping)
            {
                escaping = false;
                continue;
            }

            if (c == '\\')
            {
                escaping = true;
                continue;
            }

            if (!inDoubleQuote && c == '\'')
            {
                inSingleQuote = !inSingleQuote;
                continue;
            }

            if (!inSingleQuote && c == '"')
            {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }

            if (inSingleQuote || inDoubleQuote)
            {
                continue;
            }

            if (c == '[')
            {
                depth++;
                continue;
            }

            if (c == ']')
            {
                depth--;
                if (depth == 0)
                {
                    return i;
                }
            }
        }

        return -1;
    }

    private enum PatchStatus
    {
        PATCHED,
        ALREADY_ABSENT,
        COULD_NOT_PATCH
    }

    private record PatchResult(PatchStatus status, String updatedContent)
    {
        private static PatchResult patched(String updatedContent)
        {
            return new PatchResult(PatchStatus.PATCHED, updatedContent);
        }

        private static PatchResult alreadyAbsent()
        {
            return new PatchResult(PatchStatus.ALREADY_ABSENT, null);
        }

        private static PatchResult couldNotPatch()
        {
            return new PatchResult(PatchStatus.COULD_NOT_PATCH, null);
        }
    }
}
