package com.plataforma.gateway;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingTest {

    private static final String SECRET =
            "dev-secret-key-must-be-at-least-256-bits-long-for-hs256";

    @RegisterExtension
    static WireMockExtension usuariosWm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @RegisterExtension
    static WireMockExtension proyectosWm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void registerBackendUrls(DynamicPropertyRegistry registry) {
        registry.add("app.services.usuarios-url", usuariosWm::baseUrl);
        registry.add("app.services.proyectos-url", proyectosWm::baseUrl);
    }

    @Autowired
    private WebTestClient webTestClient;

    // --- sin token ---

    @Test
    void login_noToken_routesToUsuarios() {
        usuariosWm.stubFor(post(urlEqualTo("/api/auth/login"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"data\":\"fake-jwt\"}")));

        webTestClient.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"a@b.com\",\"password\":\"pass\"}")
                .exchange()
                .expectStatus().isOk();

        usuariosWm.verify(postRequestedFor(urlEqualTo("/api/auth/login")));
        proyectosWm.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void protectedRoute_noToken_returns401() {
        webTestClient.get().uri("/api/users")
                .exchange()
                .expectStatus().isUnauthorized();

        usuariosWm.verify(0, anyRequestedFor(anyUrl()));
        proyectosWm.verify(0, anyRequestedFor(anyUrl()));
    }

    // --- con token válido ---

    @Test
    void usersRoute_validToken_routesToUsuarios() {
        usuariosWm.stubFor(get(urlEqualTo("/api/users"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"data\":[]}")));

        webTestClient.get().uri("/api/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + buildToken())
                .exchange()
                .expectStatus().isOk();

        usuariosWm.verify(getRequestedFor(urlEqualTo("/api/users")));
        proyectosWm.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void projectsRoute_validToken_routesToProyectos() {
        proyectosWm.stubFor(get(urlEqualTo("/api/projects"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"data\":[]}")));

        webTestClient.get().uri("/api/projects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + buildToken())
                .exchange()
                .expectStatus().isOk();

        proyectosWm.verify(getRequestedFor(urlEqualTo("/api/projects")));
        usuariosWm.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void investmentsRoute_validToken_routesToUsuarios() {
        usuariosWm.stubFor(post(urlEqualTo("/api/projects/1/investments"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true}")));

        webTestClient.post().uri("/api/projects/1/investments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + buildToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"tokensToBuy\":5}")
                .exchange()
                .expectStatus().isCreated();

        usuariosWm.verify(postRequestedFor(urlEqualTo("/api/projects/1/investments")));
        proyectosWm.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void gatewayAddsUserIdHeader_toBackend() {
        usuariosWm.stubFor(get(urlPathEqualTo("/api/roles"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"data\":[]}")));

        webTestClient.get().uri("/api/roles")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + buildToken())
                .exchange()
                .expectStatus().isOk();

        usuariosWm.verify(getRequestedFor(urlPathEqualTo("/api/roles"))
                .withHeader("X-User-Id", equalTo("99"))
                .withHeader("X-User-Role", equalTo("ADMIN")));
    }

    // --- helper ---

    private String buildToken() {
        Key key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .setSubject("admin@test.com")
                .claim("id", 99L)
                .claim("role", "ADMIN")
                .claim("permissions", List.of("project:read"))
                .setExpiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(key)
                .compact();
    }
}
