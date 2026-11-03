# Rendimiento y caché de lectura

## Medición sintética

Ejecución local Windows, .NET 10, MySQL 8.4 y Redis 7.4 en Docker Desktop. Fixture: 200 publicaciones sintéticas de aproximadamente 500 caracteres de descripción; 30 lecturas secuenciales después de calentamiento, filtro campus. Se mide el caso de uso completo con acceso SQL a revisión y serialización de caché; no incluye la red HTTP del cliente. No representa tráfico de producción ni define un SLA.

| Modo | Media ms | p50 ms | p95 ms |
|---|---:|---:|---:|
| Sin Redis | 48.167 | 45.050 | 66.531 |
| Redis caliente | 20.996 | 19.997 | 28.607 |

Los resultados son de una ejecución reproducible, no un compromiso de latencia. La caché todavía consulta los metadatos SQL de generación; el coste O(n) requiere una evaluación con un catálogo representativo. No se localizaron las definiciones exactas RNF04 y RNF15 en README/docs del repositorio y no se inventan umbrales.

Una segunda ejecución simultánea con la construcción Docker mostró mayor variabilidad: sin Redis media 33.318 ms, p50 32.249 ms, p95 43.752 ms; con Redis media 41.067 ms, p50 22.630 ms, p95 117.639 ms. No se descarta esa muestra: evidencia que la media/p95 no mejoran consistentemente en un host compartido. Separar la carga y medir de nuevo con un entorno representativo antes de aceptar objetivos de rendimiento.

## Plan MySQL real

EXPLAIN sobre una consulta equivalente de publicaciones activas, categoría activa y campus, ordenada por creación/ID, con el fixture anterior:

| Tabla | Acceso | Índice elegido | Observación |
|---|---|---|---|
| categorias | ALL (una fila estimada) | ninguno | Using where; Using temporary; Using filesort |
| publicaciones | ref | idx_publicaciones_1 | Using index condition |

El tamaño sintético no justifica índices nuevos; se conservan los originales. La búsqueda de título usa Contains traducido a SQL y puede requerir escaneo para subcadenas. Antes de añadir un índice o FULLTEXT se debe revisar semántica, selectividad y EXPLAIN con volumen representativo.

## Reproducción

```powershell
dotnet test tests/LendUp.Catalogo.Integration.Tests --configuration Release --filter FullyQualifiedName~RendimientoSintetico --logger 'console;verbosity=normal'
```

El test crea sus contenedores y datos, informa latencias/plan y los elimina al terminar. Para carga HTTP de lectura sobre un despliegue de prueba explícito:

```powershell
./scripts/load-read.ps1 -BaseUrl http://localhost:8082 -Requests 100 -Concurrency 8
```

Repetir con REDIS_ENABLED=false y true, misma base de prueba, calentamiento y filtros. El script no inserta ni modifica datos. Conservar el entorno, tamaño de muestra y concurrencia al comparar resultados. Meter LendUp.Catalogo.Cache expone hit, miss, error y duración en ms; no se registran tokens ni claves con información personal.
