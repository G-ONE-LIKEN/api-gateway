package com.plataforma.gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilsTest {

    private static final String SECRET = "dev-secret-key-must-be-at-least-256-bits-long-for-hs256";

    private JwtUtils jwtUtils;
    private Key key;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils(SECRET);
        key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    private String buildToken(Long userId, String role, long expirationMs) {
        return Jwts.builder()
                .setSubject("user@test.com")
                .claim("id", userId)
                .claim("role", role)
                .claim("permissions", List.of("project:read", "project:create"))
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key)
                .compact();
    }

    @Test
    void validToken_returnsTrue() {
        String token = buildToken(1L, "ADMIN", 3_600_000);
        assertThat(jwtUtils.validateToken(token)).isTrue();
    }

    @Test
    void expiredToken_returnsFalse() {
        String token = buildToken(1L, "ADMIN", -1_000);
        assertThat(jwtUtils.validateToken(token)).isFalse();
    }

    @Test
    void tamperedToken_returnsFalse() {
        String token = buildToken(1L, "ADMIN", 3_600_000);
        String tampered = token.substring(0, token.length() - 4) + "xxxx";
        assertThat(jwtUtils.validateToken(tampered)).isFalse();
    }

    @Test
    void randomString_returnsFalse() {
        assertThat(jwtUtils.validateToken("not.a.jwt")).isFalse();
    }

    @Test
    void getUserId_extractsCorrectly() {
        String token = buildToken(42L, "BASIC", 3_600_000);
        assertThat(jwtUtils.getUserId(token)).isEqualTo(42L);
    }

    @Test
    void getRole_extractsCorrectly() {
        String token = buildToken(1L, "INVESTOR", 3_600_000);
        assertThat(jwtUtils.getRole(token)).isEqualTo("INVESTOR");
    }
}
