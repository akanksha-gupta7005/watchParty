package com.watchparty.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A registered account. The password is never stored, only a salted PBKDF2 hash. */
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Name as the person typed it (shown in rooms). */
    @Column(nullable = false, length = 24)
    private String username;

    /** Lower-case copy used to keep names unique ("Riya" and "riya" are the same account). */
    @Column(name = "username_key", nullable = false, unique = true, length = 24)
    private String usernameKey;

    @Column(name = "password_hash", nullable = false, length = 200)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserEntity() {
        // required by JPA
    }

    public UserEntity(String username, String usernameKey, String passwordHash) {
        this.username = username;
        this.usernameKey = usernameKey;
        this.passwordHash = passwordHash;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getUsernameKey() { return usernameKey; }
    public String getPasswordHash() { return passwordHash; }
    public Instant getCreatedAt() { return createdAt; }
}
