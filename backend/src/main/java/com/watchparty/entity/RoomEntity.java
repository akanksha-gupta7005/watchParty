package com.watchparty.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Persistent part of a room. Live data (participants, sockets) stays in memory.
 */
@Entity
@Table(name = "rooms")
public class RoomEntity {

    @Id
    @Column(length = 12)
    private String code;

    /** Secret known only to the creator; lets them reclaim the Host seat. */
    @Column(name = "host_key", nullable = false, length = 64)
    private String hostKey;

    @Column(name = "video_id", nullable = false, length = 16)
    private String videoId;

    @Column(nullable = false)
    private boolean playing;

    @Column(name = "position_seconds", nullable = false)
    private double positionSeconds;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RoomEntity() {
        // required by JPA
    }

    public RoomEntity(String code, String hostKey, String videoId) {
        this.code = code;
        this.hostKey = hostKey;
        this.videoId = videoId;
        this.playing = false;
        this.positionSeconds = 0;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getCode() { return code; }
    public String getHostKey() { return hostKey; }
    public String getVideoId() { return videoId; }
    public void setVideoId(String videoId) { this.videoId = videoId; }
    public boolean isPlaying() { return playing; }
    public void setPlaying(boolean playing) { this.playing = playing; }
    public double getPositionSeconds() { return positionSeconds; }
    public void setPositionSeconds(double positionSeconds) { this.positionSeconds = positionSeconds; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
