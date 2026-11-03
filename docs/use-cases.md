# Paso 6 — Casos de uso y servicios de aplicación de Catálogo

## Interfaces de entrada

| Puerto | Operaciones |
|---|---|
| `GestionarPublicacionesUseCase` | `crear`, `actualizar`, `cambiarEstado` |
| `ConsultarPublicacionesUseCase` | `obtener`, `listarActivas`, `listarMisPublicaciones`, `periodoOfrecido` |
| `GestionarDisponibilidadUseCase` | `agregar`, `actualizar`, `eliminar`, `listar` |
| `ConsultarCategoriasUseCase` | `obtener`, `listarActivas` |

Las interfaces contienen records de comandos tipados. Los detalles y las condiciones
de una actualización se reciben **completos** en el caso de uso. En la capa REST,
el controlador fusiona los campos presentes en las peticiones de
actualización parcial del monolito con los valores actuales antes de invocar
`actualizar`; los campos omitidos deben conservarse. No tratar `null` como
una omisión para campos expresamente anulables sin definir el contrato HTTP.

## Servicios

- `PublicacionApplicationService`: creación y edición de publicaciones,
  consulta de categorías existentes, cambio de estados, búsquedas y cobertura
  de un periodo ofrecido.
- `DisponibilidadApplicationService`: altas, cambios y bajas de intervalos
  mediante el agregado `Publicacion`; consultas de disponibilidad del dueño.
- `CategoriaApplicationService`: consulta de categorías.

**Seguridad:** los comandos reciben el `actorUsuarioId` como UUID interno
de Identidad. **Este ID no se acepta del cuerpo, query string ni headers arbitrarios
de un cliente externo.** En REST se deriva del token Firebase verificado
y del mapeo autorizado de Identidad. La capa de aplicación contrasta el actor
contra el UUID del propietario al editar publicaciones/estados y al administrar
o consultar disponibilidades. `listarMisPublicaciones` solo lista por el actor
autenticado que se le pasa.

## Transacciones y errores

- Las escrituras usan `@Transactional` y bloquean la publicación
  mediante `buscarPorIdParaActualizar` **dentro de la misma transacción**.
  El adaptador JPA exige una transacción activa para esa lectura bloqueada.
- El dominio sigue validando que los periodos no se superpongan.
  `DisponibilidadSuperpuestaException` extiende `IllegalArgumentException`,
  permitiendo distinguir colisiones para responder **409** en REST.
- `RecursoNoEncontradoException`: publicación, categoría o intervalo
  inexistente (**404** propuesto).
- `AccesoDenegadoException`: actor diferente del propietario
  (**403** propuesto).
- No se introdujo bloqueo de cambios de estado desde `DADA_DE_BAJA`
  porque el monolito no lo imponía.
- El caso de uso verifica que exista la categoría al crear o modificar,
  apoyándose en la FK local.

## Límite con Reservas e Identidad

`periodoOfrecido` solamente indica si **un intervalo de Catálogo**
contiene por completo el periodo solicitado. **NO verifica** reservas confirmadas,
bloqueos, elegibilidad, vigencia, autorización interservicios ni si la publicación
está activa. Para la futura API interna de Reservas habrá que completar
esos contratos con los responsables de los otros microservicios.

Durante esta etapa no se crearon tablas, FKs ni consultas a contextos ajenos.
Los controladores REST y la integración Cloudinary se incorporaron posteriormente;
no se añaden tablas compartidas ni clientes gRPC/RabbitMQ.

## Pruebas

- `PublicacionApplicationServiceTest`: creación, propietario, estados,
  categorías, consultas y transacciones.
- `DisponibilidadApplicationServiceTest`: solapamientos, cambios,
  inexistentes, propiedad y transacciones.
- `CategoriaApplicationServiceTest`: recuperación y ausencia.
- `CatalogoApplicationIntegrationTest`: flujo completo real
  Spring Boot + MySQL de Docker, con rollback al finalizar.

### Ejecución de pruebas en Windows / VS Code

Requisitos: **Docker Desktop** con Engine activo y **VS Code** abierto
en la raíz de `lendup-catalog-service`. Verificación:

```powershell
git switch develop
git pull origin develop
docker compose up -d mysql
docker compose ps
```

El contenedor MySQL debe figurar como `healthy`. Configuración de Java 21,
Maven y contraseña local:

```powershell
$jdk21 = Get-ChildItem "C:\Program Files\Microsoft" -Directory -Filter "jdk-21*" | Select-Object -First 1 -ExpandProperty FullName
$env:JAVA_HOME = $jdk21
$env:Path = "$jdk21\bin;$env:Path"
$mvn = Get-ChildItem "$HOME\.m2\wrapper\dists" -Recurse -Filter mvn.cmd -File | Select-Object -First 1
$mavenHome = Split-Path (Split-Path $mvn.FullName -Parent) -Parent
$env:DB_PASSWORD = Read-Host "Contraseña local de MySQL"
```

Para entornos donde los scripts `mvn.cmd`/`mvnw.cmd` presenten fallos,
se puede utilizar el lanzador de Maven mediante Java 21:

```powershell
& "$env:JAVA_HOME\bin\java.exe" "-Dmaven.multiModuleProjectDirectory=$((Get-Location).Path)" "-Dmaven.home=$mavenHome" "-Dclassworlds.conf=$mavenHome\bin\m2.conf" -cp "$mavenHome\boot\*" org.codehaus.plexus.classworlds.launcher.Launcher test
```

La suite global del proyecto se ejecutó en Windows con **97 pruebas,
0 fallos, 0 errores y `BUILD SUCCESS`**.

## Evolución posterior

Los controladores REST, DTO, actualizaciones parciales y manejo de errores
HTTP se implementaron en el paso 7; Firebase y la integración con Identidad
se añadieron en el paso 8.
