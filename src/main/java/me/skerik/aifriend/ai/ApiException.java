package me.skerik.aifriend.ai;

import java.io.IOException;

/** The AI service answered with an HTTP status other than 200. */
public final class ApiException extends IOException {

    private static final long serialVersionUID = 1L;

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
