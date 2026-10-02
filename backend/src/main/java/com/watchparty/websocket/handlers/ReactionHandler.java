package com.watchparty.websocket.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.watchparty.exception.WsException;
import com.watchparty.model.Action;
import com.watchparty.security.PermissionPolicy;
import com.watchparty.service.RoomService;
import com.watchparty.websocket.Connection;
import com.watchparty.websocket.Validation;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Emoji reactions ("react"). Anyone in the room can react, at any moment of the video.
 * The server adds the current video time, so everyone sees WHEN the reaction happened.
 */
@Component
public class ReactionHandler extends BaseHandler {

    private static final long MIN_INTERVAL_MS = 250;

    public ReactionHandler(RoomService rooms, PermissionPolicy policy) {
        super(rooms, policy);
    }

    @Override
    public Set<String> types() {
        return Set.of("react");
    }

    @Override
    public void handle(String type, JsonNode payload, Connection conn) {
        Actor a = actor(conn);
        policy.check(a.me().getRole(), Action.REACT);

        long now = System.currentTimeMillis();
        if (now - a.me().getLastReactionAt() < MIN_INTERVAL_MS) {
            throw new WsException("RATE_LIMITED", "Slow down a little");
        }
        a.me().setLastReactionAt(now);

        a.room().broadcastReaction(a.me(), Validation.reaction(payload));
    }
}
