package com.watchparty.controller;

import com.watchparty.security.AuthUser;
import com.watchparty.service.AuthService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Register, log in, and "who am I". Everything else in the app needs the token these return. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record Credentials(String username, String password) {
    }

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Credentials body) {
        return toBody(auth.register(body.username(), body.password()));
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Credentials body) {
        return toBody(auth.login(body.username(), body.password()));
    }

    @GetMapping("/me")
    public Map<String, Object> me(@RequestHeader(name = "Authorization", required = false) String header) {
        AuthUser user = auth.requireUser(header);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", user.id());
        out.put("username", user.username());
        return out;
    }

    private static Map<String, Object> toBody(AuthService.AuthResult r) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", r.user().id());
        user.put("username", r.user().username());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("token", r.token());
        out.put("user", user);
        return out;
    }
}
