package com.watchparty.security;

import com.watchparty.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * Creates and checks login tokens. A token is:   base64(userId|expiry|username) . base64(HMAC signature)
 *
 * It is "stateless": the server needs no session table. It only has to check the signature with
 * its secret key. If anyone changes the token, the signature no longer matches. This also works
 * when several backend servers share the same secret, which helps scaling.
 */
@Service
public class TokenService {

    private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DEC = Base64.getUrlDecoder();

    private final byte[] secret;
    private final long ttlMillis;

    public TokenService(AppProperties props) {
        if (props.authSecret() == null || props.authSecret().length() < 16) {
            throw new IllegalStateException("AUTH_SECRET must be at least 16 characters long");
        }
        this.secret = props.authSecret().getBytes(StandardCharsets.UTF_8);
        this.ttlMillis = TimeUnit.DAYS.toMillis(props.authTokenDays());
    }

    public String issue(long userId, String username) {
        long expires = System.currentTimeMillis() + ttlMillis;
        String raw = userId + "|" + expires + "|" + username;
        String payload = ENC.encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        return payload + "." + sign(payload);
    }

    /** Returns the user if the token is genuine and not expired, otherwise empty. */
    public Optional<AuthUser> verify(String token) {
        if (token == null) {
            return Optional.empty();
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            return Optional.empty();
        }
        String payload = token.substring(0, dot);
        String signature = token.substring(dot + 1);

        byte[] expected = sign(payload).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.UTF_8))) {
            return Optional.empty();
        }
        try {
            String raw = new String(DEC.decode(payload), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 3);
            if (parts.length != 3) {
                return Optional.empty();
            }
            long id = Long.parseLong(parts[0]);
            long expires = Long.parseLong(parts[1]);
            if (expires < System.currentTimeMillis()) {
                return Optional.empty();
            }
            return Optional.of(new AuthUser(id, parts[2]));
        } catch (IllegalArgumentException e) {
            return Optional.empty(); // bad base64 or bad number
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return ENC.encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC is not available", e);
        }
    }
}
