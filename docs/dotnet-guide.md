# Catálogo ASP.NET Core

## Estado y arquitectura

La solución `LendUp.Catalogo.sln` contiene cuatro capas y cuatro proyectos de prueba. El código Java se conserva como referencia hasta completar la comparación de comportamientos. Consultar `migration-plan.md` y `migration-validation.md` antes del cambio de runtime.

`Domain` contiene Publicacion, Categoria, DetallesPublicacion, CondicionesPublicacion, EstadoPublicacion, Disponibilidad, PeriodoDisponibilidad, DisponibilidadSpecification y reglas de imagen. No referencia ASP.NET, EF ni Redis. `Application` define puertos y casos de uso; comprueba propietario y orquesta cambios. `Infrastructure` implementa persistencia, caché y adaptadores externos. `Api` compone DI, autenticación, controladores y contratos HTTP.

## Herramientas y versiones

SDK .NET 10 estable, framework net10.0. Oracle MySql.EntityFrameworkCore 10.0.12 selecciona EF Core/Relational 10.0.12 y MySql.Data 26.10.0 para net10.0. La compatibilidad declarada se verificó en el nuspec restaurado; las pruebas MySQL se documentan por separado. Redis usa Microsoft.Extensions.Caching.StackExchangeRedis 10.0.0; FirebaseAdmin 3.4.0; CloudinaryDotNet 1.27.5; Swashbuckle.AspNetCore 10.0.1 y Microsoft.OpenApi 2.7.5. Pruebas xUnit 2.9.3, Test SDK 17.14.1, Moq 4.20.72, WebApplicationFactory 10.0.12 y Testcontainers 4.8.1. SSH.NET 2026.0.0 sustituye la dependencia transitiva vulnerable de Testcontainers.

Instalar SDK desde https://dotnet.microsoft.com/download/dotnet/10.0 y Docker Desktop. `global.json` permite SDK 10.0 estable con roll-forward de feature bands.

## Windows PowerShell

Ejecutar desde la raíz del repositorio:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
# Editar .env con credenciales propias; no sobrescribir un .env existente.
docker compose up -d mysql redis
# Cargar sólo el archivo local de configuración confiable; no ejecutarlo como script.
Get-Content .env | ForEach-Object {
    if ($_ -match '^([A-Z][A-Z0-9_]*)=(.*)$') {
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
    }
}
$env:ASPNETCORE_ENVIRONMENT = 'Development'
dotnet restore LendUp.Catalogo.sln
dotnet build LendUp.Catalogo.sln --configuration Release --no-restore
dotnet test LendUp.Catalogo.sln --configuration Release --no-build
dotnet run --project src/LendUp.Catalogo.Api --no-launch-profile
```

Una base nueva debe inicializarse explícitamente antes de ejecutar el servicio; levantar MySQL no crea las tablas. Una base existente con Flyway V1 no requiere DDL. No iniciar Java y .NET como escritores simultáneos sobre datos personales.

Puerto HTTP 8082. Desde el host MySQL usa 127.0.0.1:3308 y Redis 127.0.0.1:6379. Desde Compose los nombres son `mysql:3306` y `redis:6379`; localhost dentro de un contenedor es el propio contenedor. `docker compose --profile dotnet up -d --build api` usa el Dockerfile de .NET, usuario de runtime sin privilegios y autenticación deshabilitada de forma segura. Para habilitar integraciones, añadir variables y un montaje de credenciales de sólo lectura mediante un override privado, fuera de Git.

## Configuración y secretos

`.env.example` contiene sólo placeholders. .NET lee variables de entorno; no carga .env automáticamente. `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` configuran MySQL. `MYSQL_ROOT_PASSWORD` sólo corresponde a Compose. El servicio usa usuario normal. Mantener el nombre de proyecto Compose y el volumen `catalog-mysql-data` existentes; cambiar el nombre de proyecto puede seleccionar otro volumen.

`REDIS_ENABLED`, `REDIS_CONNECTION`, `REDIS_TTL_SECONDS` y `SERVER_PORT` controlan caché y HTTP. `CORS_ORIGINS` contiene orígenes exactos separados por comas (inicial http://localhost:5173). `OPENAPI_ENABLED` permite desactivar Swagger y OpenAPI.

`CATALOGO_FIREBASE_ENABLED`, `FIREBASE_PROJECT_ID` y `GOOGLE_APPLICATION_CREDENTIALS` habilitan Firebase. Nunca guardar la cuenta de servicio en el repositorio. El SDK comprueba firma, proyecto, expiración y revocación; se exige email_verified. Sin configuración las lecturas públicas funcionan y las protegidas fallan con 401 sin Bearer y 503 si solicitan una integración no disponible. No hay autenticación simulada en producción. Las implementaciones falsas se registran exclusivamente en proyectos de pruebas.

`IDENTITY_BASE_URL` apunta al contrato existente GET /api/v1/estudiantes/me, por defecto http://localhost:8081 en desarrollo. Se reenvía el mismo Bearer; sólo usuario.id UUID pasa al principal. HTTP se acepta sólo en localhost/127.0.0.1 y desarrollo; remoto exige HTTPS. Conexión 3 s y plazo HTTP 4 s; redirecciones deshabilitadas. X-User-Id y propietarios del body no conceden permisos.

Cloudinary requiere `CLOUDINARY_ENABLED`, `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`. Sin configuración retorna 503. Se valida MIME y cabecera JPEG/PNG/WebP, máximo 5 MiB y ocho imágenes, reutilizando el primer orden libre. El SDK firma la subida y genera IDs lendup/publicaciones/{id}/{uuid}. Sólo se exponen UUID, URL HTTPS y orden, no IDs remotos ni secretos.

## REST, fechas y ejemplos

Base /api/v1/catalogo. Se mantienen los 16 métodos/rutas de `rest-api.md` y `cloudinary.md`. Categorías, publicaciones visibles y sus imágenes son públicas. Mis publicaciones y administración requieren propietario autenticado. Respuestas JSON camelCase; enums ACTIVA, PAUSADA, DADA_DE_BAJA como cadenas. Arrays conservados; POST retorna 201 y Location; DELETE retorna 204 sin cuerpo. Errores contienen codigo, mensaje, instante UTC y campos. Códigos 400 VALIDACION/SOLICITUD_INVALIDA, 403 ACCESO_DENEGADO, 404 NO_ENCONTRADO, 409 DISPONIBILIDAD_SUPERPUESTA, 503 ALMACENAMIENTO_NO_DISPONIBLE o DEPENDENCIA_NO_DISPONIBLE; autenticación ausente o inválida 401.

PUT reemplaza detalles y condiciones completos. PATCH fusiona recursivamente bajo bloqueo SQL, rechaza campos desconocidos, id, propietario y estado; campos omitidos intactos. Null elimina sólo valores opcionales; null obligatorio retorna 400. Cambiar estado usa el endpoint dedicado.

Disponibilidad acepta fechas locales ISO sin Z ni offset. Periodos semiabiertos [desde,hasta), adyacencia permitida, solapamiento rechazado, actualización excluye su ID. La búsqueda requiere cobertura por un mismo intervalo; no fusiona adyacentes ni consulta reservas. Timestamps técnicos se escriben en UTC en DATETIME(6), como Java.

```powershell
$base = 'http://localhost:8082/api/v1/catalogo'
Invoke-RestMethod "$base/categorias"
Invoke-RestMethod "$base/publicaciones?nombre=Camara&campus=Monterrico&desde=2026-12-12T09:00:00&hasta=2026-12-12T12:00:00"
$headers = @{ Authorization = "Bearer $env:FIREBASE_ID_TOKEN" }
Invoke-RestMethod "$base/mis-publicaciones" -Headers $headers
$patch = @{ detalles = @{ titulo = 'Cámara actualizada' }; condiciones = @{ garantiaMonetaria = $null } } | ConvertTo-Json -Depth 5
Invoke-RestMethod "$base/publicaciones/$publicacionId" -Method Patch -Headers $headers -ContentType 'application/json' -Body $patch
$periodo = '{"desde":"2026-12-12T09:00:00","hasta":"2026-12-12T12:00:00"}'
Invoke-RestMethod "$base/publicaciones/$publicacionId/disponibilidades" -Method Post -Headers $headers -ContentType 'application/json' -Body $periodo
```

Swagger: /swagger-ui/index.html. OpenAPI: /v3/api-docs (alias redirige a /v3/api-docs/v1). Salud: /actuator/health y /health; comprueba MySQL. Redis no determina salud del servicio ni bloquea lecturas cuando falla. Logs JSON sin tokens ni contraseñas.

## EF Core, Flyway y reversión

El contexto mapea CHAR(36) como strings en persistencia y Guid en dominio; DECIMAL(12,2) dinero, DECIMAL(9,6) coordenadas, DATETIME(6), textos y nombres V1. La conexión usa OldGuids=true para impedir la interpretación automática de CHAR(36) como Guid por MySql.Data; todos los UUID están mapeados explícitamente como texto de longitud fija y no se usan columnas binarias. No hay FK a Identidad/Reservas. No hay EnsureCreated, EnsureDeleted ni migraciones automáticas.

Para una base vacía ejecutar Flyway 11.14.1 (la misma versión de la referencia Java) con `src/main/resources/db/migration` como ubicación y credenciales en variables/configuración privada. V1 permanece intacta. El helper exige inspección vacía y rechaza cualquier tabla existente:

```powershell
$mysqlContainer = docker compose ps -q mysql
./scripts/initialize-empty-database.ps1 -MySqlContainer $mysqlContainer
```

Ejecutarlo sólo para una instalación nueva, tras cargar DB_USERNAME/DB_PASSWORD; el contenedor Flyway comparte la red del MySQL seleccionado y recibe secretos únicamente por entorno. Para una base existente conservar flyway_schema_history y comprobar columnas/índices contra V1 antes del despliegue. No aplicar V1 manualmente sobre tablas existentes. Las pruebas ejecutan V1 únicamente dentro de una base efímera nueva.

Antes de cualquier cambio futuro: respaldo mysqldump --single-transaction con archivo de opciones privado (sin contraseña en argumentos), restauración de ensayo en base separada, migración incremental revisada y plan de reversión. Esta implementación no modifica el esquema. Para rollback detener .NET, volver al commit Java y reiniciar Java con las mismas variables; revisar que no haya dos escritores. Nunca ejecutar docker compose down -v ni eliminar volúmenes de datos.

Modificar una publicación adquiere SELECT ... FOR UPDATE parametrizado, reconstruye agregado, autoriza y valida dentro de la misma transacción READ COMMITTED, guarda y confirma. El bloqueo padre serializa disponibilidad e imágenes entre instancias; no hay lock C# como garantía de integridad.

## Redis y rendimiento

Cache-aside mediante IDistributedCache y StackExchange.Redis; prefijo lendup:catalogo:v1 y SHA-256 de tipo, filtros normalizados y revisión SQL. TTL inicial 60 s configurable. No se cachean respuestas privadas. Los semáforos acotados sólo reducen solicitudes duplicadas dentro de una instancia; la integridad reside en MySQL.

La generación de caché se deriva de metadatos persistidos: todos los IDs y actualizado_en de publicaciones, y categorías con su visibilidad/nombre/código. Cada mutación actualiza actualizado_en en el mismo commit, incluidas disponibilidad e imágenes. Así una caída de Redis durante una escritura no pierde invalidaciones: la siguiente lectura usa otra generación, sin KEYS ni borrado global. Publicaciones e imágenes comprueban además visibilidad SQL. Una consulta iniciada antes de un commit puede completar con su snapshot anterior; una consulta posterior observa la generación nueva.

Esta estrategia conserva las cuatro tablas sin introducir una quinta tabla de generaciones. Tiene coste O(n) de metadatos por lectura pública y debe evaluarse con datos representativos antes de producción. Cambios externos directos a publicaciones deben actualizar actualizado_en; no escribir Java y .NET a la vez. Para grandes catálogos considerar una migración incremental de generación/outbox transaccional. No se afirma una mejora de latencia sin mediciones.

Métricas Meter `LendUp.Catalogo.Cache`: catalogo.cache.hit, miss, error y duration (ms). Redis caído provoca consulta MySQL y registro sanitizado. Las entradas de generaciones antiguas expiran por TTL. Ver `performance.md` para carga sintética y limitaciones.

## Consistencia remota

Si falla guardar o confirmar tras una subida, se intenta eliminar el recurso remoto con timeout independiente. Si falla también la compensación puede quedar una imagen huérfana que requiere conciliación. Al eliminar se borra remoto antes de confirmar metadatos; si falla el commit, la fila puede apuntar a un recurso ya eliminado. MySQL y Cloudinary no ofrecen una transacción distribuida; un outbox sería una evolución explícita. El SDK usado no ofrece CancellationToken en DestroyAsync: WaitAsync limita la espera, pero no garantiza cancelar la operación remota. Pruebas usan almacenamiento falso y no eliminan imágenes reales.

## Pruebas y CI

Las cuatro suites cubren dominio, aplicación, contratos y HTTP/persistencia con Testcontainers. Docker debe estar disponible. Contenedores MySQL 8.4/Redis se crean con puertos efímeros y se eliminan al terminar; no usan Compose ni volúmenes existentes. WebApplicationFactory reemplaza Firebase, Identidad y Cloudinary sólo en pruebas. `dotnet test` ejecuta la integración sin opt-in silencioso. GitHub Actions restaura, compila Release y ejecuta pruebas en Ubuntu con Docker. Resultados reales y pendientes se registran en migration-validation.md.
