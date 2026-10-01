package com.watchparty.exception;

/** The sender's role is not allowed to perform the requested action. */
public class PermissionDeniedException extends WsException {

    public PermissionDeniedException(String message) {
        super("FORBIDDEN", message);
    }
}
