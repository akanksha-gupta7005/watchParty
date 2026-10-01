package com.watchparty.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.watchparty.config.AppProperties;
import com.watchparty.exception.WsException;
import com.watchparty.model.Participant;
import com.watchparty.model.RemovalResult;
import com.watchparty.model.Role;
import com.watchparty.model.Room;
import com.watchparty.websocket.Connection;
import com.watchparty.websocket.Validation;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Join / leave / disconnect logic, including the reconnect grace period:
 * when a socket drops, the person keeps their seat and role for a few seconds so a
 * page refresh does not cost them (or the host!) their role.
 */
@Service
public class MembershipService {

    private static final Logger log = LoggerFactory.getLogger(MembershipService.class);

    private final RoomService rooms;
    private final AppProperties props;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentMap<String, ScheduledFuture<?>> graceTimers = new ConcurrentHashMap<>();

    public MembershipService(RoomService rooms, AppProperties props, ScheduledExecutorService scheduler) {
        this.rooms = rooms;
        this.props = props;
        this.scheduler = scheduler;
    }

    // ------------------------------------------------------------------ join

    public void join(Connection conn, JsonNode payload) {
        if (conn.getRoomCode() != null) {
            throw new WsException("BAD_REQUEST", "This connection already joined a room");
        }
        String code = Validation.roomCode(payload);
        String username = Validation.username(payload);
        String userId = Validation.text(payload, "userId");
        String token = Validation.text(payload, "token");
        String hostKey = Validation.text(payload, "hostKey");

        Room room = rooms.find(code).orElseThrow(() -> new WsException("NOT_FOUND", "Room not found"));
        List<Map<String, Object>> history = rooms.recentChat(code);

        // 1) Resume an existing seat (refresh / reconnect)
        if (userId != null && token != null) {
            Participant existing = room.findByUserId(userId);
            if (existing != null && existing.tokenMatches(token) && room.reattach(existing, conn, history)) {
                cancelGrace(code, userId);
                conn.bind(code, userId);
                return;
            }
        }

        // 2) New seat. Only the creator (holding the host key) gets Host, and only if no host is present.
        Role role = Role.PARTICIPANT;
        if (!room.hasHost() && rooms.hostKeyMatches(code, hostKey)) {
            role = Role.HOST;
        }
        Participant p = room.join(username, role, conn, history);
        if (p == null) {
            // The room was closed (emptied) while we were joining: load it again once.
            room = rooms.find(code).orElseThrow(() -> new WsException("NOT_FOUND", "Room not found"));
            p = room.join(username, role, conn, history);
            if (p == null) {
                throw new WsException("INTERNAL", "Could not join the room, please try again");
            }
        }
        conn.bind(code, p.getUserId());
        log.info("{} joined room {} as {}", username, code, role);
    }

    // ------------------------------------------------------------------ leave / disconnect

    /** The user clicked Leave: free the seat immediately. */
    public void leave(Connection conn) {
        String code = conn.getRoomCode();
        String userId = conn.getUserId();
        if (code == null || userId == null) {
            return;
        }
        conn.unbind();
        removeSeat(code, userId);
    }

    /** The socket closed (refresh, network loss, tab closed): keep the seat for a grace period. */
    public void onDisconnect(Connection conn) {
        String code = conn.getRoomCode();
        String userId = conn.getUserId();
        if (code == null || userId == null) {
            return;
        }
        rooms.findActive(code).ifPresent(room -> {
            if (room.detach(userId, conn)) {
                scheduleGrace(code, userId);
            }
        });
    }

    /** Host removes a participant. */
    public void kick(Room room, Participant actor, String targetId) {
        Participant target = room.kick(actor, targetId);
        cancelGrace(room.getCode(), target.getUserId());
    }

    // ------------------------------------------------------------------ internals

    private void removeSeat(String code, String userId) {
        cancelGrace(code, userId);
        rooms.findActive(code).ifPresent(room -> {
            room.remove(userId);
            rooms.evictIfEmpty(room);
        });
    }

    private void scheduleGrace(String code, String userId) {
        String key = code + ":" + userId;
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            graceTimers.remove(key);
            try {
                rooms.findActive(code).ifPresent(room -> {
                    RemovalResult result = room.removeIfOffline(userId);
                    if (result != null) {
                        log.info("{} timed out of room {}", result.removed().getUsername(), code);
                        rooms.evictIfEmpty(room);
                    }
                });
            } catch (Exception e) {
                log.warn("Grace cleanup failed for {}: {}", key, e.toString());
            }
        }, props.reconnectGraceSeconds(), TimeUnit.SECONDS);

        ScheduledFuture<?> previous = graceTimers.put(key, future);
        if (previous != null) {
            previous.cancel(false);
        }
    }

    private void cancelGrace(String code, String userId) {
        ScheduledFuture<?> f = graceTimers.remove(code + ":" + userId);
        if (f != null) {
            f.cancel(false);
        }
    }
}
