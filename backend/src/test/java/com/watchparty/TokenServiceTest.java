package com.watchparty;

import static org.assertj.core.api.Assertions.assertThat;

import com.watchparty.config.AppProperties;
import com.watchparty.security.AuthUser;
import com.watchparty.security.TokenService;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TokenServiceTest {

    private static AppProperties props(String secret, int days) {
        return new AppProperties("*", 30, 7, "M7lc1UVf-VE", secret, days, 100);
    }

    @Test
    void validTokenGivesBackTheUser() {
        TokenService tokens = new TokenService(props("test-secret-test-secret-123456", 7));
        String token = tokens.issue(42, "Riya");
        Optional<AuthUser> user = tokens.verify(token);
        assertThat(user).isPresent();
        assertThat(user.get().id()).isEqualTo(42);
        assertThat(user.get().username()).isEqualTo("Riya");
    }

    @Test
    void changedTokenIsRejected() {
        TokenService tokens = new TokenService(props("test-secret-test-secret-123456", 7));
        String token = tokens.issue(42, "Riya");
        String changed = token.substring(0, token.length() - 3) + "AAA";
        assertThat(tokens.verify(changed)).isEmpty();
        assertThat(tokens.verify("not-a-token")).isEmpty();
        assertThat(tokens.verify(null)).isEmpty();
    }

    @Test
    void expiredTokenIsRejected() {
        TokenService tokens = new TokenService(props("test-secret-test-secret-123456", -1));
        assertThat(tokens.verify(tokens.issue(1, "Old"))).isEmpty();
    }

    @Test
    void tokenFromAnotherSecretIsRejected() {
        TokenService a = new TokenService(props("test-secret-test-secret-123456", 7));
        TokenService b = new TokenService(props("another-secret-another-123456", 7));
        assertThat(b.verify(a.issue(1, "Riya"))).isEmpty();
    }
}
