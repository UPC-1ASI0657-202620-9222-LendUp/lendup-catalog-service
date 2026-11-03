# Plan de migración de Catálogo

## Referencia y alcance
Base: origin/develop. Java permanece activo hasta completar equivalencia. Las 97 pruebas son referencia histórica, no resultados de esta migración.

## Inventario y equivalencia prevista
| Java | .NET previsto | Criterio |
|---|---|---|
| domain/model y specification | Domain | Invariantes, estados libres, intervalos semiabiertos, cobertura por un intervalo |
| application/service | Application | UUID interno, autorización de propietario, orquestación |
| JPA y Flyway V1 | Infrastructure / EF Core | Cuatro tablas, CHAR(36), decimales y DATETIME(6) idénticos |
| Controladores y DTO | Api | 16 rutas, arrays, camelCase, Location, 204 sin cuerpo |
| ParchePublicacion | Fusión dentro de transacción bloqueada | Omitidos intactos, null opcionales, rechazo de campos desconocidos |
| Firebase e Identidad | SDK Admin y HttpClient | Revocación, email_verified, mismo Bearer, usuario.id |
| Cloudinary | Adaptador SDK y puerto de almacenamiento | Firma, MIME y firma binaria, ocho imágenes, compensación |
| Sin caché | Redis (capacidad nueva) | TTL, invalidación tras commit, resiliencia y métricas |
| Listado público | LINQ con filtros (capacidad nueva) | nombre, categoría, campus, universidad y periodo combinables |

## Hallazgos
El PATCH Java lee antes del bloqueo; la fusión .NET debe ocurrir tras SELECT FOR UPDATE en la misma transacción. El listado Java reconstruye disponibilidades por publicación; .NET debe usar carga agrupada. No hay definiciones RNF04/RNF15 en README ni docs; no se atribuyen umbrales inventados. Categorías públicas inactivas y publicaciones no visibles retornan 404. Todas las transiciones entre los tres estados están permitidas.

## Dependencias y versiones
Se verificará el proveedor oficial MySql.EntityFrameworkCore 10.0.12 y sus dependencias EF Core 10 mediante restore y pruebas reales. No se usará Pomelo 9 con EF 10. SDK .NET 10 local aislado para no alterar el sistema. Fuentes: https://www.nuget.org/packages/MySql.EntityFrameworkCore/10.0.12 y https://dotnet.microsoft.com/download/dotnet/10.0.

## Datos y rollback
No ejecutar EnsureCreated/EnsureDeleted/Migrate al iniciar. Mantener V1 y flyway_schema_history. Para una base nueva vacía, ejecutar Flyway V1 explícitamente; para existentes validar esquema sin repetir DDL. Respaldar con mysqldump --single-transaction y ensayar restauración en una base separada antes del cambio. No se proponen índices adicionales hasta disponer de EXPLAIN real. Mantener volumen catalog-mysql-data. Probar exclusivamente en contenedores efímeros independientes. Rollback: detener .NET y volver al commit Java, con esquema V1 sin cambios; no ejecutar dos escritores sobre datos personales durante comparación.

## Caché y concurrencia
MySQL es fuente de verdad. Bloquear fila padre para todas las mutaciones y fusionar PATCH dentro del bloqueo. La invalidación de caché debe resistir fallos y evitar resucitar datos ocultos; comprobar visibilidad desde SQL antes de servir datos cacheados. Nunca cachear consultas privadas. No usar KEYS ni locks Redis como sustituto de transacciones.

## Riesgos
Compatibilidad efectiva del proveedor; carreras caché/commit; compensación remota no atómica; fechas locales frente a UTC técnico; integración externa sin credenciales reales; diferencias de validación DTO/dominio. Pruebas deben demostrar equivalencia antes de retirar Java.

## Etapas y aceptación
A auditoría y plan; B dominio/aplicación; C persistencia; D HTTP; E seguridad; F imágenes; G filtros/Redis; H pruebas y CI; I sustitución sólo si H cumple. Cada etapa requiere commit coherente. Aceptación: build y suites reales, cuatro tablas y concurrencia MySQL 8.4, Redis hit/TTL/invalidación/fallo, 16 contratos HTTP y seguridad, comparación Java/.NET aislada, documentación y CI. Los casos no ejecutados deben quedar explícitamente pendientes.
