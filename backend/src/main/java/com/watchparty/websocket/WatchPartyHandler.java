package com.watchparty.websocket;

import com.watchparty.service.MembershipService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Entry point for every WebSocket connection (transport only, no business logic).
 *
 * Spring's WebSocketSession.sendMessage is NOT thread-safe, and broadcasts come from many
 * threads, so each session is wrapped in ConcurrentWebSocketSessionDecorator
 * (it serializes sends and cuts off clients that cannot keep up).
 */
@Component
public class WatchPartyHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(WatchPartyHandler.class);
    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int SEND_BUFFER_LIMIT_BYTES = 512 * 1024;
    private static final int MAX_INCOMING_CHARS = 8 * 1024;

    private final MessageRouter router;
    private final MembershipService membership;
    private final Map<String, Connection> connections = new ConcurrentHashMap<>();

    public WatchPartyHandler(MessageRouter router, MembershipService membership) {
        this.router = router;
        this.membership = membership;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(
                session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT_BYTES);
        connections.put(session.getId(), new Connection(safe));
        log.debug("WebSocket opened: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Connection conn = connections.get(session.getId());
        if (conn == null) {
            return;
        }
        if (message.getPayloadLength() > MAX_INCOMING_CHARS) {
            conn.send(Outbound.msg("error", "code", "BAD_REQUEST", "message", "Message too large"));
            return;
        }
        router.route(conn, message.getPayload());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("Transport error on {}: {}", session.getId(), exception.toString());
        Connection conn = connections.get(session.getId());
        if (conn != null) {
            conn.close(CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Connection conn = connections.remove(session.getId());
        log.debug("WebSocket closed: {} ({})", session.getId(), status);
        if (conn != null) {
            membership.onDisconnect(conn);
        }
    }
}
