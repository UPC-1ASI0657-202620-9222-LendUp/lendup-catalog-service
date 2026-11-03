# Handoff de Catálogo para despliegue en AWS

La imagen y las migraciones se mantienen en este repositorio; este documento no crea ni configura infraestructura AWS.

## Artefacto
- Construir desde `main`: `docker build -t lendup-catalog-service:<git-sha> .`.
- Imagen ASP.NET Core 10; puerto de contenedor `8082`. Variables `SERVER_PORT=8082` y `ASPNETCORE_ENVIRONMENT=Production`.
- `GET /health` verifica **conexión MySQL**, no la presencia de las tablas ni Firebase/Cloudinary/Identidad. Añadir smoke tests HTTP reales.
- Redis es caché opcional: `REDIS_ENABLED=false` permite un primer despliegue con MySQL únicamente.

## Variables del servicio
| Variable | Configuración |
| --- | --- |
| `DB_HOST`, `DB_PORT` | Host de RDS privado; puerto `3306` |
| `DB_NAME` | `lendup_catalogo` |
| `DB_USERNAME`, `DB_PASSWORD` | Usuario propio; contraseña desde gestor de secretos |
| `CATALOGO_FIREBASE_ENABLED` | `true` para que funcionen endpoints protegidos |
| `FIREBASE_PROJECT_ID` | Mismo proyecto Firebase que Identidad |
| `GOOGLE_APPLICATION_CREDENTIALS` | Ruta al JSON de credenciales Firebase Admin entregado mediante montaje privado |
| `IDENTITY_BASE_URL` | URL **HTTPS** de Identidad, p. ej. `https://identity.<dominio-real>/` |
| `CLOUDINARY_ENABLED` | `true` para subida/borrado de imágenes |
| `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` | Credenciales de Cloudinary; mantener secretos fuera del repositorio |
| `REDIS_ENABLED`, `REDIS_CONNECTION`, `REDIS_TTL_SECONDS` | Redis opcional; usar hostname/puerto privado si está habilitado |
| `CORS_ORIGINS` | URL(s) HTTPS exacta(s) del frontend |
| `OPENAPI_ENABLED` | `false` si no se desea exponer Swagger |

El valor por defecto de `IDENTITY_BASE_URL` sólo sirve en desarrollo. El código rechaza HTTP remoto en producción y necesita HTTPS con certificado válido. `GOOGLE_APPLICATION_CREDENTIALS` debe apuntar a **un archivo**, no al contenido JSON de un secreto de ECS. No introducir contraseñas en Dockerfile ni Git.

## Esquemas y datos iniciales
- `V1__create_catalog_schema.sql`: tablas de Catálogo existentes, **no se modifica**.
- `V2__seed_catalog_categories.sql`: ocho categorías iniciales de ejemplo, con UUID y códigos estables.
- En RDS, ejecutar `flyway migrate` con las credenciales del usuario de migración y la carpeta `src/LendUp.Catalogo.Infrastructure/Database/Migrations`, en una tarea controlada con acceso privado al RDS **antes** de arrancar la API.
- En una base vacía se aplican V1 y V2; en una base existente con historial de Flyway V1 sólo debe aplicarse V2. Tomar respaldo y validar la historia previamente.
- El helper de PowerShell `scripts/initialize-empty-database.ps1` está pensado sólo para un contenedor local vacío; **no** se debe ejecutar sobre un RDS existente.
- Revisar y aprobar la taxonomía de categorías con el equipo de producto antes de ir a producción.

## Orden de despliegue y smoke tests
1. Provisionar RDS privado, credenciales separadas, conectividad y reglas de Security Group.
2. Aplicar migraciones Flyway y comprobar que la tabla `categorias` tiene datos.
3. Desplegar Identidad primero, comprobar `GET /api/v1/estudiantes/me` con token Firebase válido y perfil registrado.
4. Desplegar Catálogo con `IDENTITY_BASE_URL` HTTPS, Firebase y Cloudinary configurados; confirmar `GET /health`.
5. Confirmar `GET /api/v1/catalogo/categorias` (no vacío), listado público, creación de publicación autenticada y subida de imagen.
6. Si se usa Redis, verificar latencia y fallos controlados. Activar logs/alertas, y comprobar CORS para el frontend real.

CI comprueba build/tests y construcción del contenedor. El despliegue real con RDS/Firebase/Cloudinary y los health checks de ECS deben validarse en AWS antes de anunciar el servicio como operativo.
