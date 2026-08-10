package org.ctrlaltdyleted.forgefrontierlostages.config;

import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public final class LogBegoneConfigPatcher
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LOGBEGONE_FILE = "logbegone.toml";
    private static final String LOGBEGONE_SECTION = "logbegone";
    private static final String PHRASES_KEY = "phrases";
    private static final String REGEX_KEY = "regex";
    private static final String RADIUM_ERROR = "Radium Class Analysis Error";
    private static final String RENDER_TYPE_DIST_ERROR = "Attempted to load class net/minecraft/client/renderer/RenderType for invalid dist DEDICATED_SERVER";
    private static final List<String> REQUIRED_VALUES = List.of(RADIUM_ERROR, RENDER_TYPE_DIST_ERROR);

    private LogBegoneConfigPatcher()
    {
    }

    public static void patch()
    {
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER)
        {
            return;
        }

        final Path configPath = FMLPaths.CONFIGDIR.get().resolve(LOGBEGONE_FILE);
        if (Files.notExists(configPath))
        {
            LOGGER.warn("Log Begone config not found, skipping patch: {}", configPath);
            return;
        }

        final String originalContent;
        try
        {
            originalContent = Files.readString(configPath, StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            LOGGER.error("Failed reading Log Begone config {}", configPath, e);
            return;
        }

        final SectionRange logbegoneSection = findSectionRange(originalContent, LOGBEGONE_SECTION);
        if (logbegoneSection == null)
        {
            LOGGER.error("Unable to locate [{}] section in Log Begone config {}, aborting patch", LOGBEGONE_SECTION, configPath);
            return;
        }

        final AssignmentRange phrasesRange = findArrayAssignmentRange(originalContent, logbegoneSection.bodyStartInclusive(), logbegoneSection.bodyEndExclusive(), PHRASES_KEY);
        if (phrasesRange == null)
        {
            LOGGER.error("Unable to locate '{}' array in [{}] section of Log Begone config {}, aborting patch", PHRASES_KEY, LOGBEGONE_SECTION, configPath);
            return;
        }

        final AssignmentRange regexRange = findArrayAssignmentRange(originalContent, logbegoneSection.bodyStartInclusive(), logbegoneSection.bodyEndExclusive(), REGEX_KEY);
        if (regexRange == null)
        {
            LOGGER.error("Unable to locate '{}' array in [{}] section of Log Begone config {}, aborting patch", REGEX_KEY, LOGBEGONE_SECTION, configPath);
            return;
        }

        final String newline = detectNewline(originalContent);
        final ArrayPatchResult phrasesPatch;
        final ArrayPatchResult regexPatch;
        try
        {
            phrasesPatch = patchArrayValue(originalContent, phrasesRange, REQUIRED_VALUES, newline, PHRASES_KEY);
            regexPatch = patchArrayValue(originalContent, regexRange, REQUIRED_VALUES, newline, REGEX_KEY);
        }
        catch (IllegalArgumentException e)
        {
            LOGGER.error("Malformed Log Begone config {}, aborting patch: {}", configPath, e.getMessage());
            return;
        }

        if (!phrasesPatch.changed() && !regexPatch.changed())
        {
            LOGGER.info("Log Begone config already contains required dedicated server filters: {}", configPath);
            return;
        }

        final String updatedContent = applyArrayPatches(originalContent, phrasesRange, phrasesPatch.updatedArray(), regexRange, regexPatch.updatedArray());
        try
        {
            writeAtomically(configPath, updatedContent);
            LOGGER.info("Updated Log Begone config with dedicated server filters: {}", configPath);
        }
        catch (IOException e)
        {
            LOGGER.error("Failed writing Log Begone config {}", configPath, e);
        }
    }

    private static SectionRange findSectionRange(String content, String sectionName)
    {
        int cursor = 0;
        int sectionBodyStart = -1;
        while (cursor <= content.length())
        {
            final int lineStart = cursor;
            int lineEnd = content.indexOf('\n', lineStart);
            if (lineEnd < 0)
            {
                lineEnd = content.length();
                cursor = content.length() + 1;
            }
            else
            {
                cursor = lineEnd + 1;
            }

            final String line = trimTrailingCarriageReturn(content.substring(lineStart, lineEnd));
            final String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#"))
            {
                continue;
            }

            if (!isSimpleTableHeader(trimmed))
            {
                continue;
            }

            final String headerName = trimmed.substring(1, trimmed.length() - 1).trim();
            if (sectionBodyStart >= 0)
            {
                return new SectionRange(sectionBodyStart, lineStart);
            }

            if (headerName.equals(sectionName))
            {
                sectionBodyStart = cursor <= content.length() ? cursor : content.length();
            }
        }

        if (sectionBodyStart >= 0)
        {
            return new SectionRange(sectionBodyStart, content.length());
        }
        return null;
    }

    private static boolean isSimpleTableHeader(String trimmedLine)
    {
        if (trimmedLine.length() < 3 || trimmedLine.charAt(0) != '[' || trimmedLine.charAt(trimmedLine.length() - 1) != ']')
        {
            return false;
        }
        if (trimmedLine.startsWith("[[") || trimmedLine.endsWith("]]"))
        {
            return false;
        }
        final String inner = trimmedLine.substring(1, trimmedLine.length() - 1).trim();
        if (inner.isEmpty())
        {
            return false;
        }
        return inner.indexOf('#') < 0;
    }

    private static AssignmentRange findArrayAssignmentRange(String content, int startInclusive, int endExclusive, String key)
    {
        boolean inString = false;
        char quote = 0;
        boolean escaped = false;
        boolean inComment = false;
        int arrayDepth = 0;

        for (int i = startInclusive; i < endExclusive; i++)
        {
            final char c = content.charAt(i);
            if (inComment)
            {
                if (c == '\n')
                {
                    inComment = false;
                }
                continue;
            }

            if (inString)
            {
                if (quote == '"' && escaped)
                {
                    escaped = false;
                    continue;
                }
                if (quote == '"' && c == '\\')
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

            if (c == '#')
            {
                inComment = true;
                continue;
            }

            if (c == '"' || c == '\'')
            {
                inString = true;
                quote = c;
                escaped = false;
                continue;
            }

            if (c == '[')
            {
                arrayDepth++;
                continue;
            }
            if (c == ']')
            {
                if (arrayDepth > 0)
                {
                    arrayDepth--;
                }
                continue;
            }

            if (arrayDepth == 0 && isBareKeyAt(content, i, key))
            {
                int cursor = i + key.length();
                while (cursor < endExclusive && Character.isWhitespace(content.charAt(cursor)))
                {
                    cursor++;
                }
                if (cursor >= endExclusive || content.charAt(cursor) != '=')
                {
                    continue;
                }
                cursor++;
                while (cursor < endExclusive && Character.isWhitespace(content.charAt(cursor)))
                {
                    cursor++;
                }
                if (cursor >= endExclusive || content.charAt(cursor) != '[')
                {
                    continue;
                }

                final int arrayStart = cursor;
                final int arrayEnd = findMatchingBracket(content, arrayStart, endExclusive, '[', ']');
                if (arrayEnd < 0)
                {
                    throw new IllegalArgumentException("Unterminated array for key '" + key + "'");
                }
                return new AssignmentRange(key, arrayStart, arrayEnd + 1);
            }
        }

        return null;
    }

    private static ArrayPatchResult patchArrayValue(String content, AssignmentRange range, List<String> requiredValues, String newline, String keyName)
    {
        final List<ArrayValue> values = parseStringArrayValues(content, range.arrayStartInclusive(), range.arrayEndExclusive(), keyName);
        final List<String> missingValues = new ArrayList<>();

        for (String required : requiredValues)
        {
            boolean present = false;
            for (ArrayValue value : values)
            {
                if (required.equals(value.value()))
                {
                    present = true;
                    break;
                }
            }
            if (!present)
            {
                missingValues.add(required);
            }
        }

        if (missingValues.isEmpty())
        {
            return new ArrayPatchResult(content.substring(range.arrayStartInclusive(), range.arrayEndExclusive()), false);
        }

        final String currentArray = content.substring(range.arrayStartInclusive(), range.arrayEndExclusive());
        final boolean multiline = currentArray.indexOf('\n') >= 0 || currentArray.indexOf('\r') >= 0;
        final int arrayStart = range.arrayStartInclusive();
        final int arrayEndExclusive = range.arrayEndExclusive();
        final int closeIndex = arrayEndExclusive - 1;
        int whitespaceStart = closeIndex;
        while (whitespaceStart > arrayStart + 1 && Character.isWhitespace(content.charAt(whitespaceStart - 1)))
        {
            whitespaceStart--;
        }

        final int insertAt = multiline ? whitespaceStart : closeIndex;
        final boolean hasEntries = !values.isEmpty();
        final int lastNonWhitespace = findLastNonWhitespace(content, arrayStart + 1, closeIndex);
        final boolean hasTrailingComma = hasEntries && lastNonWhitespace >= 0 && content.charAt(lastNonWhitespace) == ',';
        final String insertion;

        if (multiline)
        {
            final String entryIndent = detectEntryIndent(content, values, arrayStart, closeIndex);
            final StringBuilder builder = new StringBuilder();
            if (hasEntries && !hasTrailingComma)
            {
                builder.append(',');
            }
            for (int i = 0; i < missingValues.size(); i++)
            {
                builder.append(newline)
                        .append(entryIndent)
                        .append('"')
                        .append(escapeTomlBasicString(missingValues.get(i)))
                        .append('"');
                if (i < missingValues.size() - 1)
                {
                    builder.append(',');
                }
            }
            insertion = builder.toString();
        }
        else
        {
            final String joinedMissing = joinAsQuotedList(missingValues);
            if (!hasEntries)
            {
                insertion = joinedMissing;
            }
            else if (hasTrailingComma)
            {
                insertion = " " + joinedMissing;
            }
            else
            {
                insertion = ", " + joinedMissing;
            }
        }

        final String updatedArray = content.substring(arrayStart, insertAt)
                + insertion
                + content.substring(insertAt, arrayEndExclusive);
        return new ArrayPatchResult(updatedArray, true);
    }

    private static List<ArrayValue> parseStringArrayValues(String content, int arrayStart, int arrayEndExclusive, String keyName)
    {
        final List<ArrayValue> values = new ArrayList<>();
        int i = arrayStart + 1;
        boolean inComment = false;

        while (i < arrayEndExclusive - 1)
        {
            final char c = content.charAt(i);
            if (inComment)
            {
                if (c == '\n')
                {
                    inComment = false;
                }
                i++;
                continue;
            }

            if (Character.isWhitespace(c) || c == ',')
            {
                i++;
                continue;
            }

            if (c == '#')
            {
                inComment = true;
                i++;
                continue;
            }

            if (c == '"' || c == '\'')
            {
                final StringLiteral literal = readTomlStringLiteral(content, i);
                values.add(new ArrayValue(literal.value(), i, literal.endExclusive()));
                i = literal.endExclusive();
                continue;
            }

            throw new IllegalArgumentException("Unexpected token in '" + keyName + "' array near index " + i);
        }

        return values;
    }

    private static StringLiteral readTomlStringLiteral(String content, int startQuoteIndex)
    {
        final char quote = content.charAt(startQuoteIndex);
        final StringBuilder decoded = new StringBuilder();
        boolean escaped = false;

        for (int i = startQuoteIndex + 1; i < content.length(); i++)
        {
            final char c = content.charAt(i);
            if (quote == '"' && escaped)
            {
                decoded.append(decodeEscapedCharacter(content, i));
                if (c == 'u')
                {
                    i += 4;
                }
                else if (c == 'U')
                {
                    i += 8;
                }
                escaped = false;
                continue;
            }

            if (quote == '"' && c == '\\')
            {
                escaped = true;
                continue;
            }

            if (c == quote)
            {
                return new StringLiteral(decoded.toString(), i + 1);
            }

            decoded.append(c);
        }

        throw new IllegalArgumentException("Unterminated string literal");
    }

    private static char decodeEscapedCharacter(String content, int escapeIndex)
    {
        final char esc = content.charAt(escapeIndex);
        return switch (esc)
        {
            case 'b' -> '\b';
            case 't' -> '\t';
            case 'n' -> '\n';
            case 'f' -> '\f';
            case 'r' -> '\r';
            case '"' -> '"';
            case '\\' -> '\\';
            case 'u' -> decodeUnicodeEscape(content, escapeIndex + 1, 4);
            case 'U' -> decodeUnicodeEscape(content, escapeIndex + 1, 8);
            default -> esc;
        };
    }

    private static char decodeUnicodeEscape(String content, int startIndex, int digits)
    {
        if (startIndex + digits > content.length())
        {
            throw new IllegalArgumentException("Invalid unicode escape in string literal");
        }
        final String hex = content.substring(startIndex, startIndex + digits);
        try
        {
            final int codePoint = Integer.parseInt(hex, 16);
            return (char) codePoint;
        }
        catch (NumberFormatException e)
        {
            throw new IllegalArgumentException("Invalid unicode escape in string literal");
        }
    }

    private static int findMatchingBracket(String content, int openBracketIndex, int limitExclusive, char open, char close)
    {
        int depth = 0;
        boolean inString = false;
        char quote = 0;
        boolean escaped = false;
        boolean inComment = false;

        for (int i = openBracketIndex; i < limitExclusive; i++)
        {
            final char c = content.charAt(i);
            if (inComment)
            {
                if (c == '\n')
                {
                    inComment = false;
                }
                continue;
            }

            if (inString)
            {
                if (quote == '"' && escaped)
                {
                    escaped = false;
                    continue;
                }
                if (quote == '"' && c == '\\')
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

            if (c == '#')
            {
                inComment = true;
                continue;
            }
            if (c == '"' || c == '\'')
            {
                inString = true;
                quote = c;
                escaped = false;
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

    private static String applyArrayPatches(
            String content,
            AssignmentRange phrasesRange,
            String updatedPhrases,
            AssignmentRange regexRange,
            String updatedRegex
    )
    {
        if (phrasesRange.arrayStartInclusive() < regexRange.arrayStartInclusive())
        {
            final String withPhrases = content.substring(0, phrasesRange.arrayStartInclusive())
                    + updatedPhrases
                    + content.substring(phrasesRange.arrayEndExclusive());
            final int delta = updatedPhrases.length() - (phrasesRange.arrayEndExclusive() - phrasesRange.arrayStartInclusive());
            final int regexStart = regexRange.arrayStartInclusive() + delta;
            final int regexEnd = regexRange.arrayEndExclusive() + delta;
            return withPhrases.substring(0, regexStart)
                    + updatedRegex
                    + withPhrases.substring(regexEnd);
        }

        final String withRegex = content.substring(0, regexRange.arrayStartInclusive())
                + updatedRegex
                + content.substring(regexRange.arrayEndExclusive());
        final int delta = updatedRegex.length() - (regexRange.arrayEndExclusive() - regexRange.arrayStartInclusive());
        final int phrasesStart = phrasesRange.arrayStartInclusive() + delta;
        final int phrasesEnd = phrasesRange.arrayEndExclusive() + delta;
        return withRegex.substring(0, phrasesStart)
                + updatedPhrases
                + withRegex.substring(phrasesEnd);
    }

    private static String detectNewline(String content)
    {
        return content.contains("\r\n") ? "\r\n" : "\n";
    }

    private static String detectEntryIndent(String content, List<ArrayValue> values, int arrayStart, int closeIndex)
    {
        if (!values.isEmpty())
        {
            final int entryStart = values.get(values.size() - 1).startQuoteInclusive();
            return lineIndent(content, entryStart);
        }

        final String closeIndent = lineIndent(content, closeIndex);
        return closeIndent + "    ";
    }

    private static String lineIndent(String content, int index)
    {
        int lineStart = content.lastIndexOf('\n', index - 1);
        lineStart = lineStart < 0 ? 0 : lineStart + 1;
        if (lineStart < content.length() && content.charAt(lineStart) == '\r')
        {
            lineStart++;
        }

        int cursor = lineStart;
        while (cursor < content.length())
        {
            final char c = content.charAt(cursor);
            if (c != ' ' && c != '\t')
            {
                break;
            }
            cursor++;
        }
        return content.substring(lineStart, cursor);
    }

    private static int findLastNonWhitespace(String content, int startInclusive, int endExclusive)
    {
        for (int i = endExclusive - 1; i >= startInclusive; i--)
        {
            if (!Character.isWhitespace(content.charAt(i)))
            {
                return i;
            }
        }
        return -1;
    }

    private static boolean isBareKeyAt(String content, int index, String key)
    {
        if (index < 0 || index + key.length() > content.length())
        {
            return false;
        }
        if (!content.regionMatches(index, key, 0, key.length()))
        {
            return false;
        }

        final boolean leftBoundary = index == 0 || !isBareKeyCharacter(content.charAt(index - 1));
        final int rightIndex = index + key.length();
        final boolean rightBoundary = rightIndex >= content.length() || !isBareKeyCharacter(content.charAt(rightIndex));
        return leftBoundary && rightBoundary;
    }

    private static boolean isBareKeyCharacter(char c)
    {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-';
    }

    private static String joinAsQuotedList(List<String> values)
    {
        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++)
        {
            if (i > 0)
            {
                builder.append(", ");
            }
            builder.append('"')
                    .append(escapeTomlBasicString(values.get(i)))
                    .append('"');
        }
        return builder.toString();
    }

    private static String escapeTomlBasicString(String value)
    {
        final StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++)
        {
            final char c = value.charAt(i);
            if (c == '\\' || c == '"')
            {
                escaped.append('\\');
            }
            escaped.append(c);
        }
        return escaped.toString();
    }

    private static String trimTrailingCarriageReturn(String value)
    {
        if (!value.isEmpty() && value.charAt(value.length() - 1) == '\r')
        {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static void writeAtomically(Path path, String content) throws IOException
    {
        final Path parent = path.getParent();
        if (parent == null)
        {
            throw new IOException("Config path has no parent directory: " + path);
        }

        Files.createDirectories(parent);
        final Path tempFile = Files.createTempFile(parent, path.getFileName().toString(), ".tmp");
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

    private record SectionRange(int bodyStartInclusive, int bodyEndExclusive)
    {
    }

    private record AssignmentRange(String key, int arrayStartInclusive, int arrayEndExclusive)
    {
    }

    private record ArrayValue(String value, int startQuoteInclusive, int endQuoteExclusive)
    {
    }

    private record ArrayPatchResult(String updatedArray, boolean changed)
    {
    }

    private record StringLiteral(String value, int endExclusive)
    {
    }
}
