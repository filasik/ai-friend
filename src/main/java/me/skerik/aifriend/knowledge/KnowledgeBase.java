package me.skerik.aifriend.knowledge;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * A small searchable text collection (the server's knowledge file, the taught facts, the plugin's own self-description).
 * Plain word matching, no database and no extra request: words are lower-cased, accents are removed and long words are cut
 * to their first four letters, so "serveru" finds "server" and "pravidla" finds "pravidlech" in Czech as well as English.
 * Rarer words count more. Immutable once built, so it can be swapped atomically while the bot is reading it.
 *
 * <p>File format (see the sample file): a line starting with {@code #} opens an entry with that title, the following lines
 * are its text. Without headings every paragraph (separated by an empty line) is an entry. A {@code keywords:} line adds
 * search words that are not shown to the model. Lines starting with {@code //} are comments.
 */
public final class KnowledgeBase {

    /** One fact. {@code title} may be empty. */
    public record Entry(String title, String text) {
    }

    static final int MAX_ENTRY_CHARS = 600;
    static final int MAX_ENTRIES = 2000;
    private static final double MIN_SCORE = 0.5;
    private static final Set<String> STOP = Set.of(
            "the", "and", "for", "are", "you", "what", "how", "who", "can", "was", "this", "that", "with", "have", "has",
            "jak", "jaky", "jaka", "jake", "kde", "kdy", "kdo", "co", "proc", "jsou", "jsem", "jsi", "byl", "byla", "ale",
            "nebo", "nez", "tak", "tam", "tady", "bych", "ten", "ta", "to", "se", "si", "na", "do", "za", "pro", "pod",
            "pri", "ze", "mam", "mas", "ma", "muze", "muzes", "mozno", "bot", "ahoj", "dik", "diky", "jo", "ne", "no");

    private record Indexed(Entry entry, Set<String> title, Set<String> body) {
    }

    private final List<Indexed> items;
    private final Map<String, Integer> docFreq;

    private KnowledgeBase(List<Indexed> items) {
        this.items = List.copyOf(items);
        Map<String, Integer> df = new HashMap<>();
        for (Indexed i : items) {
            Set<String> all = new HashSet<>(i.title());
            all.addAll(i.body());
            for (String w : all) df.merge(w, 1, Integer::sum);
        }
        this.docFreq = df;
    }

    public static KnowledgeBase empty() {
        return new KnowledgeBase(List.of());
    }

    public int size() {
        return items.size();
    }

    public List<Entry> entries() {
        return items.stream().map(Indexed::entry).toList();
    }

    /** Merges several collections into one (the index is rebuilt). */
    public static KnowledgeBase of(List<KnowledgeBase> parts) {
        List<Indexed> all = new ArrayList<>();
        for (KnowledgeBase p : parts) all.addAll(p.items);
        return new KnowledgeBase(all.size() > MAX_ENTRIES ? all.subList(0, MAX_ENTRIES) : all);
    }

    /** Parses the file format described above. Over-long entries are cut and too many entries are ignored, both logged. */
    public static KnowledgeBase parse(String text, String source, Logger log) {
        List<Indexed> out = new ArrayList<>();
        String title = "";
        StringBuilder body = new StringBuilder();
        StringBuilder keywords = new StringBuilder();
        boolean open = false;
        for (String raw : text.split("\\R", -1)) {
            String line = raw.strip();
            if (line.startsWith("//")) continue;
            if (line.startsWith("#")) {
                flush(out, title, body, keywords, source, log);
                title = line.replaceFirst("^#+\\s*", "");
                open = true;
                continue;
            }
            if (line.isEmpty()) {
                if (!open || title.isEmpty()) {
                    flush(out, title, body, keywords, source, log);
                    title = "";
                    open = false;
                }
                continue;
            }
            if (line.regionMatches(true, 0, "keywords:", 0, 9)) {
                keywords.append(' ').append(line.substring(9));
                open = true;
                continue;
            }
            if (!body.isEmpty()) body.append(' ');
            body.append(line);
            open = true;
        }
        flush(out, title, body, keywords, source, log);
        if (out.size() > MAX_ENTRIES) {
            log.warning("Knowledge " + source + ": more than " + MAX_ENTRIES + " entries, the rest is ignored");
            out = out.subList(0, MAX_ENTRIES);
        }
        return new KnowledgeBase(out);
    }

    private static void flush(List<Indexed> out, String title, StringBuilder body, StringBuilder keywords, String source,
                              Logger log) {
        String text = body.toString().strip();
        if (text.isEmpty()) {
            body.setLength(0);
            keywords.setLength(0);
            return;
        }
        if (text.length() > MAX_ENTRY_CHARS) {
            log.warning("Knowledge " + source + ": an entry" + (title.isEmpty() ? "" : " (\"" + title + "\")")
                    + " is longer than " + MAX_ENTRY_CHARS + " characters and was cut; split it into several entries");
            text = text.substring(0, MAX_ENTRY_CHARS);
        }
        Set<String> bodyWords = stems(text);
        bodyWords.addAll(stems(keywords.toString()));
        out.add(new Indexed(new Entry(title.strip(), text), stems(title), bodyWords));
        body.setLength(0);
        keywords.setLength(0);
    }

    /**
     * The entries that best fit the question, best first, at most {@code maxEntries} and about {@code maxChars} characters
     * together. Empty when nothing matches well enough (the bot then gets no knowledge at all, which costs nothing).
     */
    public List<Entry> retrieve(String query, int maxEntries, int maxChars) {
        if (items.isEmpty() || maxEntries <= 0) return List.of();
        Set<String> q = stems(query);
        if (q.isEmpty()) return List.of();
        int n = items.size();
        record Scored(Indexed item, double score) {
        }
        List<Scored> scored = new ArrayList<>();
        for (Indexed i : items) {
            double score = 0;
            for (String w : q) {
                double idf = Math.log(1.0 + (double) n / (1 + docFreq.getOrDefault(w, 0)));
                if (i.title().contains(w)) score += 2 * idf;
                else if (i.body().contains(w)) score += idf;
            }
            if (score >= MIN_SCORE) scored.add(new Scored(i, score));
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        List<Entry> out = new ArrayList<>();
        int chars = 0;
        for (Scored s : scored) {
            Entry e = s.item().entry();
            int len = e.title().length() + e.text().length();
            if (!out.isEmpty() && chars + len > maxChars) continue;
            out.add(e);
            chars += len;
            if (out.size() >= maxEntries) break;
        }
        return out;
    }

    /** Lower case, no accents, cut to four letters, without filler words. */
    static Set<String> stems(String text) {
        String n = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        Set<String> out = new HashSet<>();
        for (String token : n.split("[^\\p{L}\\p{N}]+")) {
            if (token.length() < 3 && !(token.length() == 2 && token.chars().allMatch(Character::isDigit))) continue;
            if (STOP.contains(token)) continue;
            out.add(token.length() > 4 ? token.substring(0, 4) : token);
        }
        return out;
    }
}
