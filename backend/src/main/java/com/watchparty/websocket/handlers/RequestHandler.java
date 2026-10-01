package com.watchparty.websocket.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.watchparty.exception.WsException;
import com.watchparty.model.Action;
import com.watchparty.model.PendingRequest;
import com.watchparty.security.PermissionPolicy;
import com.watchparty.service.PlaybackService;
import com.watchparty.service.RoomService;
import com.watchparty.websocket.Connection;
import com.watchparty.websocket.Outbound;
import com.watchparty.websocket.Validation;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Approval workflow:
 *  - request_action: a Participant/Viewer asks for a playback change
 *  - resolve_request: a Host/Moderator approves or rejects it
 * An approved request runs through the exact same PlaybackService as a direct command.
 */
@Component
public class RequestHandler extends BaseHandler {

    private final PlaybackService playback;

    public RequestHandler(RoomService rooms, PermissionPolicy policy, PlaybackService playback) {
        super(rooms, policy);
        this.playback = playback;
    }

    @Override
    public Set<String> types() {
        return Set.of("request_action", "resolve_request");
    }

    @Override
    public void handle(String type, JsonNode payload, Connection conn) {
        Actor a = actor(conn);
        if ("request_action".equals(type)) {
            policy.check(a.me().getRole(), Action.REQUEST_ACTION);
            requestAction(a, payload);
        } else {
            policy.check(a.me().getRole(), Action.RESOLVE_REQUEST);
            resolve(a, payload);
        }
    }

    private void requestAction(Actor a, JsonNode payload) {
        String action = Validation.requireText(payload, "action", 32);
        JsonNode data = payload.path("data");
        if (!data.isObject()) {
            data = Outbound.mapper().createObjectNode();
        }

        // Validate NOW so a bad request never reaches a moderator, and keep only known fields.
        ObjectNode clean = Outbound.mapper().createObjectNode();
        switch (action) {
            case "play", "pause" -> {
                Double t = Validation.optionalTime(data, "time");
                if (t != null) {
                    clean.put("time", t);
                }
            }
            case "seek" -> clean.put("time", Validation.time(data, "time"));
            case "change_video" -> clean.put("videoId", Validation.videoId(data));
            default -> throw new WsException("BAD_REQUEST", "Unsupported request: " + action);
        }
        a.room().addRequest(a.me(), action, clean);
    }

    private void resolve(Actor a, JsonNode payload) {
        String requestId = Validation.requireText(payload, "requestId", 64);
        boolean approve = payload.path("approve").asBoolean(false);

        PendingRequest request = a.room().takeRequest(requestId);
        a.room().notifyResolved(a.me(), request, approve);

        if (approve) {
            playback.apply(a.room(), request.username(), request.action(), request.data());
        }
    }
}
