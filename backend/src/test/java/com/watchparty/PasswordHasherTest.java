package com.watchparty;

import static org.assertj.core.api.Assertions.assertThat;

import com.watchparty.security.PasswordHasher;
import org.junit.jupiter.api.Test;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void rightPasswordMatchesAndWrongOneDoesNot() {
        String hash = hasher.hash("secret123");
        assertThat(hasher.matches("secret123", hash)).isTrue();
        assertThat(hasher.matches("secret124", hash)).isFalse();
    }

    @Test
    void hashIsSaltedAndDoesNotContainThePassword() {
        String h1 = hasher.hash("secret123");
        String h2 = hasher.hash("secret123");
        assertThat(h1).isNotEqualTo(h2);
        assertThat(h1).doesNotContain("secret123");
    }

    @Test
    void brokenHashNeverMatches() {
        assertThat(hasher.matches("x", "garbage")).isFalse();
        assertThat(hasher.matches("x", null)).isFalse();
    }
}
