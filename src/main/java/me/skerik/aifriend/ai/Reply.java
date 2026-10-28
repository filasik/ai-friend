package me.skerik.aifriend.ai;

/** What the model decided: say something, go quiet, or nothing usable came back. */
public record Reply(Action action, String text) {

    public enum Action { SAY, SLEEP, NONE }

    public static final Reply NONE = new Reply(Action.NONE, "");
    public static final Reply SLEEP = new Reply(Action.SLEEP, "");

    /** The answer in the wire format the model is asked to use: {@code {"action":"say","text":"..."}}. */
    public static String asJson(String text) {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("action", "say");
        o.addProperty("text", text);
        return o.toString();
    }

    public static Reply say(String text) {
        return new Reply(Action.SAY, text);
    }
}
