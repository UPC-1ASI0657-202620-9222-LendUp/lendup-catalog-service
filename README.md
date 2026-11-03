# lendup-catalog-service

Microservicio de Catálogo de LendUp, desarrollado con Java 21, Spring Boot 4, MySQL 8.4 y arquitectura orientada al dominio (DDD).

## Funcionalidades

- Gestión de categorías, publicaciones y disponibilidad ofrecida, con reglas de negocio y persistencia mediante JPA y Flyway.
- API REST con validación, actualizaciones parciales, control de estados y permisos del propietario.
- Autenticación con Firebase Admin y resolución del UUID interno mediante el microservicio de Identidad.
- Adaptador de almacenamiento de imágenes en Cloudinary y documentación OpenAPI/Swagger.
- Pruebas automatizadas de dominio, aplicación, persistencia, API y seguridad. Referencia de verificación local: 97 pruebas aprobadas.

## Documentación

- [Arquitectura y límites del contexto](docs/architecture.md)
- [Modelo de dominio](docs/domain-model.md)
- [Casos de uso](docs/use-cases.md)
- [MySQL y migraciones Flyway](docs/database-setup.md)
- [Persistencia JPA](docs/persistence.md)
- [API REST](docs/rest-api.md)
- [Firebase e integración con Identidad](docs/auth-identity.md)
- [Imágenes y Cloudinary](docs/cloudinary.md)
- [OpenAPI y Swagger](docs/openapi.md)

## Configuración local

- API: `http://localhost:8082/api/v1/catalogo`.
- MySQL de Catálogo: `127.0.0.1:3308`.
- La autenticación está deshabilitada por defecto (`CATALOGO_FIREBASE_ENABLED=false`). Para activar las rutas protegidas se requieren credenciales válidas de Firebase e `IDENTITY_BASE_URL`.
- Los endpoints protegidos obtienen la identidad del token verificado; no aceptan identificadores de propietario proporcionados por el cliente.
- Los archivos `.env`, las credenciales de servicio y los tokens deben permanecer fuera del control de versiones.

## Integraciones

La comunicación HTTP con Identidad y el acceso autenticado a publicaciones se han verificado en un entorno local. Permanecen pendientes de validación extremo a extremo la subida y eliminación de imágenes con Cloudinary y el contrato de ocupación efectiva con Reservas. Cada microservicio conserva su propia base de datos.
