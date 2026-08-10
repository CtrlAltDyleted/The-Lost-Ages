package org.ctrlaltdyleted.forgefrontierlostages;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QuestFilePatcher
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String CHAPTER_GROUP_ID = "7BE1503B2D1E8BE5";
    private static final String CHAPTER_GROUP_TITLE = "The Lost Ages";

    private static final String THEME_SECTION_1 = "09122EB4ABF3FEBA";
    private static final String THEME_BACKGROUND_1 = "ftb:textures/ae_screenshot.png";
    private static final String THEME_SECTION_2 = "14AD7CCF86F19D90";
    private static final String THEME_BACKGROUND_2 = "ftb:textures/mob_grinding_utils_bg.png";

    private static final Pattern ID_PATTERN = Pattern.compile("\\bid\\s*:\\s*[\"']" + Pattern.quote(CHAPTER_GROUP_ID) + "[\"']");
    private static final Pattern TITLE_PATTERN = Pattern.compile("\\btitle\\s*:\\s*(\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*')");
    private static final Pattern THEME_SECTION_HEADER = Pattern.compile("^\\s*\\[[^\\]]+\\]\\s*$");
    private static final Pattern THEME_BACKGROUND_LINE = Pattern.compile("^\\s*background\\s*:\\s*.*$");

    private QuestFilePatcher()
    {
    }

    public static void patch()
    {
        patchChapterGroups();
        patchThemeFile();
    }

    private static void patchChapterGroups()
    {
        final Path chapterGroupsPath = FMLPaths.CONFIGDIR.get().resolve("ftbquests").resolve("quests").resolve("chapter_groups.snbt");

        try
        {
            if (Files.notExists(chapterGroupsPath))
            {
                final Path parent = chapterGroupsPath.getParent();
                if (parent != null)
                {
                    Files.createDirectories(parent);
                }
                writeFile(chapterGroupsPath, minimalChapterGroupsContent());
                LOGGER.info("Quest chapter groups created: {}", chapterGroupsPath);
                return;
            }

            final String original = Files.readString(chapterGroupsPath, StandardCharsets.UTF_8);
            final ListRange listRange = findChapterGroupsListRange(original, chapterGroupsPath);
            String updated = ensureChapterGroup(original, listRange);

            if (!updated.equals(original))
            {
                writeFile(chapterGroupsPath, updated);
                LOGGER.info("Quest chapter groups updated: {}", chapterGroupsPath);
            }
            else
            {
                LOGGER.info("Quest chapter groups unchanged: {}", chapterGroupsPath);
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed patching chapter_groups.snbt", e);
        }
    }

    private static String ensureChapterGroup(String content, ListRange listRange)
    {
        final List<ObjectRange> objectRanges = findObjectRanges(content, listRange.listBodyStart, listRange.listBodyEndExclusive);
        String updated = content;
        boolean found = false;
        final List<Replacement> replacements = new ArrayList<>();

        for (ObjectRange range : objectRanges)
        {
            final String objectText = updated.substring(range.startInclusive, range.endExclusive);
            if (ID_PATTERN.matcher(objectText).find())
            {
                found = true;
                final String patched = ensureTitleOnObject(objectText);
                if (!patched.equals(objectText))
                {
                    replacements.add(new Replacement(range.startInclusive, range.endExclusive, patched));
                }
            }
        }

        for (int i = replacements.size() - 1; i >= 0; i--)
        {
            final Replacement replacement = replacements.get(i);
            updated = updated.substring(0, replacement.startInclusive)
                    + replacement.replacement
                    + updated.substring(replacement.endExclusive);
        }

        if (!found)
        {
            updated = appendChapterGroupObject(updated, listRange);
        }

        return updated;
    }

    private static String ensureTitleOnObject(String objectText)
    {
        final Matcher matcher = TITLE_PATTERN.matcher(objectText);
        final String desiredTitle = "title: \"" + CHAPTER_GROUP_TITLE + "\"";

        if (matcher.find())
        {
            final String replaced = matcher.replaceFirst(desiredTitle);
            return replaced;
        }

        final int closeBraceIndex = objectText.lastIndexOf('}');
        if (closeBraceIndex < 0)
        {
            throw new RuntimeException("Malformed chapter_groups object entry");
        }

        final String beforeBrace = objectText.substring(0, closeBraceIndex).trim();
        if (beforeBrace.endsWith("{"))
        {
            return objectText.substring(0, closeBraceIndex) + "title: \"" + CHAPTER_GROUP_TITLE + "\" }";
        }

        return objectText.substring(0, closeBraceIndex) + ", " + desiredTitle + objectText.substring(closeBraceIndex);
    }

    private static String appendChapterGroupObject(String content, ListRange listRange)
    {
        final String newObject = "{ id: \"" + CHAPTER_GROUP_ID + "\", title: \"" + CHAPTER_GROUP_TITLE + "\" }";
        int lastNonWhitespace = listRange.listBodyEndExclusive - 1;

        while (lastNonWhitespace >= listRange.listBodyStart && Character.isWhitespace(content.charAt(lastNonWhitespace)))
        {
            lastNonWhitespace--;
        }

        if (lastNonWhitespace < listRange.listBodyStart)
        {
            return content.substring(0, listRange.listBodyStart)
                    + "\n  " + newObject + "\n"
                    + content.substring(listRange.listBodyEndExclusive);
        }

        final char lastChar = content.charAt(lastNonWhitespace);
        final String prefix = lastChar == ',' ? "\n  " : ",\n  ";
        final int insertAt = lastNonWhitespace + 1;
        return content.substring(0, insertAt)
                + prefix + newObject
                + content.substring(insertAt);
    }

    private static ListRange findChapterGroupsListRange(String content, Path path)
    {
        final int keyIndex = content.indexOf("chapter_groups");
        if (keyIndex < 0)
        {
            throw new RuntimeException("Invalid chapter_groups.snbt (missing chapter_groups key): " + path);
        }

        int index = keyIndex + "chapter_groups".length();
        while (index < content.length() && Character.isWhitespace(content.charAt(index)))
        {
            index++;
        }

        if (index >= content.length() || content.charAt(index) != ':')
        {
            throw new RuntimeException("Invalid chapter_groups.snbt (missing ':' after chapter_groups): " + path);
        }

        index++;
        while (index < content.length() && Character.isWhitespace(content.charAt(index)))
        {
            index++;
        }

        if (index >= content.length() || content.charAt(index) != '[')
        {
            throw new RuntimeException("Invalid chapter_groups.snbt (chapter_groups is not a list): " + path);
        }

        final int listOpen = index;
        final int listClose = findMatchingBracket(content, listOpen, '[', ']');
        if (listClose < 0)
        {
            throw new RuntimeException("Invalid chapter_groups.snbt (unterminated chapter_groups list): " + path);
        }

        return new ListRange(listOpen + 1, listClose);
    }

    private static List<ObjectRange> findObjectRanges(String content, int startInclusive, int endExclusive)
    {
        final List<ObjectRange> ranges = new ArrayList<>();
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
                    ranges.add(new ObjectRange(objectStart, i + 1));
                    objectStart = -1;
                }
                if (depth < 0)
                {
                    throw new RuntimeException("Malformed chapter_groups list (unbalanced object braces)");
                }
            }
        }

        if (depth != 0)
        {
            throw new RuntimeException("Malformed chapter_groups list (unterminated object)");
        }

        return ranges;
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

    private static String minimalChapterGroupsContent()
    {
        return "{\n"
                + "  chapter_groups: [\n"
                + "    { id: \"" + CHAPTER_GROUP_ID + "\", title: \"" + CHAPTER_GROUP_TITLE + "\" }\n"
                + "  ]\n"
                + "}\n";
    }

    private static void patchThemeFile()
    {
        final Path themePath = FMLPaths.GAMEDIR.get()
                .resolve("kubejs")
                .resolve("assets")
                .resolve("ftbquests")
                .resolve("ftb_quests_theme.txt");

        try
        {
            if (Files.notExists(themePath))
            {
                final Path parent = themePath.getParent();
                if (parent != null)
                {
                    Files.createDirectories(parent);
                }
                writeFile(themePath, minimalThemeContent());
                LOGGER.info("FTB Quests theme created: {}", themePath);
                return;
            }

            final String original = Files.readString(themePath, StandardCharsets.UTF_8);
            final String updated = ensureThemeSections(original);

            if (!updated.equals(original))
            {
                writeFile(themePath, updated);
                LOGGER.info("FTB Quests theme updated: {}", themePath);
            }
            else
            {
                LOGGER.info("FTB Quests theme unchanged: {}", themePath);
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed patching FTB Quests theme file", e);
        }
    }

    private static String ensureThemeSections(String content)
    {
        final List<String> lines = new ArrayList<>(List.of(content.split("\\R", -1)));
        boolean changed = false;

        changed |= ensureThemeSection(lines, THEME_SECTION_1, THEME_BACKGROUND_1);
        changed |= ensureThemeSection(lines, THEME_SECTION_2, THEME_BACKGROUND_2);

        if (!changed)
        {
            return content;
        }

        return String.join("\n", lines);
    }

    private static boolean ensureThemeSection(List<String> lines, String sectionId, String background)
    {
        final String header = "[" + sectionId + "]";
        final String backgroundLine = "background: " + background;
        int headerIndex = -1;

        for (int i = 0; i < lines.size(); i++)
        {
            if (lines.get(i).trim().equals(header))
            {
                headerIndex = i;
                break;
            }
        }

        if (headerIndex < 0)
        {
            if (!lines.isEmpty())
            {
                int last = lines.size() - 1;
                while (last >= 0 && lines.get(last).isEmpty())
                {
                    last--;
                }
                if (last >= 0)
                {
                    lines.subList(last + 1, lines.size()).clear();
                    lines.add("");
                }
            }

            lines.add(header);
            lines.add(backgroundLine);
            return true;
        }

        int sectionEnd = lines.size();
        for (int i = headerIndex + 1; i < lines.size(); i++)
        {
            if (THEME_SECTION_HEADER.matcher(lines.get(i)).matches())
            {
                sectionEnd = i;
                break;
            }
        }

        for (int i = headerIndex + 1; i < sectionEnd; i++)
        {
            if (THEME_BACKGROUND_LINE.matcher(lines.get(i)).matches())
            {
                if (!lines.get(i).equals(backgroundLine))
                {
                    lines.set(i, backgroundLine);
                    return true;
                }
                return false;
            }
        }

        lines.add(headerIndex + 1, backgroundLine);
        return true;
    }

    private static String minimalThemeContent()
    {
        return "[" + THEME_SECTION_1 + "]\n"
                + "background: " + THEME_BACKGROUND_1 + "\n\n"
                + "[" + THEME_SECTION_2 + "]\n"
                + "background: " + THEME_BACKGROUND_2 + "\n";
    }

    private static void writeFile(Path path, String content) throws IOException
    {
        Files.writeString(path, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private record ListRange(int listBodyStart, int listBodyEndExclusive)
    {
    }

    private record ObjectRange(int startInclusive, int endExclusive)
    {
    }

    private record Replacement(int startInclusive, int endExclusive, String replacement)
    {
    }
}
