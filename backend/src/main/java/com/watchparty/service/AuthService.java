package com.watchparty.service;

import com.watchparty.entity.UserEntity;
import com.watchparty.exception.ApiException;
import com.watchparty.repository.UserRepository;
import com.watchparty.security.AuthUser;
import com.watchparty.security.PasswordHasher;
import com.watchparty.security.TokenService;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** Register, log in, and read the user from a token. */
@Service
public class AuthService {

    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{3,20}$");
    private static final int MIN_PASSWORD = 6;
    private static final int MAX_PASSWORD = 72;

    public record AuthResult(String token, AuthUser user) {
    }

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final TokenService tokens;
    /** Used to spend the same time when a username does not exist (makes guessing names harder). */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordHasher hasher, TokenService tokens) {
        this.users = users;
        this.hasher = hasher;
        this.tokens = tokens;
        this.dummyHash = hasher.hash("not-a-real-password");
    }

    public AuthResult register(String username, String password) {
        String name = cleanUsername(username);
        if (password == null || password.length() < MIN_PASSWORD || password.length() > MAX_PASSWORD) {
            throw new ApiException(400, "Password must be " + MIN_PASSWORD + " to " + MAX_PASSWORD + " characters");
        }
        String key = name.toLowerCase(Locale.ROOT);
        if (users.existsByUsernameKey(key)) {
            throw new ApiException(409, "That username is already taken");
        }
        try {
            UserEntity saved = users.save(new UserEntity(name, key, hasher.hash(password)));
            AuthUser user = new AuthUser(saved.getId(), saved.getUsername());
            return new AuthResult(tokens.issue(user.id(), user.username()), user);
        } catch (DataIntegrityViolationException e) {
            // two people registered the same name at the same moment
            throw new ApiException(409, "That username is already taken");
        }
    }

    public AuthResult login(String username, String password) {
        String key = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        Optional<UserEntity> found = key.isEmpty() ? Optional.empty() : users.findByUsernameKey(key);
        if (found.isEmpty()) {
            hasher.matches(password == null ? "" : password, dummyHash);
            throw new ApiException(401, "Wrong username or password");
        }
        UserEntity u = found.get();
        if (!hasher.matches(password, u.getPasswordHash())) {
            throw new ApiException(401, "Wrong username or password");
        }
        AuthUser user = new AuthUser(u.getId(), u.getUsername());
        return new AuthResult(tokens.issue(user.id(), user.username()), user);
    }

    /** Accepts "Bearer <token>" or a bare token. Throws 401 if it is missing, fake or expired. */
    public AuthUser requireUser(String tokenOrHeader) {
        String token = tokenOrHeader == null ? "" : tokenOrHeader.trim();
        if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
            token = token.substring(7).trim();
        }
        return tokens.verify(token)
                .orElseThrow(() -> new ApiException(401, "Please log in again"));
    }

    private static String cleanUsername(String username) {
        String name = username == null ? "" : username.trim();
        if (!USERNAME.matcher(name).matches()) {
            throw new ApiException(400, "Username must be 3 to 20 letters, numbers or underscores");
        }
        return name;
    }
}
