package com.watchparty.exception;

/**
 * An error that is reported back to the sender as an {"type":"error"} message.
 * The connection stays open.
 */
public class WsException extends RuntimeException {

    private final String code;

    public WsException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
