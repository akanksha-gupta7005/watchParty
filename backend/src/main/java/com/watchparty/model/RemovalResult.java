package com.watchparty.model;

/** Outcome of removing someone from a room. newHost is null unless the host left. */
public record RemovalResult(Participant removed, Participant newHost) {
}
