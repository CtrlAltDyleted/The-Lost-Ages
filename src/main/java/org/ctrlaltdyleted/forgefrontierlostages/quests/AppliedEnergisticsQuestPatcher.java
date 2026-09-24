package org.ctrlaltdyleted.forgefrontierlostages.quests;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class AppliedEnergisticsQuestPatcher
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private static Path defaultTargetChapterPath()
    {
        return FMLPaths.CONFIGDIR.get()
                .resolve("ftbquests")
                .resolve("quests")
                .resolve("chapters")
                .resolve("applied_energistics.snbt");
    }
    private static final String RESOURCE_PATH = "/forgefrontierlostages/managed/ftbquests/applied_energistics_additions.snbt";
    private static final List<String> EXPECTED_QUEST_IDS = List.of(
            "42566AC194215C82",
            "2FF9D061A1268FF9",
            "4213D8B68B2D8546",
            "47C06EEDF3FEF708",
            "4B57B4C9692E87BC",
            "54BCEB6981101EE3",
            "7B4DC3DDDD71FDC9",
            "444043822C11DE7E",
            "398D0D8B52F362A8",
            "0D2E92335FE90783",
            "4F1BEB3735E8FAF3",
            "2D29B28CE2CBC26D"
    );

    private AppliedEnergisticsQuestPatcher()
    {
    }

    public static void patch()
    {
        final String bundledFragment = loadBundledFragment();
        if (bundledFragment == null)
        {
            return;
        }
        patch(defaultTargetChapterPath(), bundledFragment);
    }

    static void patch(Path targetChapterPath, String bundledFragment)
    {
        final List<QuestObject> bundledQuests;
        try
        {
            bundledQuests = parseBundledQuestObjects(bundledFragment);
        }
        catch (IllegalArgumentException e)
        {
            LOGGER.error("Unable to validate AE2 quest fragment {}: {}", RESOURCE_PATH, e.getMessage());
            return;
        }

        if (Files.notExists(targetChapterPath))
        {
            LOGGER.warn("AE2 quest chapter not found, skipping patch: {}", targetChapterPath);
            return;
        }

        final String runtimeChapter;
        try
        {
            runtimeChapter = Files.readString(targetChapterPath, StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            LOGGER.error("Failed reading existing AE2 quest chapter {}", targetChapterPath, e);
            return;
        }

        final ListRange runtimeQuestsList;
        try
        {
            runtimeQuestsList = findTopLevelListRange(runtimeChapter, "quests", targetChapterPath.toString());
        }
        catch (IllegalArgumentException e)
        {
            LOGGER.error("Unable to locate top-level quests list in AE2 quest chapter {}: {}", targetChapterPath, e.getMessage());
            return;
        }

        final List<QuestObject> runtimeQuests;
        try
        {
            runtimeQuests = parseQuestObjects(runtimeChapter, runtimeQuestsList.listBodyStart, runtimeQuestsList.listBodyEndExclusive);
        }
        catch (IllegalArgumentException e)
        {
            LOGGER.error("Unable to parse quest objects in AE2 quest chapter {}: {}", targetChapterPath, e.getMessage());
            return;
        }

        final LinkedHashMap<String, Integer> runtimeQuestIdCounts = new LinkedHashMap<>();
        for (QuestObject runtimeQuest : runtimeQuests)
        {
            if (runtimeQuest.id() != null && EXPECTED_QUEST_IDS.contains(runtimeQuest.id()))
            {
                runtimeQuestIdCounts.merge(runtimeQuest.id(), 1, Integer::sum);
            }
        }

        for (String expectedQuestId : EXPECTED_QUEST_IDS)
        {
            final Integer runtimeCount = runtimeQuestIdCounts.get(expectedQuestId);
            if (runtimeCount != null && runtimeCount > 1)
            {
                LOGGER.error("Duplicate Lost Ages AE2 quest ID detected in runtime chapter, aborting patch: {}", expectedQuestId);
                return;
            }
        }

        final String updatedChapter = applyQuestPatches(runtimeChapter, runtimeQuests, bundledQuests, targetChapterPath.toString());
        if (updatedChapter.equals(runtimeChapter))
        {
            LOGGER.info("AE2 quest chapter already matches bundled Lost Ages additions: {}", targetChapterPath);
            return;
        }

        try
        {
            writeAtomically(targetChapterPath, updatedChapter);
            LOGGER.info("Patched AE2 quest chapter with Lost Ages additions: {}", targetChapterPath);
        }
        catch (IOException e)
        {
            LOGGER.error("Failed writing patched AE2 quest chapter {}", targetChapterPath, e);
        }
    }

    private static String loadBundledFragment()
    {
        try (InputStream stream = AppliedEnergisticsQuestPatcher.class.getResourceAsStream(RESOURCE_PATH))
        {
            if (stream == null)
            {
                LOGGER.error("Unable to find bundled AE2 quest fragment: {}", RESOURCE_PATH);
                return null;
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            LOGGER.error("Failed reading bundled AE2 quest fragment {}", RESOURCE_PATH, e);
            return null;
        }
    }

    private static List<QuestObject> parseBundledQuestObjects(String content)
    {
        final ListRange listRange = findTopLevelListRange(content, "quests", RESOURCE_PATH);
        final List<QuestObject> questObjects = parseQuestObjects(content, listRange.listBodyStart, listRange.listBodyEndExclusive);
        final Set<String> ids = new LinkedHashSet<>();
        final Set<String> expected = new LinkedHashSet<>(EXPECTED_QUEST_IDS);

        if (questObjects.size() != EXPECTED_QUEST_IDS.size())
        {
            throw new IllegalArgumentException("Bundled AE2 quest fragment contains " + questObjects.size() + " quest objects; expected " + EXPECTED_QUEST_IDS.size());
        }

        for (QuestObject questObject : questObjects)
        {
            if (!ids.add(questObject.id()))
            {
                throw new IllegalArgumentException("Bundled AE2 quest fragment contains duplicate quest ID: " + questObject.id());
            }
            if (!expected.contains(questObject.id()))
            {
                throw new IllegalArgumentException("Bundled AE2 quest fragment contains unexpected quest ID: " + questObject.id());
            }
        }

        if (!ids.equals(expected))
        {
            throw new IllegalArgumentException("Bundled AE2 quest fragment is missing expected quest IDs");
        }

        return questObjects;
    }

    private static String applyQuestPatches(String content, List<QuestObject> runtimeQuests, List<QuestObject> bundledQuests, String targetPath)
    {
        String updated = content;
        final LinkedHashMap<String, QuestObject> runtimeObjectsById = new LinkedHashMap<>();
        for (QuestObject questObject : runtimeQuests)
        {
            if (questObject.id() != null && EXPECTED_QUEST_IDS.contains(questObject.id()))
            {
                runtimeObjectsById.putIfAbsent(questObject.id(), questObject);
            }
        }

        final List<Replacement> replacements = new ArrayList<>();
        for (QuestObject runtimeQuest : runtimeQuests)
        {
            if (runtimeQuest.id() == null || !EXPECTED_QUEST_IDS.contains(runtimeQuest.id()))
            {
                continue;
            }

            final QuestObject bundledQuest = findBundledQuestById(bundledQuests, runtimeQuest.id());
            if (bundledQuest == null)
            {
                continue;
            }

            if (!runtimeQuest.text().equals(bundledQuest.text()))
            {
                replacements.add(new Replacement(runtimeQuest.startInclusive(), runtimeQuest.endExclusive(), bundledQuest.text()));
            }
        }

        for (int i = replacements.size() - 1; i >= 0; i--)
        {
            final Replacement replacement = replacements.get(i);
            updated = updated.substring(0, replacement.startInclusive())
                    + replacement.replacement()
                    + updated.substring(replacement.endExclusive());
        }

        final ListRange updatedListRange;
        try
        {
            updatedListRange = findTopLevelListRange(updated, "quests", targetPath);
        }
        catch (IllegalArgumentException e)
        {
            LOGGER.error("Unable to locate top-level quests list after patching AE2 quest chapter: {}", e.getMessage());
            return content;
        }

        final StringBuilder insertions = new StringBuilder();
        for (QuestObject bundledQuest : bundledQuests)
        {
            if (runtimeObjectsById.containsKey(bundledQuest.id()))
            {
                continue;
            }

            if (insertions.length() > 0)
            {
                insertions.append('\n');
            }
            insertions.append(bundledQuest.text());
        }

        if (insertions.length() == 0)
        {
            return updated;
        }

        final String prefix = updated.substring(0, updatedListRange.listBodyEndExclusive());
        final String suffix = updated.substring(updatedListRange.listBodyEndExclusive());
        final StringBuilder insertionBuilder = new StringBuilder();
        if (!prefix.isEmpty() && !Character.isWhitespace(prefix.charAt(prefix.length() - 1)))
        {
            insertionBuilder.append('\n');
        }
        insertionBuilder.append(insertions);
        insertionBuilder.append('\n');
        return prefix + insertionBuilder + suffix;
    }

    private static QuestObject findBundledQuestById(List<QuestObject> bundledQuests, String questId)
    {
        for (QuestObject bundledQuest : bundledQuests)
        {
            if (bundledQuest.id().equals(questId))
            {
                return bundledQuest;
            }
        }
        return null;
    }

    private static List<QuestObject> parseQuestObjects(String content, int startInclusive, int endExclusive)
    {
        final List<QuestObject> objects = new ArrayList<>();
        boolean inString = false;
        char quote = 0;
        boolean escaped = false;
        int depth = 0;
        int objectStart = -1;

        for (int i = startInclusive; i < endExclusive; i++)
        {
            final char c = content.charAt(i);

            if (inString)
            {
                if (escaped)
                {
                    escaped = false;
                    continue;
                }
                if (c == '\\')
                {
                    escaped = true;
                    continue;
                }
                if (c == quote)
                {
                    inString = false;
                }
                continue;
            }

            if (c == '"' || c == '\'')
            {
                inString = true;
                quote = c;
                continue;
            }

            if (c == '{')
            {
                if (depth == 0)
                {
                    objectStart = i;
                }
                depth++;
            }
            else if (c == '}')
            {
                depth--;
                if (depth == 0 && objectStart >= 0)
                {
                    final String objectText = content.substring(objectStart, i + 1);
                    final String questId = extractTopLevelQuestId(objectText);
                    objects.add(new QuestObject(questId, objectText, objectStart, i + 1));
                    objectStart = -1;
                }
                if (depth < 0)
                {
                    throw new IllegalArgumentException("Malformed quest object list (unbalanced braces)");
                }
            }
        }

        if (depth != 0)
        {
            throw new IllegalArgumentException("Malformed quest object list (unterminated quest object)");
        }

        return objects;
    }

    private static String extractTopLevelQuestId(String objectText)
    {
        boolean inString = false;
        char quote = 0;
        boolean escaped = false;
        int depth = 0;

        for (int i = 0; i < objectText.length(); i++)
        {
            final char c = objectText.charAt(i);
            if (inString)
            {
                if (escaped)
                {
                    escaped = false;
                    continue;
                }
                if (c == '\\')
                {
                    escaped = true;
                    continue;
                }
                if (c == quote)
                {
                    inString = false;
                }
                continue;
            }

            if (c == '"' || c == '\'')
            {
                inString = true;
                quote = c;
                continue;
            }

            if (c == '{')
            {
                depth++;
                continue;
            }
            if (c == '}')
            {
                if (depth > 0)
                {
                    depth--;
                }
                continue;
            }

            if (depth == 1 && isIdentifierAt(objectText, i, "id"))
            {
                int cursor = i + 2;
                while (cursor < objectText.length() && Character.isWhitespace(objectText.charAt(cursor)))
                {
                    cursor++;
                }
                if (cursor < objectText.length() && objectText.charAt(cursor) == ':')
                {
                    cursor++;
                    while (cursor < objectText.length() && Character.isWhitespace(objectText.charAt(cursor)))
                    {
                        cursor++;
                    }
                    if (cursor < objectText.length())
                    {
                        final char valueQuote = objectText.charAt(cursor);
                        if (valueQuote == '"' || valueQuote == '\'')
                        {
                            final String value = readStringLiteral(objectText, cursor);
                            return value;
                        }
                    }
                }
            }
        }

        return null;
    }

    private static String readStringLiteral(String text, int startIndex)
    {
        final char quote = text.charAt(startIndex);
        final StringBuilder builder = new StringBuilder();
        boolean escaped = false;

        for (int i = startIndex + 1; i < text.length(); i++)
        {
            final char c = text.charAt(i);
            if (escaped)
            {
                builder.append(c);
                escaped = false;
                continue;
            }
            if (c == '\\')
            {
                escaped = true;
                continue;
            }
            if (c == quote)
            {
                return builder.toString();
            }
            builder.append(c);
        }

        return "";
    }

    private static boolean isIdentifierAt(String text, int index, String identifier)
    {
        if (index < 0 || index + identifier.length() > text.length())
        {
            return false;
        }

        if (!text.substring(index, index + identifier.length()).equals(identifier))
        {
            return false;
        }

        final boolean leftBoundary = index == 0 || !Character.isLetterOrDigit(text.charAt(index - 1)) && text.charAt(index - 1) != '_';
        final boolean rightBoundary = index + identifier.length() >= text.length() || !Character.isLetterOrDigit(text.charAt(index + identifier.length())) && text.charAt(index + identifier.length()) != '_';
        return leftBoundary && rightBoundary;
    }

    private static ListRange findTopLevelListRange(String content, String key, String context)
    {
        final RootRange rootRange = findRootCompoundRange(content, context);
        boolean inString = false;
        char quote = 0;
        boolean escaped = false;
        int depth = 1;

        for (int i = rootRange.startInclusive() + 1; i < rootRange.endExclusive(); i++)
        {
            final char c = content.charAt(i);
            if (inString)
            {
                if (escaped)
                {
                    escaped = false;
                    continue;
                }
                if (c == '\\')
                {
                    escaped = true;
                    continue;
                }
                if (c == quote)
                {
                    inString = false;
                }
                continue;
            }

            if (c == '"' || c == '\'')
            {
                inString = true;
                quote = c;
                continue;
            }

            if (c == '{')
            {
                depth++;
                continue;
            }
            if (c == '}')
            {
                if (depth > 1)
                {
                    depth--;
                }
                continue;
            }

            if (depth == 1 && content.startsWith(key, i) && isIdentifierAt(content, i, key))
            {
                int cursor = i + key.length();
                while (cursor < content.length() && Character.isWhitespace(content.charAt(cursor)))
                {
                    cursor++;
                }
                if (cursor < content.length() && content.charAt(cursor) == ':')
                {
                    cursor++;
                    while (cursor < content.length() && Character.isWhitespace(content.charAt(cursor)))
                    {
                        cursor++;
                    }
                    if (cursor < content.length() && content.charAt(cursor) == '[')
                    {
                        final int listOpen = cursor;
                        final int listClose = findMatchingBracket(content, listOpen, '[', ']');
                        if (listClose < 0)
                        {
                            throw new IllegalArgumentException("Unterminated " + key + " list in " + context);
                        }
                        return new ListRange(listOpen + 1, listClose);
                    }
                }
            }
        }

        throw new IllegalArgumentException("Unable to locate top-level '" + key + "' list in " + context);
    }

    private static RootRange findRootCompoundRange(String content, String context)
    {
        boolean inString = false;
        char quote = 0;
        boolean escaped = false;
        int depth = 0;
        int rootStart = -1;

        for (int i = 0; i < content.length(); i++)
        {
            final char c = content.charAt(i);
            if (inString)
            {
                if (escaped)
                {
                    escaped = false;
                    continue;
                }
                if (c == '\\')
                {
                    escaped = true;
                    continue;
                }
                if (c == quote)
                {
                    inString = false;
                }
                continue;
            }

            if (c == '"' || c == '\'')
            {
                inString = true;
                quote = c;
                continue;
            }

            if (c == '{')
            {
                if (depth == 0)
                {
                    rootStart = i;
                }
                depth++;
                continue;
            }
            if (c == '}')
            {
                depth--;
                if (depth == 0 && rootStart >= 0)
                {
                    return new RootRange(rootStart, i + 1);
                }
                if (depth < 0)
                {
                    throw new IllegalArgumentException("Malformed structure in " + context + " (unbalanced braces)");
                }
            }
        }

        if (rootStart < 0)
        {
            throw new IllegalArgumentException("Malformed structure in " + context + " (missing root compound)");
        }

        throw new IllegalArgumentException("Malformed structure in " + context + " (unterminated root compound)");
    }

    private static int findMatchingBracket(String text, int openIndex, char open, char close)
    {
        int depth = 0;
        boolean inString = false;
        char quote = 0;
        boolean escaped = false;

        for (int i = openIndex; i < text.length(); i++)
        {
            final char c = text.charAt(i);
            if (inString)
            {
                if (escaped)
                {
                    escaped = false;
                    continue;
                }
                if (c == '\\')
                {
                    escaped = true;
                    continue;
                }
                if (c == quote)
                {
                    inString = false;
                }
                continue;
            }

            if (c == '"' || c == '\'')
            {
                inString = true;
                quote = c;
                continue;
            }

            if (c == open)
            {
                depth++;
            }
            else if (c == close)
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

    private static void writeAtomically(Path path, String content) throws IOException
    {
        final Path parent = path.getParent();
        if (parent != null)
        {
            Files.createDirectories(parent);
        }

        final Path tempFile = parent != null
                ? Files.createTempFile(parent, path.getFileName().toString(), ".tmp")
                : Files.createTempFile(path.getFileName().toString(), ".tmp");
        try
        {
            Files.writeString(tempFile, content, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
            try
            {
                Files.move(tempFile, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (AtomicMoveNotSupportedException e)
            {
                Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        finally
        {
            if (Files.exists(tempFile))
            {
                Files.deleteIfExists(tempFile);
            }
        }
    }

    private record QuestObject(String id, String text, int startInclusive, int endExclusive)
    {
    }

    private record ListRange(int listBodyStart, int listBodyEndExclusive)
    {
    }

    private record RootRange(int startInclusive, int endExclusive)
    {
    }

    private record Replacement(int startInclusive, int endExclusive, String replacement)
    {
    }
}
