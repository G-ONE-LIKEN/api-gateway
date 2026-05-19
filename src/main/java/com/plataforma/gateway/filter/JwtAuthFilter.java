package com.plataforma.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataforma.gateway.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;

import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private final JwtUtils jwtUtils;
    private final ObjectMapper objectMapper;

    // Rutas completamente públicas (cualquier método)
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/login"
    );

    // Rutas públicas solo para un método específico
    private static final Map<String, HttpMethod> PUBLIC_METHOD_PATHS = Map.of(
            "/api/users", HttpMethod.POST   // registro de usuario
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();

        if (isPublic(path, method)) {
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return rejectUnauthorized(exchange, "Token ausente");
        }

        String token = authHeader.substring(7);
        if (!jwtUtils.validateToken(token)) {
            return rejectUnauthorized(exchange, "Token inválido o expirado");
        }

        ServerHttpRequest mutated = request.mutate()
                .header("X-User-Id", String.valueOf(jwtUtils.getUserId(token)))
                .header("X-User-Role", jwtUtils.getRole(token))
                .build();

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private boolean isPublic(String path, HttpMethod method) {
        if (PUBLIC_PATHS.contains(path)) {
            return true;
        }
        HttpMethod publicMethod = PUBLIC_METHOD_PATHS.get(path);
        return publicMethod != null && publicMethod.equals(method);
    }

    private Mono<Void> rejectUnauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "success", false,
                "message", message
        );

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            bytes = "{\"success\":false,\"message\":\"Unauthorized\"}".getBytes();
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
