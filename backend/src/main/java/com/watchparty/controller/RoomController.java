package com.watchparty.controller;

import com.watchparty.security.AuthUser;
import com.watchparty.service.AuthService;
import com.watchparty.service.RoomService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Small REST API. Everything live goes through the WebSocket at /ws.
 */
@RestController
@RequestMapping("/api")
public class RoomController {

    private final RoomService rooms;
    private final AuthService auth;

    public RoomController(RoomService rooms, AuthService auth) {
        this.rooms = rooms;
        this.auth = auth;
    }

    /** Creates a room. Needs a login. The creator is saved as the owner and becomes the Host. */
    @PostMapping("/rooms")
    public Map<String, Object> create(@RequestHeader(name = "Authorization", required = false) String header) {
        AuthUser user = auth.requireUser(header);
        RoomService.CreatedRoom created = rooms.createRoom(user.id());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", created.code());
        body.put("hostKey", created.hostKey());
        return body;
    }

    @GetMapping("/rooms/{code}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String code) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (!rooms.exists(code)) {
            body.put("error", "Room not found");
            return ResponseEntity.status(404).body(body);
        }
        body.put("code", RoomService.normalize(code));
        body.put("exists", true);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        return body;
    }
}
