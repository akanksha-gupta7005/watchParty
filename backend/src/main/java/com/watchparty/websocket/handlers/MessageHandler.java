package com.watchparty.websocket.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.watchparty.websocket.Connection;
import java.util.Set;

/**
 * One handler per group of message types. Add a new event = add a new handler class;
 * the router discovers it automatically (open/closed principle).
 */
public interface MessageHandler {

    /** The message "type" values this handler is responsible for. */
    Set<String> types();

    void handle(String type, JsonNode payload, Connection conn);
}
