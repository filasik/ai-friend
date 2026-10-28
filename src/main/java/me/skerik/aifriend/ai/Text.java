package me.skerik.aifriend.ai;

/** Small text helpers shared by the chat intake and the reply filter. */
final class Text {

    private Text() {
    }

    /** One line without control characters or legacy {@code §} colour codes, single spaces, trimmed. */
    static String singleLine(String input) {
        StringBuilder out = new StringBuilder(input.length());
        boolean space = false;
        for (int i = 0; i < input.length(); ) {
            int cp = input.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '§') {
                // drop the colour code letter that follows as well
                if (i < input.length()) i += Character.charCount(input.codePointAt(i));
                continue;
            }
            if (Character.isWhitespace(cp) || Character.isISOControl(cp)) {
                space = true;
                continue;
            }
            if (space && !out.isEmpty()) out.append(' ');
            space = false;
            out.appendCodePoint(cp);
        }
        return out.toString();
    }

    /** At most {@code max} characters, cut at a word boundary when one is near and marked with an ellipsis. */
    static String cut(String text, int max) {
        if (text.length() <= max) return text;
        int end = max - 1;
        if (Character.isHighSurrogate(text.charAt(end - 1))) end--;
        int space = text.lastIndexOf(' ', end);
        if (space > max * 2 / 3) end = space;
        return text.substring(0, end).stripTrailing() + "…";
    }
}
