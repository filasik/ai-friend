package me.skerik.aifriend.longmemory;

import java.io.IOException;

/** Turns text into a vector for similarity search. Blocking and thread-safe; call it from async code only. */
public interface Embedder {

    /** Model name; stored with every embedding so vectors of different models are never compared. */
    String model();

    float[] embed(String text) throws IOException, InterruptedException;
}
