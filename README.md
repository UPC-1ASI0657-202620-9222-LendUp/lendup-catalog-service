# lendup-catalog-service

Microservicio de Catálogo de LendUp (Java 21, Spring Boot 4, MySQL 8.4).

## Estado

- Pasos 1–6: dominio DDD, Flyway, persistencia y casos de uso.
- Paso 7: API REST, DTOs, PATCH y controles de acceso;
  **61 pruebas aprobadas en Windows**.
- **Paso 8: Firebase Admin, integración con Identidad vía
  `GET /api/v1/estudiantes/me`, validación del UUID interno, permisos
  de propietario y pruebas de seguridad; 82 pruebas aprobadas en Windows.**

- **Paso 9: endpoints Cloudinary y gestión de fotos; pendiente de pruebas locales.**

## Documentación

- [Paso 9: Cloudinary](docs/cloudinary.md)
- [Paso 8: Firebase e Identidad](docs/auth-identity.md)
- [Paso 7: API REST](docs/rest-api.md)
- [Paso 6: Casos de uso](docs/use-cases.md)
- [Paso 5: Persistencia JPA](docs/persistence.md)
- [Paso 4: MySQL y Flyway](docs/database-setup.md)
- [Modelo de dominio](docs/domain-model.md)

API local: `http://localhost:8082/api/v1/catalogo`.
MySQL propio: `127.0.0.1:3308`.

**Seguridad:** la autenticación Firebase está desactivada hasta que
`CATALOGO_FIREBASE_ENABLED=true` y las variables de Firebase e
Identidad se configuren correctamente. Los endpoints del propietario
permanecen protegidos y no aceptan IDs de usuario enviados por el cliente.
No publicar `.env`, tokens o credenciales.

Pendiente: probar en vivo la subida/eliminación con Cloudinary cuando Identidad esté
operativo, acordar los contratos de Reservas y validar la integración completa.
