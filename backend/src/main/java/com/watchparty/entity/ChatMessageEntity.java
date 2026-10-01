package com.watchparty.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "chat_messages", indexes = @Index(name = "idx_chat_room", columnList = "room_code"))
public class ChatMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_code", nullable = false, length = 12)
    private String roomCode;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(nullable = false, length = 64)
    private String username;

    @Column(name = "body", nullable = false, length = 1000)
    private String text;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ChatMessageEntity() {
        // required by JPA
    }

    public ChatMessageEntity(String roomCode, String userId, String username, String text) {
        this.roomCode = roomCode;
        this.userId = userId;
        this.username = username;
        this.text = text;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getRoomCode() { return roomCode; }
    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getText() { return text; }
    public Instant getCreatedAt() { return createdAt; }
}
