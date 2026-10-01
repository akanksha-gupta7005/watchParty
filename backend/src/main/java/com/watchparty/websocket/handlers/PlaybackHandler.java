package com.watchparty.websocket.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.watchparty.model.Action;
import com.watchparty.security.PermissionPolicy;
import com.watchparty.service.PlaybackService;
import com.watchparty.service.RoomService;
import com.watchparty.websocket.Connection;
import java.util.Set;
import org.springframework.stereotype.Component;

/** play, pause, seek, change_video. Host and Moderator only. */
@Component
public class PlaybackHandler extends BaseHandler {

    private final PlaybackService playback;

    public PlaybackHandler(RoomService rooms, PermissionPolicy policy, PlaybackService playback) {
        super(rooms, policy);
        this.playback = playback;
    }

    @Override
    public Set<String> types() {
        return Set.of("play", "pause", "seek", "change_video");
    }

    @Override
    public void handle(String type, JsonNode payload, Connection conn) {
        Actor a = actor(conn);
        Action action = PlaybackService.actionFor(type);

        // 1) permission FIRST, before anything is validated or changed
        policy.check(a.me().getRole(), action);

        // 2) validate + mutate + broadcast + persist
        playback.apply(a.room(), a.me().getUsername(), type, payload);
    }
}
