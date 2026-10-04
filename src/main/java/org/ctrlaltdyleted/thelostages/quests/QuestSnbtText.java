package org.ctrlaltdyleted.thelostages.quests;

final class QuestSnbtText {
    private QuestSnbtText() {}

    static int findMatchingBracket(String text, int openIndex, char open, char close)
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
}
