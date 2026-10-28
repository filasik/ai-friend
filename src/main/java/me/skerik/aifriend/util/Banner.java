package me.skerik.aifriend.util;

import java.util.Map;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/** The coloured startup summary. Component logging renders ANSI colours in the console and plain text in latest.log. */
public final class Banner {

    public static final String REPO = "https://github.com/filasik/ai-friend";

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Banner() {
    }

    /**
     * @param details label to value rows between the header and the footer (storage, hooks, ...), in order
     */
    public static void print(ComponentLogger log, String version, String minecraft, Map<String, String> details,
                             long startupMs) {
        TagResolver.Builder r = TagResolver.builder()
                .resolver(Placeholder.unparsed("version", version))
                .resolver(Placeholder.unparsed("mc", minecraft))
                .resolver(Placeholder.unparsed("ms", Long.toString(startupMs)))
                .resolver(Placeholder.unparsed("repo", REPO));
        log.info(MM.deserialize(""));
        log.info(MM.deserialize("<dark_gray>  ┌─ <gold><bold>AI Friend</bold> <gray>v<version> <dark_gray>· <gray>Paper <mc>", r.build()));
        log.info(MM.deserialize("<dark_gray>  │"));
        int i = 0;
        for (Map.Entry<String, String> row : details.entrySet()) {
            r.resolver(Placeholder.unparsed("k" + i, String.format("%-9s", row.getKey())));
            r.resolver(Placeholder.unparsed("v" + i, row.getValue()));
            log.info(MM.deserialize("<dark_gray>  │  <gray><k" + i + "> <white><v" + i + ">", r.build()));
            i++;
        }
        log.info(MM.deserialize("<dark_gray>  │"));
        log.info(MM.deserialize("<dark_gray>  └─ <green>Ready in <ms> ms <dark_gray>· <blue><underlined><repo></underlined>", r.build()));
        log.info(MM.deserialize(""));
    }
}
