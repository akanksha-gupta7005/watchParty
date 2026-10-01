package com.watchparty.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;

/** A change a Participant asked for; waits until a Host/Moderator approves or rejects it. */
public record PendingRequest(
        String id,
        String userId,
        String username,
        String action,
        JsonNode data,
        long createdAt) {

    public Map<String, Object> toView() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("requestId", id);
        m.put("userId", userId);
        m.put("username", username);
        m.put("action", action);
        m.put("data", data);
        m.put("ts", createdAt);
        return m;
    }
}
