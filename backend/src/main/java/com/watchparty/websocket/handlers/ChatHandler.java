package com.watchparty.websocket.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.watchparty.entity.ChatMessageEntity;
import com.watchparty.exception.WsException;
import com.watchparty.model.Action;
import com.watchparty.security.PermissionPolicy;
import com.watchparty.service.RoomService;
import com.watchparty.websocket.Connection;
import com.watchparty.websocket.Validation;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Text chat. Messages are stored in PostgreSQL so late joiners see recent history. */
@Component
public class ChatHandler extends BaseHandler {

    private static final long MIN_INTERVAL_MS = 400;

    public ChatHandler(RoomService rooms, PermissionPolicy policy) {
        super(rooms, policy);
    }

    @Override
    public Set<String> types() {
        return Set.of("chat");
    }

    @Override
    public void handle(String type, JsonNode payload, Connection conn) {
        Actor a = actor(conn);
        policy.check(a.me().getRole(), Action.CHAT);

        long now = System.currentTimeMillis();
        if (now - a.me().getLastChatAt() < MIN_INTERVAL_MS) {
            throw new WsException("RATE_LIMITED", "You are sending messages too fast");
        }
        a.me().setLastChatAt(now);

        String text = Validation.requireText(payload, "text", 500);
        ChatMessageEntity saved = rooms.saveChat(
                a.room().getCode(), a.me().getUserId(), a.me().getUsername(), text);
        a.room().broadcastChat(a.me(), saved.getId(), text, saved.getCreatedAt().toEpochMilli());
    }
}
