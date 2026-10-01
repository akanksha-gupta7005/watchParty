package com.watchparty.websocket.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.watchparty.service.MembershipService;
import com.watchparty.websocket.Connection;
import java.util.Set;
import org.springframework.stereotype.Component;

/** join_room and leave_room. */
@Component
public class MembershipHandler implements MessageHandler {

    private final MembershipService membership;

    public MembershipHandler(MembershipService membership) {
        this.membership = membership;
    }

    @Override
    public Set<String> types() {
        return Set.of("join_room", "leave_room");
    }

    @Override
    public void handle(String type, JsonNode payload, Connection conn) {
        if ("join_room".equals(type)) {
            membership.join(conn, payload);
        } else {
            membership.leave(conn);
        }
    }
}
