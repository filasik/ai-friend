package me.skerik.aifriend.longmemory;

import java.util.regex.Pattern;

/** Code-side safety net for extracted notes: anything that looks like contact data or a secret is dropped. */
public final class SensitiveFilter {

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+\\s?(@|\\(at\\)|\\[at\\])\\s?[\\w-]+(\\.|\\s?\\(dot\\)\\s?)[\\w.-]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern URL = Pattern.compile("(?i)(https?://|www\\.|\\b[a-z0-9-]+\\.(com|net|org|eu|cz|sk|de|io|gg|me|ru|tv|xyz|info|co|uk|pl|fr|us)\\b|discord\\.gg|\\bdiscord\\.com)");
    private static final Pattern IPV4 = Pattern.compile("\\b\\d{1,3}(\\.\\d{1,3}){3}\\b");
    private static final Pattern IPV6 = Pattern.compile("(?i)\\b([0-9a-f]{1,4}:){3,}[0-9a-f]{0,4}\\b");
    /** Phone numbers, card numbers, ids: seven or more digits, possibly separated by spaces, dots, dashes or brackets. */
    private static final Pattern PHONE = Pattern.compile("\\+?\\d[\\d\\s().-]{5,}\\d");
    private static final Pattern SECRET = Pattern.compile("(?i)\\b(password|passwd|passwort|heslo|pin code|api[ -]?key|token|secret)\\b");

    private SensitiveFilter() {
    }

    public static boolean isSensitive(String note) {
        if (EMAIL.matcher(note).find() || URL.matcher(note).find() || IPV4.matcher(note).find()
                || IPV6.matcher(note).find() || SECRET.matcher(note).find()) {
            return true;
        }
        var m = PHONE.matcher(note);
        while (m.find()) {
            if (m.group().chars().filter(Character::isDigit).count() >= 7) return true;
        }
        return false;
    }
}
