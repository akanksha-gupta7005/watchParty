package com.watchparty.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.watchparty.exception.WsException;
import com.watchparty.websocket.Connection;
import com.watchparty.websocket.Outbound;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.socket.CloseStatus;

/**
 * A live watch room: participants, playback state, pending requests.
 *
 * Design notes
 * - Every public method is synchronized on the room, so state changes AND the broadcast that
 *   announces them happen atomically. Two users acting at the same instant can never leave
 *   clients with a different final state than the server.
 * - Authorization is NOT done here; handlers call PermissionPolicy first.
 *   Room only enforces structural rules (e.g. the host cannot remove themselves).
 */
public class Room {

    private static final int MAX_PENDING_PER_USER = 3;

    private final String code;
    private final PlaybackState playback;
    private final Map<String, Participant> participants = new LinkedHashMap<>(); // join order
    private final Map<String, PendingRequest> pending = new LinkedHashMap<>();
    private final int maxParticipants;
    private boolean closed = false;

    public Room(String code, PlaybackState playback, int maxParticipants) {
        this.code = code;
        this.playback = playback;
        this.maxParticipants = maxParticipants;
    }

    public String getCode() {
        return code;
    }

    // ------------------------------------------------------------------ lifecycle

    public synchronized boolean isClosed() {
        return closed;
    }

    public synchronized boolean isEmpty() {
        return participants.isEmpty();
    }

    public synchronized int participantCount() {
        return participants.size();
    }

    /** Marks the room closed if nobody is left. A closed room accepts no new joins. */
    public synchronized boolean closeIfEmpty() {
        if (participants.isEmpty()) {
            closed = true;
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ lookup

    public synchronized boolean hasHost() {
        for (Participant p : participants.values()) {
            if (p.getRole() == Role.HOST) {
                return true;
            }
        }
        return false;
    }

    public synchronized Participant findByUserId(String userId) {
        return userId == null ? null : participants.get(userId);
    }

    /** One account = one seat. Used to let a person come back (or move to another tab). */
    public synchronized Participant findByAccountId(long accountId) {
        for (Participant p : participants.values()) {
            if (p.getAccountId() == accountId) {
                return p;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ membership

    /** Adds a brand new participant. Returns null if the room was closed in the meantime. */
    public synchronized Participant join(String username, long accountId, Role role, Connection conn,
                                         List<Map<String, Object>> chatHistory) {
        if (closed) {
            return null;
        }
        if (participants.size() >= maxParticipants) {
            throw new WsException("ROOM_FULL", "This room is full (maximum " + maxParticipants + " people)");
        }
        String userId = "u_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Participant p = new Participant(userId, UUID.randomUUID().toString(), accountId, username, role, conn);
        participants.put(userId, p);

        p.send(joinedMessage(p, chatHistory));
        broadcastExcept(userId, Outbound.msg("user_joined",
                "username", p.getUsername(),
                "userId", userId,
                "role", p.getRole(),
                "participants", participantViews()));
        return p;
    }

    /** A known participant comes back (page refresh, reconnect). Keeps seat and role. */
    public synchronized boolean reattach(Participant p, Connection conn, List<Map<String, Object>> chatHistory) {
        if (closed || participants.get(p.getUserId()) != p) {
            return false;
        }
        Connection old = p.getConnection();
        p.attach(conn);
        if (old != null && old != conn) {
            // Tell the old tab not to fight for the seat, then drop it.
            old.send(Outbound.msg("replaced"));
            old.close(CloseStatus.NORMAL.withReason("Replaced by a newer connection"));
        }
        p.send(joinedMessage(p, chatHistory));
        broadcastExcept(p.getUserId(), Outbound.msg("presence_changed", "participants", participantViews()));
        return true;
    }

    /** Socket closed but the seat is kept for a grace period. Returns true if state changed. */
    public synchronized boolean detach(String userId, Connection conn) {
        Participant p = participants.get(userId);
        if (p == null || p.getConnection() != conn) {
            return false; // already replaced by a newer connection or already removed
        }
        p.detach();
        broadcast(Outbound.msg("presence_changed", "participants", participantViews()));
        return true;
    }

    /** Removes someone for good (left, or grace period expired). Returns null if not present. */
    public synchronized RemovalResult remove(String userId) {
        Participant removed = participants.remove(userId);
        if (removed == null) {
            return null;
        }
        List<String> dropped = dropRequestsOf(userId);

        Participant newHost = null;
        if (removed.getRole() == Role.HOST && !participants.isEmpty()) {
            newHost = pickSuccessor();
            newHost.setRole(Role.HOST);
        }

        broadcast(Outbound.msg("user_left",
                "username", removed.getUsername(),
                "userId", userId,
                "participants", participantViews()));
        if (newHost != null) {
            broadcast(hostChangedMessage(newHost, userId));
            sendRequestsTo(newHost);
        }
        notifyRequestsDropped(dropped);
        return new RemovalResult(removed, newHost);
    }

    /** Used by the grace timer: only removes if the person is still offline. */
    public synchronized RemovalResult removeIfOffline(String userId) {
        Participant p = participants.get(userId);
        if (p == null || p.isOnline()) {
            return null;
        }
        return remove(userId);
    }

    /** Host removes someone. The target is told, then their socket is closed. */
    public synchronized Participant kick(Participant actor, String targetId) {
        if (targetId.equals(actor.getUserId())) {
            throw new WsException("BAD_REQUEST", "You cannot remove yourself");
        }
        Participant target = participants.get(targetId);
        if (target == null) {
            throw new WsException("NOT_FOUND", "Participant not found");
        }
        participants.remove(targetId);
        List<String> dropped = dropRequestsOf(targetId);

        target.send(Outbound.msg("removed", "reason", "You were removed from the room by the host"));
        broadcast(Outbound.msg("participant_removed",
                "userId", targetId,
                "username", target.getUsername(),
                "participants", participantViews()));
        notifyRequestsDropped(dropped);

        Connection c = target.getConnection();
        if (c != null) {
            c.close(CloseStatus.POLICY_VIOLATION.withReason("Removed by host"));
        }
        return target;
    }

    // ------------------------------------------------------------------ roles

    public synchronized Participant assignRole(Participant actor, String targetId, Role role) {
        if (role == Role.HOST) {
            throw new WsException("BAD_REQUEST", "Use transfer_host to change the host");
        }
        Participant target = participants.get(targetId);
        if (target == null) {
            throw new WsException("NOT_FOUND", "Participant not found");
        }
        if (target.getUserId().equals(actor.getUserId())) {
            throw new WsException("BAD_REQUEST", "You cannot change your own role");
        }
        if (target.getRole() == Role.HOST) {
            throw new WsException("BAD_REQUEST", "The host's role cannot be changed");
        }
        boolean wasManager = target.getRole().isManager();
        target.setRole(role);

        broadcast(Outbound.msg("role_assigned",
                "userId", target.getUserId(),
                "username", target.getUsername(),
                "role", role,
                "participants", participantViews()));
        if (role.isManager() && !wasManager) {
            sendRequestsTo(target); // new moderator needs to see waiting requests
        }
        return target;
    }

    /** Host passes the crown. The old host becomes a Moderator. */
    public synchronized Participant transferHost(Participant actor, String targetId) {
        Participant target = participants.get(targetId);
        if (target == null) {
            throw new WsException("NOT_FOUND", "Participant not found");
        }
        if (target.getUserId().equals(actor.getUserId())) {
            throw new WsException("BAD_REQUEST", "You are already the host");
        }
        if (!target.isOnline()) {
            throw new WsException("BAD_REQUEST", "That participant is offline");
        }
        actor.setRole(Role.MODERATOR);
        target.setRole(Role.HOST);
        broadcast(hostChangedMessage(target, actor.getUserId()));
        sendRequestsTo(target);
        return target;
    }

    // ------------------------------------------------------------------ playback

    /** Applies a playback change and broadcasts the new state to everyone (including the sender). */
    public synchronized PlaybackSnapshot applyPlayback(Action action, Double time, String videoId, String by) {
        switch (action) {
            case PLAY -> playback.play(time);
            case PAUSE -> playback.pause(time);
            case SEEK -> playback.seek(time);
            case CHANGE_VIDEO -> playback.changeVideo(videoId);
            default -> throw new IllegalArgumentException("Not a playback action: " + action);
        }
        broadcast(Outbound.msgOf("sync_state", syncPayload(action.name().toLowerCase(), by)));
        return playback.snapshot();
    }

    /** Periodic re-sync so small drifts are corrected while a video is playing. */
    public synchronized void broadcastHeartbeat() {
        if (playback.isPlaying() && !participants.isEmpty()) {
            broadcast(Outbound.msgOf("sync_state", syncPayload("heartbeat", null)));
        }
    }

    public synchronized PlaybackSnapshot snapshot() {
        return playback.snapshot();
    }

    /** Called when the last person leaves: freeze the video so it resumes where it stopped. */
    public synchronized PlaybackSnapshot freezeForIdle() {
        playback.pause(null);
        return playback.snapshot();
    }

    // ------------------------------------------------------------------ requests

    public synchronized PendingRequest addRequest(Participant from, String action, JsonNode data) {
        long mine = pending.values().stream().filter(r -> r.userId().equals(from.getUserId())).count();
        if (mine >= MAX_PENDING_PER_USER) {
            throw new WsException("BAD_REQUEST", "You already have " + MAX_PENDING_PER_USER + " pending requests");
        }
        PendingRequest r = new PendingRequest(
                UUID.randomUUID().toString().substring(0, 8),
                from.getUserId(), from.getUsername(), action, data, System.currentTimeMillis());
        pending.put(r.id(), r);

        from.send(Outbound.msg("request_sent", "requestId", r.id(), "action", action));
        notifyManagers(Outbound.msgOf("action_requested", r.toView()));
        return r;
    }

    public synchronized PendingRequest takeRequest(String requestId) {
        PendingRequest r = pending.remove(requestId);
        if (r == null) {
            throw new WsException("NOT_FOUND", "That request no longer exists");
        }
        return r;
    }

    /** Tells every manager (so their lists update) and the requester how it ended. */
    public synchronized void notifyResolved(Participant resolver, PendingRequest r, boolean approve) {
        String json = Outbound.msg("request_resolved",
                "requestId", r.id(),
                "approve", approve,
                "action", r.action(),
                "userId", r.userId(),
                "resolvedBy", resolver.getUsername());
        for (Participant p : participants.values()) {
            if (p.getRole().isManager() || p.getUserId().equals(r.userId())) {
                p.send(json);
            }
        }
    }

    // ------------------------------------------------------------------ reactions

    /** Sends an emoji reaction to everyone, stamped with the video time it happened at. */
    public synchronized void broadcastReaction(Participant from, String emojiKey) {
        double at = Math.round(playback.currentPosition() * 10.0) / 10.0;
        broadcast(Outbound.msg("reaction",
                "userId", from.getUserId(),
                "username", from.getUsername(),
                "emoji", emojiKey,
                "videoTime", at,
                "ts", System.currentTimeMillis()));
    }

    // ------------------------------------------------------------------ chat

    public synchronized void broadcastChat(Participant from, long id, String text, long ts) {
        broadcast(Outbound.msg("chat",
                "id", id,
                "userId", from.getUserId(),
                "username", from.getUsername(),
                "text", text,
                "ts", ts));
    }

    // ------------------------------------------------------------------ helpers (callers hold the lock)

    private void broadcast(String json) {
        for (Participant p : participants.values()) {
            p.send(json);
        }
    }

    private void broadcastExcept(String exceptUserId, String json) {
        for (Participant p : participants.values()) {
            if (!p.getUserId().equals(exceptUserId)) {
                p.send(json);
            }
        }
    }

    private void notifyManagers(String json) {
        for (Participant p : participants.values()) {
            if (p.getRole().isManager()) {
                p.send(json);
            }
        }
    }

    private void sendRequestsTo(Participant p) {
        p.send(Outbound.msg("requests_sync", "requests", pendingViews()));
    }

    private List<String> dropRequestsOf(String userId) {
        List<String> dropped = new ArrayList<>();
        Iterator<PendingRequest> it = pending.values().iterator();
        while (it.hasNext()) {
            PendingRequest r = it.next();
            if (r.userId().equals(userId)) {
                dropped.add(r.id());
                it.remove();
            }
        }
        return dropped;
    }

    private void notifyRequestsDropped(List<String> requestIds) {
        for (String id : requestIds) {
            notifyManagers(Outbound.msg("request_resolved",
                    "requestId", id, "approve", false, "reason", "requester_left"));
        }
    }

    /** Best next host: online moderator, then online anyone, then offline moderator, then anyone. */
    private Participant pickSuccessor() {
        Participant best = null;
        int bestScore = -1;
        for (Participant p : participants.values()) {
            int score = (p.isOnline() ? 2 : 0) + (p.getRole() == Role.MODERATOR ? 1 : 0);
            if (score > bestScore) {
                best = p;
                bestScore = score;
            }
        }
        return best;
    }

    private List<Map<String, Object>> participantViews() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Participant p : participants.values()) {
            list.add(p.toView());
        }
        return list;
    }

    private List<Map<String, Object>> pendingViews() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (PendingRequest r : pending.values()) {
            list.add(r.toView());
        }
        return list;
    }

    private Map<String, Object> syncPayload(String cause, String by) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("playState", playback.isPlaying() ? "PLAYING" : "PAUSED");
        m.put("currentTime", Math.round(playback.currentPosition() * 1000.0) / 1000.0);
        m.put("videoId", playback.getVideoId());
        m.put("serverTime", System.currentTimeMillis());
        m.put("cause", cause);
        m.put("by", by);
        return m;
    }

    private String hostChangedMessage(Participant newHost, String previousHostId) {
        return Outbound.msg("host_changed",
                "userId", newHost.getUserId(),
                "username", newHost.getUsername(),
                "previousHostId", previousHostId,
                "participants", participantViews());
    }

    private String joinedMessage(Participant p, List<Map<String, Object>> chatHistory) {
        return Outbound.msg("joined",
                "roomId", code,
                "userId", p.getUserId(),
                "token", p.getToken(),
                "role", p.getRole(),
                "participants", participantViews(),
                "state", syncPayload("join", null),
                "chat", chatHistory,
                "requests", p.getRole().isManager() ? pendingViews() : List.of());
    }
}
