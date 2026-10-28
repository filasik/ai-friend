package me.skerik.aifriend.knowledge;

import java.util.ArrayList;
import java.util.List;
import me.skerik.aifriend.config.ChatFeatures;
import me.skerik.aifriend.config.Settings;

/** The part of the bot's self-description that depends on this server's configuration (the rest is a bundled file). */
final class SelfKnowledge {

    private SelfKnowledge() {
    }

    /** One entry listing the features that are switched on right now, in the knowledge file format. */
    static String status(Settings s) {
        ChatFeatures f = s.features();
        var aw = f.awareness();
        List<String> on = new ArrayList<>();
        List<String> off = new ArrayList<>();
        add(f.personas().enabled(), "personas (different personalities by world and time)", on, off);
        add(f.memory().enabled(), "short conversation memory", on, off);
        add(f.longMemory().enabled(), "long-term memory of what players tell about themselves", on, off);
        add(f.limits().enabled(), "rate limits (/aifriend limits)", on, off);
        add(f.mentions().enabled(), "direct questions with /ai or by writing my name", on, off);
        add(f.events().enabled(), "reactions to game events (deaths, advancements, first joins...)", on, off);
        add(aw.context().enabled(), "awareness of the surroundings of players who write (time, weather, biome, nearby mobs)", on, off);
        add(aw.mood().enabled(), "a mood", on, off);
        add(aw.relationships().enabled(), "getting to know players (nicknames)", on, off);
        add(aw.boredom().enabled(), "starting a conversation when the chat is quiet", on, off);
        add(aw.supervision().enabled(), "friendly private hints about caps lock, spam and language", on, off);
        add(f.optOut().enabled(), "players can mute me with /aifriend mute", on, off);
        return "# Which features are on at this server right now\n"
                + "keywords: features funkce zapnute zapnuto vypnuto umis umíš co umis what can you do schopnosti nastaveni settings"
                + " pamatujes pamatuješ pamet paměť\n"
                + "On: " + (on.isEmpty() ? "none of the optional features" : String.join("; ", on)) + ". "
                + "Off: " + (off.isEmpty() ? "nothing" : String.join("; ", off)) + ".\n";
    }

    private static void add(boolean enabled, String feature, List<String> on, List<String> off) {
        (enabled ? on : off).add(feature);
    }
}
