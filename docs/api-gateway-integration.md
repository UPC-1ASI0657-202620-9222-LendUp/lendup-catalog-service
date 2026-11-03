# Integración de Catálogo con LendUp API Gateway e Identidad

Referencia revisada: `UPC-1ASI0657-202620-9222-LendUp/lendup-api-gateway` en `main` (2026-11-03).

## Contrato público y Gateway

Catálogo escucha en el puerto 8082 dentro del contenedor. **Todas** sus rutas HTTP públicas comienzan con `/api/v1/catalogo`:

- `GET /api/v1/catalogo/categorias`, `GET /api/v1/catalogo/publicaciones`, `GET /api/v1/catalogo/publicaciones/{id}`: públicas.
- `POST /api/v1/catalogo/publicaciones`, `GET /api/v1/catalogo/mis-publicaciones`, escritura de imágenes y disponibilidades: requieren token Firebase.
- El Gateway **ya tiene** la ruta `/api/v1/catalogo,/api/v1/catalogo/**` y la envía al microservicio **solo cuando** se configura `CATALOG_SERVICE_URL`. De lo contrario, cae al monolito.
- No agregar `/api/v1/catalogo` en `CATALOG_SERVICE_URL`: debe ser el origen del backend, p. ej. `http://catalogo.internal:8082`.
- No eliminar ni cambiar la cabecera `Authorization: Bearer <Firebase-ID-token>`, los query params ni el multipart; el Gateway hace proxy sin reescritura.
- El Gateway centraliza CORS externo. Mantener las restricciones propias del servicio y las credenciales privadas fuera del Gateway.

La suite `GatewayContractTests` asegura el prefijo de ruta, la exposición pública de lecturas y la autenticación de operaciones de escritura.

## Relación interna Catálogo → Identidad (no pasa por el Gateway público)

Cuando llega una operación protegida, Catálogo verifica el token Firebase y llama directamente a:

```text
GET {IDENTITY_BASE_URL}/api/v1/estudiantes/me
Authorization: Bearer <mismo token Firebase>
```

El contrato de respuesta debe contener `usuario.id` como UUID interno (con formato estándar). El servicio de Identidad y Catálogo deben usar el mismo `FIREBASE_PROJECT_ID`.

**Importante en Production:** `IdentidadClient.ValidarBase` exige `https://` y certificado válido para `IDENTITY_BASE_URL`; la dirección HTTP `http://identity:8081` será rechazada. Para mantener Identidad privada en AWS, ofrecer una dirección HTTPS interna mediante un ALB/reverse-proxy TLS privado o un mecanismo equivalente. `IDENTITY_BASE_URL` es la URL base de ese endpoint, sin el segmento `/api/v1`. No se recomienda usar el Gateway público como intermediario de esta llamada interna.

No agregar lógica de resolución de propietario en el Gateway ni confiar en `X-User-Id`.

## Evitar conflictos de puertos en desarrollo local

El Gateway de otro equipo asume Reservas en el puerto local 8081 y Pagos en 8082. Si ejecutan simultáneamente todo LendUp en el **mismo host**:

```powershell
# En Identidad (con Firebase y MySQL ya configurados):
$env:PORT = "8085"
npm run start:dev

# En Catálogo (.env y base ya migrada):
$env:ASPNETCORE_ENVIRONMENT = "Development"
$env:SERVER_PORT = "8084"
$env:IDENTITY_BASE_URL = "http://localhost:8085"
dotnet run --project src/LendUp.Catalogo.Api --no-launch-profile
```

Y en el `.env` del contenedor del Gateway:

```dotenv
IDENTITY_SERVICE_URL=http://host.docker.internal:8085
CATALOG_SERVICE_URL=http://host.docker.internal:8084
```

El Gateway **necesita agregar la ruta de Identidad** (con prioridad inferior a Reputación), porque hoy no existe. Cuando los servicios están en contenedores independientes en ECS, pueden conservar sus puertos internos `8081` y `8082` y distinguirse por DNS/servicio y Security Groups.

## Pruebas de humo en AWS

1. Consultar `GET /api/v1/catalogo/categorias` por la URL pública del Gateway. Debe devolver las ocho categorías V2 (o la taxonomía final aprobada).
2. Crear un usuario con correo Firebase verificado en Identidad y comprobar `GET /api/v1/estudiantes/me` vía Gateway.
3. Crear una publicación vía Gateway con el mismo Bearer. Una respuesta 503 de Catálogo puede indicar `IDENTITY_BASE_URL` o credenciales Firebase incorrectas; consultar logs sin tokens.
4. Consultar `GET /api/v1/catalogo/publicaciones`, editar una publicación propia y probar una imagen real si Cloudinary está habilitado.
5. Confirmar que `/api/v1/estudiantes/{id}/reputacion` no cae en Identidad.

El CI valida rutas y servicios aislados. No sustituye la validación extremo a extremo real con el Gateway, Firebase y RDS.
