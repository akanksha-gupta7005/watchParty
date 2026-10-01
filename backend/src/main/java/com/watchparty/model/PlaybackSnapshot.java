package com.watchparty.model;

/** Immutable copy of the playback state, safe to hand to the persistence layer. */
public record PlaybackSnapshot(String videoId, boolean playing, double position) {
}
