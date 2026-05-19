# API Gateway — LIKEN

Punto de entrada único para todos los microservicios de la plataforma LIKEN. Valida JWT, enruta requests y maneja CORS centralmente.

## Arquitectura

```
Frontend (5173)
      │
      ▼
API Gateway (8090)         ← validación JWT + CORS
      │
      ├─── /api/auth/**          ──▶  backend-adm-usuarios (8080)
      ├─── /api/users/**         ──▶  backend-adm-usuarios (8080)
      ├─── /api/roles/**         ──▶  backend-adm-usuarios (8080)
      ├─── /api/permissions/**   ──▶  backend-adm-usuarios (8080)
      ├─── /api/projects/*/investments ──▶ backend-adm-usuarios (8080)
      └─── /api/projects/**      ──▶  backend-proyectos    (8081)
```

## Responsabilidades

- **Enrutamiento**: redirige cada request al microservicio correspondiente según el path.
- **Autenticación centralizada**: valida el JWT antes de que el request llegue al backend. Si el token es inválido o ausente, responde `401` sin tocar los servicios downstream.
- **Propagación de identidad**: agrega los headers `X-User-Id` y `X-User-Role` a cada request autorizado para que los backends puedan leer la identidad del usuario sin re-parsear el token.
- **CORS**: gestiona los headers CORS centralmente para el frontend.

## Rutas y autenticación

| Método | Path | Backend | JWT requerido |
|--------|------|---------|---------------|
| `POST` | `/api/auth/login` | adm-usuarios | No |
| `POST` | `/api/users` | adm-usuarios | No (registro público) |
| `*` | `/api/auth/**` | adm-usuarios | Sí |
| `*` | `/api/users/**` | adm-usuarios | Sí |
| `*` | `/api/roles/**` | adm-usuarios | Sí |
| `*` | `/api/permissions/**` | adm-usuarios | Sí |
| `*` | `/api/projects/*/investments` | adm-usuarios | Sí |
| `*` | `/api/projects/**` | backend-proyectos | Sí |

> La ruta de inversiones (`/api/projects/*/investments`) vive en `backend-adm-usuarios` y está definida **antes** del catch-all de proyectos para que tome prioridad.

## Stack

- Java 21
- Spring Boot 3.2.4
- Spring Cloud Gateway 2023.0.1 (reactivo / WebFlux)
- jjwt 0.11.5

## Requisitos

- JDK 21
- Maven 3.9+
- `backend-adm-usuarios` corriendo en puerto 8080
- `backend-proyectos` corriendo en puerto 8081

## Configuración

Todas las propiedades se pueden sobrescribir con variables de entorno:

| Variable | Default | Descripción |
|----------|---------|-------------|
| `PORT` | `8090` | Puerto del gateway |
| `JWT_SECRET` | `dev-secret-key-...` | Clave HMAC-SHA256 compartida con los backends |
| `FRONTEND_URL` | `http://localhost:5173` | Origen permitido en CORS |
| `USUARIOS_URL` | `http://localhost:8080` | URL base de backend-adm-usuarios |
| `PROYECTOS_URL` | `http://localhost:8081` | URL base de backend-proyectos |

> En producción siempre definir `JWT_SECRET` como variable de entorno, nunca dejar el default.

## Levantar en local

```bash
mvn spring-boot:run
```

O con variables de entorno:

```bash
JWT_SECRET=mi-clave-secreta mvn spring-boot:run
```

El gateway queda disponible en `http://localhost:8090`.

## Estructura del proyecto

```
src/main/java/com/plataforma/gateway/
├── Application.java                  # Entry point
├── filter/
│   └── JwtAuthFilter.java            # GlobalFilter: valida JWT e inyecta headers
└── security/
    └── JwtUtils.java                 # Parseo y validación de tokens JWT
```

## Tests

```bash
mvn test
```

Hay tres capas de tests:

| Clase | Tipo | Qué cubre |
|-------|------|-----------|
| `JwtUtilsTest` | Unitario | Validación de firma, expiración y extracción de claims |
| `JwtAuthFilterTest` | Unitario (mocks) | Lógica del filtro: rutas públicas, token ausente/inválido/válido, headers propagados |
| `GatewayRoutingTest` | Integración (WireMock) | Enrutamiento correcto de cada path, bloqueo sin token, headers `X-User-Id`/`X-User-Role` recibidos por el backend |
