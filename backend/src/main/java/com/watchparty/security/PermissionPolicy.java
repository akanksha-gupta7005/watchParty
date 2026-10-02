package com.watchparty.security;

import com.watchparty.exception.PermissionDeniedException;
import com.watchparty.model.Action;
import com.watchparty.model.Role;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for "which role may do what".
 * Every handler calls check() BEFORE it touches any room state.
 */
@Component
public class PermissionPolicy {

    private final Map<Role, Set<Action>> rules = new EnumMap<>(Role.class);

    public PermissionPolicy() {
        // Host: everything except asking for approval (the host IS the approver)
        rules.put(Role.HOST, EnumSet.complementOf(EnumSet.of(Action.REQUEST_ACTION)));

        // Moderator: playback + reviewing requests (+ chat)
        rules.put(Role.MODERATOR, EnumSet.of(
                Action.PLAY, Action.PAUSE, Action.SEEK, Action.CHANGE_VIDEO,
                Action.RESOLVE_REQUEST, Action.CHAT, Action.REACT));

        // Participant / Viewer: watch, chat, and ask a manager to approve a change
        rules.put(Role.PARTICIPANT, EnumSet.of(Action.REQUEST_ACTION, Action.CHAT, Action.REACT));
        rules.put(Role.VIEWER, EnumSet.of(Action.REQUEST_ACTION, Action.CHAT, Action.REACT));
    }

    public boolean can(Role role, Action action) {
        return rules.get(role).contains(action);
    }

    public void check(Role role, Action action) {
        if (!can(role, action)) {
            throw new PermissionDeniedException(
                    "Role " + role + " is not allowed to do " + action.name().toLowerCase().replace('_', ' '));
        }
    }
}
