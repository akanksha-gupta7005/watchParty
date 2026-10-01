package com.watchparty.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds outgoing JSON messages in the shared envelope format:
 * {"type": "...", "payload": {...}}
 */
public final class Outbound {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Outbound() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /** msg("role_assigned", "userId", id, "role", role) -> payload built from key/value pairs. */
    public static String msg(String type, Object... keyValues) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            payload.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return msgOf(type, payload);
    }

    public static String msgOf(String type, Map<String, Object> payload) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("type", type);
        envelope.put("payload", payload);
        try {
            return MAPPER.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize message " + type, e);
        }
    }
}
