package com.watchparty.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.watchparty.exception.WsException;
import com.watchparty.websocket.handlers.MessageHandler;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Parses the JSON envelope and dispatches by "type" to the right handler.
 * Any error becomes an {"type":"error"} reply to the sender only; the socket stays open.
 */
@Component
public class MessageRouter {

    private static final Logger log = LoggerFactory.getLogger(MessageRouter.class);

    private final Map<String, MessageHandler> handlers = new HashMap<>();

    public MessageRouter(List<MessageHandler> allHandlers) {
        for (MessageHandler h : allHandlers) {
            for (String type : h.types()) {
                if (handlers.put(type, h) != null) {
                    throw new IllegalStateException("Two handlers registered for message type: " + type);
                }
            }
        }
    }

    public void route(Connection conn, String raw) {
        try {
            JsonNode root;
            try {
                root = Outbound.mapper().readTree(raw);
            } catch (JsonProcessingException e) {
                throw new WsException("BAD_REQUEST", "Malformed JSON");
            }
            if (root == null || !root.isObject()) {
                throw new WsException("BAD_REQUEST", "Message must be a JSON object");
            }

            String type = root.path("type").asText("");
            if (type.isEmpty()) {
                throw new WsException("BAD_REQUEST", "Missing message type");
            }

            JsonNode payload = root.path("payload");
            if (payload.isMissingNode() || payload.isNull()) {
                payload = Outbound.mapper().createObjectNode();
            } else if (!payload.isObject()) {
                throw new WsException("BAD_REQUEST", "payload must be an object");
            }

            MessageHandler handler = handlers.get(type);
            if (handler == null) {
                String shown = type.length() > 40 ? type.substring(0, 40) : type;
                throw new WsException("BAD_REQUEST", "Unknown message type: " + shown);
            }
            handler.handle(type, payload, conn);

        } catch (WsException e) {
            conn.send(Outbound.msg("error", "code", e.getCode(), "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Unhandled error while processing a message", e);
            conn.send(Outbound.msg("error", "code", "INTERNAL", "message", "Something went wrong"));
        }
    }
}
