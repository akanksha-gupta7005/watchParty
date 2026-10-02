package com.watchparty.security;

/** The logged-in person, as read from a valid login token. */
public record AuthUser(long id, String username) {
}
