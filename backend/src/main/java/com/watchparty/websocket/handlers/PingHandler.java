package com.watchparty.websocket.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.watchparty.websocket.Connection;
import com.watchparty.websocket.Outbound;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Application-level keep-alive so proxies and free hosting tiers do not drop idle sockets. */
@Component
public class PingHandler implements MessageHandler {

    @Override
    public Set<String> types() {
        return Set.of("ping");
    }

    @Override
    public void handle(String type, JsonNode payload, Connection conn) {
        conn.send(Outbound.msg("pong"));
    }
}
