# lendup-catalog-service

Microservicio de Catálogo de LendUp (Java 21 y Spring Boot 4, MySQL 8.4).

## Progreso

1. Inicialización de Spring Boot y estructura DDD.
2. Dominio: publicaciones, categorías, condiciones y disponibilidades.
3. MySQL 8.4 independiente, Flyway V1 y Docker Compose.
4. Entidades JPA, repositorios Spring Data y adaptadores.
5. Paso 6: casos de uso y servicios de aplicación validados (45 pruebas aprobadas en Windows).
6. **Paso 7: API REST versionada, DTOs, Bean Validation, PATCH parcial, manejo de errores
   y controles de acceso fail-closed. Pruebas HTTP agregadas; pendientes de ejecutar localmente.**

## Endpoints

Base: `http://localhost:8082/api/v1/catalogo`

- Públicos: `GET /categorias`, `GET /categorias/{id}`,
  `GET /publicaciones`, `GET /publicaciones/{id}`.
- Propietario verificado: `GET /mis-publicaciones`; `POST /publicaciones`;
  `PUT/PATCH /publicaciones/{id}`, `PATCH /publicaciones/{id}/estado`,
  y `GET/POST/PUT/DELETE` de sus disponibilidades.

**Atención:** aún no hay autenticación HTTP Firebase. Las operaciones protegidas
rechazarán accesos externos hasta implementar el adaptador de Identidad; jamás
usar un `X-User-Id` enviado por un cliente como autenticación.

## Documentación

- [REST, ejemplos y pruebas](docs/rest-api.md)
- [Casos de uso](docs/use-cases.md)
- [Persistencia JPA](docs/persistence.md)
- [MySQL y Flyway](docs/database-setup.md)
- [Modelo de dominio](docs/domain-model.md)
- [Arquitectura DDD](docs/architecture.md)

Para instalar/ejecutar: MySQL en `127.0.0.1:3308`, servicio en `localhost:8082`.
No subir `.env`, contraseñas ni credenciales Firebase a Git.

**Pendiente:** verificación Firebase/Identidad, contratos internos con Reservas,
imágenes Cloudinary y documentación OpenAPI.
