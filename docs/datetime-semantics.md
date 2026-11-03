# Fechas locales de disponibilidad y DATETIME(6)

## Contrato definitivo

Las disponibilidades de LendUp representan fecha y hora local de negocio. Se conserva literalmente el valor almacenado en MySQL: `2026-12-12 09:00:00` se devuelve como `2026-12-12T09:00:00`. No se aplican zonas, offsets ni conversiones UTC. Esta decisión resuelve la divergencia encontrada durante la migración; no se conserva el desplazamiento accidental del cliente Java.

`DateTimeKind.Unspecified` se exige al guardar fechas de negocio. El mapeo de `desde` y `hasta` es explícitamente `DATETIME(6)` con precisión de seis decimales. Al materializar, el convertidor de EF marca Unspecified sin alterar ticks ni hora. No llama a ToUniversalTime, ToLocalTime ni a conversores de zona. Los filtros LINQ usan el mismo mapeo. El serializador escribe ISO local sin Z/offset y conserva los microsegundos significativos; cero microsegundos se omiten como en el contrato anterior.

Los timestamps técnicos creado_en/actualizado_en mantienen su escritura en UTC y están fuera del convertidor de disponibilidad. `OldGuids=true` impide la interpretación automática de UUID textuales por MySql.Data; no es una opción de zona horaria y no se usa para convertir fechas.

## Investigación de Java

Versiones efectivas extraídas del JAR de referencia: Hibernate 7.2.25.Final y MySQL Connector/J 9.7.0. application.yaml configura serverTimezone=UTC, sin una zona JDBC de Hibernate explícita. La JVM del host usa America/Lima.

Se ejecutó una prueba JDBC aislada con el mismo Connector/J sobre MySQL 8.4. La consulta fue `SELECT CAST('2026-12-12 09:00:00.123456' AS DATETIME(6))`; no lee ni escribe datos personales.

| Zona JVM | preserveInstants | getString | getObject(LocalDateTime.class) | getTimestamp().toLocalDateTime() |
|---|---|---|---|---|
| America/Lima | true | 09:00:00.123456 | 09:00:00.123456 | 04:00:00.123456 |
| America/Lima | false | 09:00:00.123456 | 09:00:00.123456 | 09:00:00.123456 |
| UTC | true | 09:00:00.123456 | 09:00:00.123456 | 09:00:00.123456 |
| UTC | false | 09:00:00.123456 | 09:00:00.123456 | 09:00:00.123456 |

El bytecode del Hibernate realmente empaquetado confirma que LocalDateTimeJavaType puede usar JDBC TIMESTAMP (93), que TimestampJdbcType extrae con ResultSet.getTimestamp y que LocalDateTimeJavaType.wrap transforma Timestamp mediante toLocalDateTime. Esa ruta introduce una representación de instante entre dos valores que debían ser locales. Con preservación de instantes, la conexión UTC y la JVM Lima difieren cinco horas. La columna DATETIME y la consulta SQL siguen conteniendo 09:00; el desplazamiento sucede en la ruta de conversión del cliente.

La [documentación oficial de Connector/J](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-datetime-types-processing.html) describe serverTimezone como alias de connectionTimeZone y su interacción con preserveInstants. El [descriptor de Hibernate](https://github.com/hibernate/hibernate-orm/blob/main/hibernate-core/src/main/java/org/hibernate/type/descriptor/java/LocalDateTimeJavaType.java) documenta las rutas Timestamp/LocalDateTime; la versión concreta se contrastó con javap sobre 7.2.25.Final. No se atribuye el fallo a MySQL ni se modifica la base para compensarlo.

La comparación HTTP anterior con JVM UTC era útil para aislar esta causa, pero no se considera prueba de equivalencia con el Java ejecutado en Lima. La diferencia se registra como una corrección intencional del adaptador conforme al contrato local autorizado. No deben restarse ni sumarse cinco horas a los registros existentes.

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

El alcance es el estado de esa base en el momento de la auditoría. No acredita otros despliegues no conectados ni permite inferir qué hora pretendía un cliente antiguo al crear cada registro. La regla adoptada conserva el dato literal existente, sin reinterpretarlo ni corregirlo retrospectivamente.

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
