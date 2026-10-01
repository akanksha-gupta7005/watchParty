package com.watchparty.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * One browser WebSocket connection.
 * Wraps a (thread-safe) session and remembers which room/user it is bound to.
 */
public class Connection {

    private static final Logger log = LoggerFactory.getLogger(Connection.class);

    private final WebSocketSession session;
    private volatile String roomCode;
    private volatile String userId;

    public Connection(WebSocketSession session) {
        this.session = session;
    }

    public String getId() {
        return session.getId();
    }

    public void send(String json) {
        if (!session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            log.debug("Send failed on session {}: {}", getId(), e.toString());
        }
    }

    public void close(CloseStatus status) {
        try {
            session.close(status);
        } catch (Exception e) {
            log.debug("Close failed on session {}: {}", getId(), e.toString());
        }
    }

    public void bind(String roomCode, String userId) {
        this.roomCode = roomCode;
        this.userId = userId;
    }

    public void unbind() {
        this.roomCode = null;
        this.userId = null;
    }

    public String getRoomCode() {
        return roomCode;
    }

    public String getUserId() {
        return userId;
    }
}
