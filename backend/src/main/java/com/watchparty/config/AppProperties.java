package com.watchparty.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed binding for the "watchparty.*" properties in application.properties.
 */
@ConfigurationProperties(prefix = "watchparty")
public record AppProperties(
        String allowedOrigins,
        int reconnectGraceSeconds,
        int roomRetentionDays,
        String defaultVideoId) {

    /** Allowed origins split into an array (supports patterns such as "*" or "https://*.example.com"). */
    public String[] allowedOriginArray() {
        return allowedOrigins.trim().split("\\s*,\\s*");
    }
}
