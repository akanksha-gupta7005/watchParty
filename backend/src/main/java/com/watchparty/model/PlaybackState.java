package com.watchparty.model;

/**
 * Server-side video state.
 *
 * The server does NOT tick every second. It stores the position at the moment of the
 * last change plus a timestamp, and computes the live position on demand:
 *   live = positionAtUpdate + (now - updatedAt)   (only while playing)
 *
 * Not thread-safe on its own: Room guards every access with its own lock.
 */
public class PlaybackState {

    private String videoId;
    private boolean playing;
    private double positionAtUpdate;
    private long updatedAtMillis;

    public PlaybackState(String videoId, boolean playing, double position) {
        this.videoId = videoId;
        this.playing = playing;
        this.positionAtUpdate = position;
        this.updatedAtMillis = System.currentTimeMillis();
    }

    public double currentPosition() {
        if (!playing) {
            return positionAtUpdate;
        }
        return positionAtUpdate + (System.currentTimeMillis() - updatedAtMillis) / 1000.0;
    }

    /** @param time position reported by the sender; if null, keep the server's own estimate */
    public void play(Double time) {
        double pos = (time != null) ? time : currentPosition();
        this.positionAtUpdate = pos;
        this.playing = true;
        this.updatedAtMillis = System.currentTimeMillis();
    }

    public void pause(Double time) {
        double pos = (time != null) ? time : currentPosition();
        this.positionAtUpdate = pos;
        this.playing = false;
        this.updatedAtMillis = System.currentTimeMillis();
    }

    public void seek(double time) {
        this.positionAtUpdate = time;
        this.updatedAtMillis = System.currentTimeMillis();
    }

    public void changeVideo(String newVideoId) {
        this.videoId = newVideoId;
        this.positionAtUpdate = 0;
        this.playing = true;
        this.updatedAtMillis = System.currentTimeMillis();
    }

    public String getVideoId() {
        return videoId;
    }

    public boolean isPlaying() {
        return playing;
    }

    public PlaybackSnapshot snapshot() {
        return new PlaybackSnapshot(videoId, playing, currentPosition());
    }
}
