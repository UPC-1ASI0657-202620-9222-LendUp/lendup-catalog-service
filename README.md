# lendup-catalog-service

Microservicio de Catálogo de LendUp (Java 21 y Spring Boot 4).

## Estado

1. Inicialización de Spring Boot y estructura DDD.
2. Dominio: publicaciones, categorías, condiciones y disponibilidades.
3. MySQL 8.4 independiente, Flyway V1 y Docker Compose.
4. Entidades JPA, repositorios Spring Data y adaptadores.
5. **Paso 6: casos de uso y servicios de aplicación** para gestionar publicaciones,
   estados, categorías y disponibilidades; pruebas unitarias y de integración.

**Aún faltan:** endpoints REST, seguridad Firebase, comunicación con Identidad y
Reservas, y cliente Cloudinary.

## Guías

- [Paso 4: MySQL y Flyway](docs/database-setup.md)
- [Paso 5: Persistencia JPA](docs/persistence.md)
- [Paso 6: Casos de uso, seguridad y pruebas](docs/use-cases.md)
- [Modelo de dominio](docs/domain-model.md)
- [Arquitectura DDD](docs/architecture.md)

Entorno local: MySQL `127.0.0.1:3308`, aplicación `localhost:8082`.
No subir `.env`, contraseñas ni credenciales Firebase a Git.
