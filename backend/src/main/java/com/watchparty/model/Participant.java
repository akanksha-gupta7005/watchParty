package com.watchparty.model;

import com.watchparty.websocket.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A person sitting in a room. The seat (userId, token, role) survives short disconnects:
 * while the socket is gone, connection is null and the participant shows as offline.
 */
public class Participant {

    private final String userId;
    private final String token;
    private final long accountId;
    private final String username;
    private volatile Role role;
    private volatile Connection connection;
    private volatile long lastChatAt;
    private volatile long lastReactionAt;

    public Participant(String userId, String token, long accountId, String username, Role role, Connection connection) {
        this.userId = userId;
        this.token = token;
        this.accountId = accountId;
        this.username = username;
        this.role = role;
        this.connection = connection;
    }

    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public Connection getConnection() { return connection; }
    public boolean isOnline() { return connection != null; }
    public long getAccountId() { return accountId; }
    public long getLastChatAt() { return lastChatAt; }
    public long getLastReactionAt() { return lastReactionAt; }
    public void setLastReactionAt(long t) { this.lastReactionAt = t; }
    public void setLastChatAt(long lastChatAt) { this.lastChatAt = lastChatAt; }

    public boolean tokenMatches(String candidate) {
        return candidate != null && java.security.MessageDigest.isEqual(
                token.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                candidate.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public String getToken() { return token; }

    public void attach(Connection c) { this.connection = c; }
    public void detach() { this.connection = null; }

    public void send(String json) {
        Connection c = connection;
        if (c != null) {
            c.send(json);
        }
    }

    /** What other clients are allowed to know about this participant. */
    public Map<String, Object> toView() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", userId);
        m.put("username", username);
        m.put("role", role);
        m.put("online", isOnline());
        return m;
    }
}
