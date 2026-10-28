package me.skerik.aifriend.ai;

/** What the model decided: say something, go quiet, or nothing usable came back. */
public record Reply(Action action, String text) {

    public enum Action { SAY, SLEEP, NONE }

    public static final Reply NONE = new Reply(Action.NONE, "");
    public static final Reply SLEEP = new Reply(Action.SLEEP, "");

    public static Reply say(String text) {
        return new Reply(Action.SAY, text);
    }
}
