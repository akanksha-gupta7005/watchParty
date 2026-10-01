package com.watchparty.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.watchparty.exception.WsException;
import com.watchparty.model.Action;
import com.watchparty.model.PlaybackSnapshot;
import com.watchparty.model.Room;
import com.watchparty.websocket.Validation;
import org.springframework.stereotype.Service;

/**
 * The ONE code path that changes video state. Used for direct commands from a
 * Host/Moderator and for approved participant requests.
 */
@Service
public class PlaybackService {

    private final RoomService rooms;

    public PlaybackService(RoomService rooms) {
        this.rooms = rooms;
    }

    public static Action actionFor(String type) {
        return switch (type) {
            case "play" -> Action.PLAY;
            case "pause" -> Action.PAUSE;
            case "seek" -> Action.SEEK;
            case "change_video" -> Action.CHANGE_VIDEO;
            default -> throw new WsException("BAD_REQUEST", "Unknown playback action");
        };
    }

    /** Validates the payload, updates the room, broadcasts sync_state, and saves to the database. */
    public void apply(Room room, String by, String type, JsonNode payload) {
        Action action = actionFor(type);
        Double time = null;
        String videoId = null;

        if (action == Action.PLAY || action == Action.PAUSE) {
            time = Validation.optionalTime(payload, "time");
        } else if (action == Action.SEEK) {
            time = Validation.time(payload, "time");
        } else if (action == Action.CHANGE_VIDEO) {
            videoId = Validation.videoId(payload);
        }

        PlaybackSnapshot snapshot = room.applyPlayback(action, time, videoId, by);
        rooms.persistPlayback(room.getCode(), snapshot);
    }
}
