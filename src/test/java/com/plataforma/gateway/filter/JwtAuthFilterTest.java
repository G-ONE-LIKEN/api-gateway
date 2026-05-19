package com.plataforma.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataforma.gateway.security.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JwtAuthFilterTest {

    private JwtUtils jwtUtils;
    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        jwtUtils = mock(JwtUtils.class);
        filter = new JwtAuthFilter(jwtUtils, new ObjectMapper());
    }

    // --- rutas públicas ---

    @Test
    void loginPath_noToken_passesThrough() {
        MockServerWebExchange exchange = exchangeFor("POST", "/api/auth/login", null);
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        filter.filter(exchange, ex -> { captured.set(ex); return Mono.empty(); }).block();

        assertThat(captured.get()).isNotNull();
        verifyNoInteractions(jwtUtils);
    }

    @Test
    void postUsers_noToken_passesThrough() {
        MockServerWebExchange exchange = exchangeFor("POST", "/api/users", null);
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        filter.filter(exchange, ex -> { captured.set(ex); return Mono.empty(); }).block();

        assertThat(captured.get()).isNotNull();
        verifyNoInteractions(jwtUtils);
    }

    @Test
    void getUsers_noToken_returns401() {
        MockServerWebExchange exchange = exchangeFor("GET", "/api/users", null);

        filter.filter(exchange, chain()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getUsers_malformedHeader_returns401() {
        MockServerWebExchange exchange = exchangeFor("GET", "/api/users", "Token abc");

        filter.filter(exchange, chain()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getUsers_invalidToken_returns401() {
        when(jwtUtils.validateToken("bad-token")).thenReturn(false);
        MockServerWebExchange exchange = exchangeFor("GET", "/api/users", "Bearer bad-token");

        filter.filter(exchange, chain()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getUsers_validToken_addsUserHeaders() {
        when(jwtUtils.validateToken("good-token")).thenReturn(true);
        when(jwtUtils.getUserId("good-token")).thenReturn(7L);
        when(jwtUtils.getRole("good-token")).thenReturn("ADMIN");

        MockServerWebExchange exchange = exchangeFor("GET", "/api/users", "Bearer good-token");
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        filter.filter(exchange, ex -> { captured.set(ex); return Mono.empty(); }).block();

        HttpHeaders headers = captured.get().getRequest().getHeaders();
        assertThat(headers.getFirst("X-User-Id")).isEqualTo("7");
        assertThat(headers.getFirst("X-User-Role")).isEqualTo("ADMIN");
    }

    // --- helpers ---

    private MockServerWebExchange exchangeFor(String method, String path, String authHeader) {
        MockServerHttpRequest.BaseBuilder<?> builder = switch (method) {
            case "POST" -> MockServerHttpRequest.post(path);
            case "PUT"  -> MockServerHttpRequest.put(path);
            case "DELETE" -> MockServerHttpRequest.delete(path);
            default     -> MockServerHttpRequest.get(path);
        };
        if (authHeader != null) {
            builder.header(HttpHeaders.AUTHORIZATION, authHeader);
        }
        return MockServerWebExchange.from(builder.build());
    }

    private GatewayFilterChain chain() {
        return exchange -> Mono.empty();
    }
}
