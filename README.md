# lendup-catalog-service

Microservicio de Catálogo de LendUp (Java 21 y Spring Boot 4).

## Estado del proyecto

- **DDD**: modelo de publicaciones, categorías, disponibilidad e imágenes.
- **Base de datos independiente**: MySQL 8.4 con Flyway V1 y Docker Compose.
- **Persistencia (paso 5)**: entidades JPA, repositorios Spring Data y
  adaptadores transaccionales para Catálogo.
- **Pendiente**: casos de uso REST, seguridad Firebase, integración con
  Identidad y Reservas, y clientes Cloudinary.

## Desarrollo local y documentación

- [Preparar MySQL y Flyway](docs/database-setup.md)
- [Ejecutar pruebas de persistencia](docs/persistence.md)
- [Modelo de dominio](docs/domain-model.md)
- [Límites arquitectónicos](docs/architecture.md)

MySQL local: **127.0.0.1:3308**, Catálogo: **localhost:8082**.

Nunca subir el archivo `.env`, tokens Firebase u otras credenciales a Git.
