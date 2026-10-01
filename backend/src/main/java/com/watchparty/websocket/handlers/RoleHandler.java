package com.watchparty.websocket.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.watchparty.model.Action;
import com.watchparty.security.PermissionPolicy;
import com.watchparty.service.MembershipService;
import com.watchparty.service.RoomService;
import com.watchparty.websocket.Connection;
import com.watchparty.websocket.Validation;
import java.util.Set;
import org.springframework.stereotype.Component;

/** assign_role, remove_participant, transfer_host. Host only. */
@Component
public class RoleHandler extends BaseHandler {

    private final MembershipService membership;

    public RoleHandler(RoomService rooms, PermissionPolicy policy, MembershipService membership) {
        super(rooms, policy);
        this.membership = membership;
    }

    @Override
    public Set<String> types() {
        return Set.of("assign_role", "remove_participant", "transfer_host");
    }

    @Override
    public void handle(String type, JsonNode payload, Connection conn) {
        Actor a = actor(conn);
        String targetId = Validation.requireText(payload, "userId", 64);

        switch (type) {
            case "assign_role" -> {
                policy.check(a.me().getRole(), Action.ASSIGN_ROLE);
                a.room().assignRole(a.me(), targetId, Validation.role(payload));
            }
            case "remove_participant" -> {
                policy.check(a.me().getRole(), Action.REMOVE_PARTICIPANT);
                membership.kick(a.room(), a.me(), targetId);
            }
            case "transfer_host" -> {
                policy.check(a.me().getRole(), Action.TRANSFER_HOST);
                a.room().transferHost(a.me(), targetId);
            }
            default -> throw new IllegalStateException("Unexpected type " + type);
        }
    }
}
