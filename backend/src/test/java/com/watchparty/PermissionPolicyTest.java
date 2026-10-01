package com.watchparty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.watchparty.exception.PermissionDeniedException;
import com.watchparty.model.Action;
import com.watchparty.model.Role;
import com.watchparty.security.PermissionPolicy;
import org.junit.jupiter.api.Test;

class PermissionPolicyTest {

    private final PermissionPolicy policy = new PermissionPolicy();

    @Test
    void hostCanDoEverythingExceptRequest() {
        for (Action a : Action.values()) {
            if (a != Action.REQUEST_ACTION) {
                assertThat(policy.can(Role.HOST, a)).as(a.name()).isTrue();
            }
        }
    }

    @Test
    void moderatorControlsPlaybackButNotRoles() {
        assertThat(policy.can(Role.MODERATOR, Action.SEEK)).isTrue();
        assertThat(policy.can(Role.MODERATOR, Action.CHANGE_VIDEO)).isTrue();
        assertThat(policy.can(Role.MODERATOR, Action.RESOLVE_REQUEST)).isTrue();
        assertThat(policy.can(Role.MODERATOR, Action.ASSIGN_ROLE)).isFalse();
        assertThat(policy.can(Role.MODERATOR, Action.REMOVE_PARTICIPANT)).isFalse();
        assertThat(policy.can(Role.MODERATOR, Action.TRANSFER_HOST)).isFalse();
    }

    @Test
    void participantAndViewerAreWatchOnly() {
        for (Role r : new Role[] {Role.PARTICIPANT, Role.VIEWER}) {
            assertThat(policy.can(r, Action.PLAY)).isFalse();
            assertThat(policy.can(r, Action.PAUSE)).isFalse();
            assertThat(policy.can(r, Action.SEEK)).isFalse();
            assertThat(policy.can(r, Action.CHANGE_VIDEO)).isFalse();
            assertThat(policy.can(r, Action.REQUEST_ACTION)).isTrue();
            assertThat(policy.can(r, Action.CHAT)).isTrue();
        }
    }

    @Test
    void checkThrowsForbidden() {
        assertThatThrownBy(() -> policy.check(Role.PARTICIPANT, Action.SEEK))
                .isInstanceOf(PermissionDeniedException.class);
    }
}
