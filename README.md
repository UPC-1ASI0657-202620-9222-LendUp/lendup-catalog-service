# lendup-catalog-service

Microservicio de Catálogo de LendUp, basado en Java 21 y Spring Boot 4.

## Estado del proyecto

Estructura DDD y modelo de dominio de publicaciones/disponibilidades implementados.
Se añadió el esquema inicial de **MySQL independiente** con Flyway y un
entorno local de base de datos usando Docker Compose.

**Aún faltan:** adaptadores JPA, APIs REST, seguridad Firebase, integración gRPC con
Identidad y contrato de snapshot con Reservas. Este repositorio no contiene un
microservicio de Catálogo integrado ni desplegado todavía.

## Desarrollo local

- [Configurar MySQL y Flyway con Docker Desktop](docs/database-setup.md)
- [Modelo de dominio y sus reglas](docs/domain-model.md)
- [Arquitectura y límites del bounded context](docs/architecture.md)

El puerto local propuesto para Catálogo es **8082** y el de MySQL es **3308**.
Nunca subir `.env`, credenciales Firebase o secretos de integración a Git.

## Tecnologías

Java 21, Spring Boot 4, Maven, MySQL 8.4, Flyway; Firebase, Swagger/OpenAPI
y gRPC quedan pendientes de implementación.
