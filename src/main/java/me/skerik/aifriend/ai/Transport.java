package me.skerik.aifriend.ai;

import java.io.IOException;

/** Sends one prompt to the AI service and returns the raw answer body. Blocking: call from an async task only. */
@FunctionalInterface
public interface Transport {

    String send(String prompt) throws IOException, InterruptedException;
}
