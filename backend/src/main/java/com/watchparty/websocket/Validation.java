package com.watchparty.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.watchparty.exception.WsException;
import com.watchparty.model.Role;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Input validation for client payloads. Never trust the browser.
 */
public final class Validation {

    private static final Pattern VIDEO_ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");
    private static final Pattern ROOM_CODE = Pattern.compile("^[A-Z0-9]{4,12}$");
    private static final double MAX_SECONDS = 1_000_000d;
    /** Allowed reactions. The browser maps each key to an emoji picture. */
    private static final Set<String> REACTIONS = Set.of("heart", "laugh", "clap", "wow", "fire", "like");

    private Validation() {
    }

    /** Returns the trimmed text value, or null when missing / blank / not a string. */
    public static String text(JsonNode payload, String field) {
        JsonNode n = payload.path(field);
        if (!n.isTextual()) {
            return null;
        }
        String s = n.asText().trim();
        return s.isEmpty() ? null : s;
    }

    public static String requireText(JsonNode payload, String field, int maxLength) {
        String s = text(payload, field);
        if (s == null) {
            throw new WsException("BAD_REQUEST", "Missing field: " + field);
        }
        if (s.length() > maxLength) {
            throw new WsException("BAD_REQUEST", "Field too long: " + field);
        }
        return s;
    }

    public static String roomCode(JsonNode payload) {
        String s = text(payload, "roomId");
        if (s == null) {
            throw new WsException("BAD_REQUEST", "Missing field: roomId");
        }
        s = s.toUpperCase(Locale.ROOT);
        if (!ROOM_CODE.matcher(s).matches()) {
            throw new WsException("BAD_REQUEST", "Invalid room code");
        }
        return s;
    }

    public static String username(JsonNode payload) {
        String s = text(payload, "username");
        if (s == null) {
            throw new WsException("BAD_REQUEST", "Please enter a name");
        }
        s = s.replaceAll("\\p{Cntrl}", "").replaceAll("\\s+", " ").trim();
        if (s.isEmpty() || s.length() > 24) {
            throw new WsException("BAD_REQUEST", "Name must be 1-24 characters");
        }
        return s;
    }

    public static double time(JsonNode payload, String field) {
        JsonNode n = payload.path(field);
        if (!n.isNumber()) {
            throw new WsException("BAD_REQUEST", "Missing or invalid number: " + field);
        }
        double d = n.asDouble();
        if (Double.isNaN(d) || Double.isInfinite(d) || d < 0 || d > MAX_SECONDS) {
            throw new WsException("BAD_REQUEST", "Time out of range");
        }
        return d;
    }

    /** Optional time: null if absent, validated if present. */
    public static Double optionalTime(JsonNode payload, String field) {
        JsonNode n = payload.path(field);
        if (n.isMissingNode() || n.isNull()) {
            return null;
        }
        return time(payload, field);
    }

    public static String videoId(JsonNode payload) {
        String s = text(payload, "videoId");
        if (s == null || !VIDEO_ID.matcher(s).matches()) {
            throw new WsException("BAD_REQUEST", "Invalid YouTube video id");
        }
        return s;
    }

    public static String reaction(JsonNode payload) {
        String s = text(payload, "emoji");
        if (s == null || !REACTIONS.contains(s)) {
            throw new WsException("BAD_REQUEST", "Unknown reaction");
        }
        return s;
    }

    public static Role role(JsonNode payload) {
        String s = text(payload, "role");
        if (s == null) {
            throw new WsException("BAD_REQUEST", "Missing field: role");
        }
        try {
            return Role.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new WsException("BAD_REQUEST", "Unknown role: " + s);
        }
    }
}
