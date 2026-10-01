package com.watchparty.websocket.handlers;

import com.watchparty.exception.WsException;
import com.watchparty.model.Participant;
import com.watchparty.model.Room;
import com.watchparty.security.PermissionPolicy;
import com.watchparty.service.RoomService;
import com.watchparty.websocket.Connection;

/**
 * Shared helper: works out WHO is sending a message.
 * Identity always comes from the server-side connection binding, never from the payload,
 * so a client cannot pretend to be someone else.
 */
public abstract class BaseHandler implements MessageHandler {

    protected final RoomService rooms;
    protected final PermissionPolicy policy;

    protected BaseHandler(RoomService rooms, PermissionPolicy policy) {
        this.rooms = rooms;
        this.policy = policy;
    }

    protected record Actor(Room room, Participant me) {
    }

    protected Actor actor(Connection conn) {
        String code = conn.getRoomCode();
        String userId = conn.getUserId();
        if (code == null || userId == null) {
            throw new WsException("NOT_IN_ROOM", "Join a room first");
        }
        Room room = rooms.findActive(code)
                .orElseThrow(() -> new WsException("NOT_IN_ROOM", "This room is no longer active"));
        Participant me = room.findByUserId(userId);
        if (me == null || me.getConnection() != conn) {
            throw new WsException("NOT_IN_ROOM", "You are not in this room");
        }
        return new Actor(room, me);
    }
}
