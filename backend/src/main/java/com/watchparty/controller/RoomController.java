package com.watchparty.controller;

import com.watchparty.service.RoomService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Small REST API. Everything live goes through the WebSocket at /ws.
 */
@RestController
@RequestMapping("/api")
public class RoomController {

    private final RoomService rooms;

    public RoomController(RoomService rooms) {
        this.rooms = rooms;
    }

    /** Creates a room. The returned hostKey proves ownership when the creator joins the socket. */
    @PostMapping("/rooms")
    public Map<String, Object> create() {
        RoomService.CreatedRoom created = rooms.createRoom();
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
