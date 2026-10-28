package me.skerik.aifriend.ai.provider;

import java.time.Duration;

/** Network settings shared by all HTTP providers. */
public record HttpOptions(Duration connectTimeout, Duration readTimeout, String userAgent) {
}
