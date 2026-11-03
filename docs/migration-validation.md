# Validación de migración Java / ASP.NET Core

## Resultado y condición de cambio

Implementación .NET disponible para revisión. **La sustitución final de Java no está autorizada por la evidencia actual de fechas históricas.** Se conserva Java en la estructura activa; no se modifica main/develop ni se ejecuta merge. La aplicación .NET funciona de forma independiente de Maven/Spring.

## Resultados ejecutados

Validación local del 3 de noviembre de 2026:

| Verificación | Resultado real |
|---|---|
| .NET restore y build Release | Correctos; build con cero errores y cero advertencias |
| Domain.Tests | 16 aprobadas |
| Application.Tests | 12 aprobadas |
| Api.Tests | 22 aprobadas |
| Integration.Tests | 18 aprobadas contra MySQL 8.4 y Redis 7.4 |
| Total .NET | 68 ejecutadas, 68 aprobadas, ninguna omitida |
| Java / Maven / JDK 21 | 97 ejecutadas, 97 aprobadas, ninguna omitida, BUILD SUCCESS |
| Docker multi-stage .NET | Imagen local construida correctamente; no publicada |
| Comparación HTTP Java UTC / .NET | Cinco consultas públicas con JSON equivalente y ruta protegida sin Bearer con 401 equivalente |
| Comparación HTTP Java Lima / .NET | Categorías equivalentes; publicaciones difieren en horas de disponibilidad |

Las suites Java se ejecutaron con una base MySQL efímera nueva. Las suites .NET usan Testcontainers y no acceden a los volúmenes de Compose existentes. La referencia histórica de 97 pruebas se volvió a ejecutar, no se asumió. Los fixtures SQL son compatibles con V1; no se leyó ni transformó información personal existente.

## Diferencia de fechas que bloquea el retiro de Java

Comparación de dos bases aisladas inicializadas con el mismo fixture SQL:

| Valor | Java con JVM America/Lima | Java con JVM UTC | .NET |
|---|---|---|---|
| DATETIME desde = 2026-12-12 09:00:00 | 2026-12-12T04:00:00 | 2026-12-12T09:00:00 | 2026-12-12T09:00:00 |
| DATETIME hasta = 2026-12-12 12:00:00 | 2026-12-12T07:00:00 | 2026-12-12T12:00:00 | 2026-12-12T12:00:00 |

La referencia JDBC usa serverTimezone=UTC, mientras Hibernate/JVM puede operar con zona local del host. La nueva implementación trata DATETIME de negocio como DateTimeKind.Unspecified y no desplaza horas. Configurar la JVM de comparación en UTC aísla la diferencia y permite contrastar el resto del contrato; **no demuestra equivalencia de fechas con el despliegue Java en Lima**.

Antes del corte se debe contrastar una disponibilidad de origen conocido: hora enviada al crearla, respuesta de la API Java actual y valor DATETIME almacenado. Definir si la compatibilidad exigida conserva la hora visible actual o el literal de MySQL. No ejecutar UPDATE de fechas ni introducir una conversión implícita antes de esa decisión. Mantener copia de respaldo y validar cualquier estrategia en una copia controlada. No basta con fijar UTC para una prueba y declarar completa la migración.

## Equivalencia HTTP

Base /api/v1/catalogo. La suite FlujoDeLos16Endpoints ejecuta los 16 métodos/rutas; las pruebas adicionales cubren validación, autorización, concurrencia, caché y persistencia.

| Método / ruta | Implementación .NET | Evidencia |
|---|---|---|
| GET /categorias | Categorías activas, array | HTTP y comparación Java/.NET |
| GET /categorias/{id} | Sólo activa, 404 si oculta | HTTP y comparación Java/.NET |
| GET /publicaciones | Activas y categoría activa | HTTP, SQL y comparación condicionada por fechas |
| GET /publicaciones/{id} | Detalle visible | HTTP, caché, comparación condicionada por fechas |
| GET /mis-publicaciones | UUID interno autenticado | HTTP y rechazo sin Bearer |
| POST /publicaciones | ACTIVA, 201 y Location | HTTP, propietario desde principal |
| PUT /publicaciones/{id} | Reemplazo completo | HTTP |
| PATCH /publicaciones/{id} | Recursivo dentro de bloqueo | HTTP, null, campos desconocidos y concurrencia |
| PATCH /publicaciones/{id}/estado | Tres estados originales | HTTP, ocultación/invalidation |
| GET /publicaciones/{id}/disponibilidades | Sólo propietario | HTTP |
| POST /publicaciones/{id}/disponibilidades | 201 + Location, 409 al solapar | HTTP y cuatro solicitudes concurrentes |
| PUT /publicaciones/{id}/disponibilidades/{id} | Excluye su propio ID | HTTP y dominio |
| DELETE /publicaciones/{id}/disponibilidades/{id} | 204 sin cuerpo | HTTP y consulta posterior |
| GET /publicaciones/{id}/imagenes | Sólo publicación visible | HTTP y comparación Java/.NET |
| POST /publicaciones/{id}/imagenes | Multipart file, 201 + Location | HTTP, ocho imágenes, MIME/firma y orden reutilizado |
| DELETE /publicaciones/{id}/imagenes/{id} | 204 sin cuerpo | HTTP y consulta posterior |

## Equivalencia de reglas e integraciones

| Área Java | .NET | Estado |
|---|---|---|
| Agregado Publicacion, detalles y condiciones | Domain | Validaciones, edición, dinero decimal, propietario inmutable y estados probados |
| Intervalos y specification | Domain | Adyacencia, solapamiento, autoexclusión y cobertura por un intervalo probados |
| JPA/Flyway V1 | EF Core/MySQL | Cuatro tablas, decimales, UUID textuales, FK/únicos y lectura fixture probados |
| Transacciones y bloqueo pesimista | READ COMMITTED + FOR UPDATE | Sólo una inserción incompatible confirma; PATCH concurrentes conservan cambios |
| Firebase Admin | SDK .NET con checkRevoked=true | Llamada implementada; seguridad HTTP con puerto simulado, sin tokens reales |
| Identidad | HttpClient con plazos y HTTPS | Ruta, Bearer, UUID, JSON incompatible y 401/403/503 probados con handler simulado |
| Cloudinary | SDK .NET, subida firmada y compensación | Casos de uso con almacenamiento falso; sin llamadas ni borrados reales |
| Errores, JSON y fechas | DTO/middleware | HTTP 200/201/204/400/401/403/404/409/503 y fechas sin zona probados; diferencia histórica documentada |
| CORS, Swagger y salud | Middleware ASP.NET | Preflight autorizado/no autorizado, documento, UI y MySQL probados |

## Capacidades nuevas

- Filtros combinables nombre, categoriaId, campus, universidad, desde/hasta con LINQ traducido a SQL y AsNoTracking; dos consultas agrupadas para datos y disponibilidades, sin N+1.
- Redis cache-aside con TTL, claves versionadas por consulta/filtros/revisión persistida, resiliencia y métricas. No se cachean respuestas privadas.
- Pruebas de TTL, filtros distintos, serialización de accesos simultáneos, invalidación ante cambios de estado/disponibilidad/imágenes y caída de Redis.
- Carga sintética reproducible y EXPLAIN real; no se añaden índices sin justificación.
- GitHub Actions restore/build/test y Dockerfile .NET con usuario sin privilegios.

## Pendientes y límites

1. Resolver la semántica de fechas históricas y validar una copia controlada de datos realmente creados por el despliegue Java del equipo. No se ha tocado esa base.
2. No se ha probado Firebase/Identidad/Cloudinary con credenciales o servicios personales reales; los automatizados usan puertos simulados, como requiere la estrategia de pruebas.
3. La comparación HTTP entre runtimes cubre lecturas públicas y 401 sin Bearer; las escrituras autenticadas se validan por suites independientes, no por un comparador con Firebase real.
4. La generación de caché consulta metadatos O(n). El benchmark sintético no acredita rendimiento de producción; considerar generación/outbox transaccional con migración revisada para volúmenes grandes.
5. Una compensación Cloudinary puede fallar; existe riesgo de recurso huérfano o metadato sin binario cuando falla un commit después del borrado remoto.
6. El workflow está definido y las verificaciones locales se ejecutaron; registrar por separado el resultado remoto de GitHub Actions cuando esté disponible.

## Reproducción de contratos

`scripts/compare-contracts.ps1` compara JSON normalizado sin alterar arrays ni fechas. Requiere servicios de prueba con bases separadas restauradas del mismo fixture y UUID explícitos. No usa datos personales por defecto. Para Java UTC configurar -Duser.timezone=UTC; para reproducir la divergencia usar la zona real de la JVM de referencia. No normalizar ni ignorar fechas en el comparador.

Ver dotnet-guide.md para inicio en Windows, preservación de Flyway V1, secretos y rollback. Etapa I permanece pendiente hasta resolver el primer punto.
