package com.watchparty.model;

public enum Role {
    HOST,
    MODERATOR,
    PARTICIPANT,
    VIEWER;

    /** Host and Moderator can control playback and review requests. */
    public boolean isManager() {
        return this == HOST || this == MODERATOR;
    }
}
