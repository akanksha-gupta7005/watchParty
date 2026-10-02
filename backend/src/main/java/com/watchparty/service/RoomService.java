package com.watchparty.service;

import com.watchparty.config.AppProperties;
import com.watchparty.entity.ChatMessageEntity;
import com.watchparty.entity.RoomEntity;
import com.watchparty.model.PlaybackSnapshot;
import com.watchparty.model.PlaybackState;
import com.watchparty.model.Room;
import com.watchparty.repository.ChatMessageRepository;
import com.watchparty.repository.RoomRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps live rooms in memory and the durable parts (code, video, position, chat) in PostgreSQL.
 */
@Service
public class RoomService {

    private static final Logger log = LoggerFactory.getLogger(RoomService.class);
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O/1/I
    private static final int CODE_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    public record CreatedRoom(String code, String hostKey) {
    }

    private final RoomRepository roomRepo;
    private final ChatMessageRepository chatRepo;
    private final AppProperties props;
    private final ConcurrentMap<String, Room> rooms = new ConcurrentHashMap<>();

    public RoomService(RoomRepository roomRepo, ChatMessageRepository chatRepo, AppProperties props) {
        this.roomRepo = roomRepo;
        this.chatRepo = chatRepo;
        this.props = props;
    }

    public static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------ create / lookup

    @Transactional
    public CreatedRoom createRoom(long ownerId) {
        String code;
        do {
            code = randomCode();
        } while (roomRepo.existsById(code));

        String hostKey = UUID.randomUUID().toString().replace("-", "");
        roomRepo.save(new RoomEntity(code, hostKey, props.defaultVideoId(), ownerId));
        log.info("Room {} created by account {}", code, ownerId);
        return new CreatedRoom(code, hostKey);
    }

    /** Cheap existence check (does not load the room into memory). */
    public boolean exists(String rawCode) {
        String code = normalize(rawCode);
        if (code.isEmpty() || code.length() > 12) {
            return false;
        }
        return rooms.containsKey(code) || roomRepo.existsById(code);
    }

    /** Returns the live room, loading it from the database if it is not in memory yet. */
    public Optional<Room> find(String rawCode) {
        String code = normalize(rawCode);
        if (code.isEmpty() || code.length() > 12) {
            return Optional.empty();
        }
        Room room = rooms.get(code);
        if (room != null && room.isClosed()) {
            rooms.remove(code, room);
            room = null;
        }
        if (room != null) {
            return Optional.of(room);
        }
        Optional<RoomEntity> entity = roomRepo.findById(code);
        if (entity.isEmpty()) {
            return Optional.empty();
        }
        RoomEntity e = entity.get();
        Room loaded = rooms.computeIfAbsent(code,
                c -> new Room(c, new PlaybackState(e.getVideoId(), false, e.getPositionSeconds()),
                        props.maxParticipantsPerRoom()));
        return Optional.of(loaded);
    }

    /** Only rooms that are currently live in memory (never touches the database). */
    public Optional<Room> findActive(String rawCode) {
        Room room = rooms.get(normalize(rawCode));
        if (room == null || room.isClosed()) {
            return Optional.empty();
        }
        return Optional.of(room);
    }

    public List<Room> activeRooms() {
        return new ArrayList<>(rooms.values());
    }

    // ------------------------------------------------------------------ host key / owner

    /** True if this account created the room. */
    public boolean isOwner(String code, long accountId) {
        return roomRepo.findById(code)
                .map(e -> e.getOwnerId() != null && e.getOwnerId() == accountId)
                .orElse(false);
    }

    public boolean hostKeyMatches(String code, String hostKey) {
        if (hostKey == null || hostKey.isBlank()) {
            return false;
        }
        return roomRepo.findById(code)
                .map(e -> MessageDigest.isEqual(
                        e.getHostKey().getBytes(StandardCharsets.UTF_8),
                        hostKey.getBytes(StandardCharsets.UTF_8)))
                .orElse(false);
    }

    // ------------------------------------------------------------------ persistence

    @Transactional
    public void persistPlayback(String code, PlaybackSnapshot s) {
        roomRepo.findById(code).ifPresent(e -> {
            e.setVideoId(s.videoId());
            e.setPlaying(s.playing());
            e.setPositionSeconds(s.position());
            e.setUpdatedAt(Instant.now());
            roomRepo.save(e);
        });
    }

    /** Drops an empty room from memory after saving where the video stopped. */
    public void evictIfEmpty(Room room) {
        if (room.closeIfEmpty()) {
            rooms.remove(room.getCode(), room);
            persistPlayback(room.getCode(), room.freezeForIdle());
            log.info("Room {} is empty, unloaded from memory", room.getCode());
        }
    }

    public void evictEmptyRooms() {
        for (Room room : activeRooms()) {
            evictIfEmpty(room);
        }
    }

    // ------------------------------------------------------------------ chat

    @Transactional
    public ChatMessageEntity saveChat(String roomCode, String userId, String username, String text) {
        return chatRepo.save(new ChatMessageEntity(roomCode, userId, username, text));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> recentChat(String roomCode) {
        List<ChatMessageEntity> list = new ArrayList<>(chatRepo.findTop50ByRoomCodeOrderByIdDesc(roomCode));
        Collections.reverse(list);
        List<Map<String, Object>> views = new ArrayList<>();
        for (ChatMessageEntity m : list) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("id", m.getId());
            v.put("userId", m.getUserId());
            v.put("username", m.getUsername());
            v.put("text", m.getText());
            v.put("ts", m.getCreatedAt().toEpochMilli());
            views.add(v);
        }
        return views;
    }

    // ------------------------------------------------------------------ housekeeping

    @Transactional
    public int purgeOldRooms() {
        Instant cutoff = Instant.now().minus(props.roomRetentionDays(), ChronoUnit.DAYS);
        int removed = 0;
        for (RoomEntity e : roomRepo.findByUpdatedAtBefore(cutoff)) {
            if (rooms.containsKey(e.getCode())) {
                continue; // still live
            }
            chatRepo.deleteByRoomCode(e.getCode());
            roomRepo.delete(e);
            removed++;
        }
        if (removed > 0) {
            log.info("Purged {} inactive rooms", removed);
        }
        return removed;
    }

    private static String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
