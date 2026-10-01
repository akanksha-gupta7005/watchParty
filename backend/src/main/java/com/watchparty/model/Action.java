package com.watchparty.model;

/** Everything a user may try to do that needs a permission check. */
public enum Action {
    PLAY,
    PAUSE,
    SEEK,
    CHANGE_VIDEO,
    ASSIGN_ROLE,
    REMOVE_PARTICIPANT,
    TRANSFER_HOST,
    RESOLVE_REQUEST,
    REQUEST_ACTION,
    CHAT
}
