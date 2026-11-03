# Fechas locales de disponibilidad y DATETIME(6)

## Contrato definitivo

Las disponibilidades de LendUp representan fecha y hora local de negocio. Se conserva literalmente el valor almacenado en MySQL: `2026-12-12 09:00:00` se devuelve como `2026-12-12T09:00:00`. No se aplican zonas, offsets ni conversiones UTC.

`DateTimeKind.Unspecified` se exige al guardar fechas de negocio. El mapeo de `desde` y `hasta` es explícitamente `DATETIME(6)` con precisión de seis decimales. Al materializar, el convertidor de EF marca Unspecified sin alterar ticks ni hora. No llama a ToUniversalTime, ToLocalTime ni a conversores de zona. Los filtros LINQ usan el mismo mapeo. El serializador escribe ISO local sin Z/offset y conserva los microsegundos significativos; cero microsegundos se omiten como en el contrato anterior.

Los timestamps técnicos creado_en/actualizado_en mantienen su escritura en UTC y están fuera del convertidor de disponibilidad. `OldGuids=true` impide la interpretación automática de UUID textuales por MySql.Data; no es una opción de zona horaria y no se usa para convertir fechas.

## Verificación de registros reales

El 3 de noviembre de 2026 se verificó exclusivamente la base de Catálogo vinculada al contenedor del repositorio, expuesta en 127.0.0.1:3308. No se accedió a la base de Identidad.

La auditoría abrió una transacción REPEATABLE READ con `SET SESSION TRANSACTION READ ONLY`, confirmó transaction_read_only=1 y verificó ambas columnas mediante information_schema: DATETIME, precisión 6. Comparó DATE_FORMAT SQL con lectura EF AsNoTracking y el serializador HTTP real. Las credenciales existentes se utilizaron únicamente en memoria; no se guardaron ni se mostraron valores de filas o UUID.

| Control | Resultado |
|---|---:|
| Disponibilidades reales verificadas | 1 |
| Diferencias de valor literal SQL/EF | 0 |
| Errores de DateTimeKind | 0 |
| Diferencias de JSON local | 0 |
| Entidades seguidas por ChangeTracker | 0 |
| Escrituras ejecutadas | 0 |
| Valores SQL antes/después | Sin cambios |

El alcance es el estado de esa base en el momento de la auditoría. No acredita otros despliegues no conectados ni permite inferir qué hora pretendía un cliente al crear cada registro. La regla adoptada conserva el dato literal existente, sin reinterpretarlo ni corregirlo retrospectivamente.

## Regresiones automatizadas

DateTimePersistenceTests ejecuta MySQL 8.4 real y cubre:

- Lectura de literales DATETIME(6) con sesiones +00:00, -05:00 y +09:00: mismos valores y Kind.Unspecified.
- Lectura HTTP sin Z/offset y sin cambio de hora, incluidos seis decimales.
- Creación HTTP, verificación independiente mediante DATE_FORMAT SQL y actualización con microsegundos.
- Cruce de medianoche y año; fracciones 123456, 654321, 999999 y 000001 sin pérdida.
- Lectura sin cambios seguida de SaveChanges=0 y comprobación SQL posterior.
- Rechazo de DateTimeKind.Utc y Local por el adaptador de persistencia de negocio.

Las escrituras de estas pruebas ocurren sólo en contenedores efímeros. No se ejecutan pruebas de escritura sobre la base real.

```powershell
dotnet test tests/LendUp.Catalogo.Integration.Tests --configuration Release --filter 'FullyQualifiedName~Datetime'
```
