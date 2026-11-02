# Paso 6 — Casos de uso y servicios de aplicación de Catálogo

## Interfaces de entrada

| Puerto | Operaciones |
|---|---|
| `GestionarPublicacionesUseCase` | `crear`, `actualizar`, `cambiarEstado` |
| `ConsultarPublicacionesUseCase` | `obtener`, `listarActivas`, `listarMisPublicaciones`, `periodoOfrecido` |
| `GestionarDisponibilidadUseCase` | `agregar`, `actualizar`, `eliminar`, `listar` |
| `ConsultarCategoriasUseCase` | `obtener`, `listarActivas` |

Las interfaces contienen records de comandos tipados. Los detalles y las condiciones
de una actualización se reciben **completos** en el caso de uso. En el **paso 7**,
el controlador tendrá que fusionar los campos presentes en las peticiones de
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
de un cliente externo.** En el paso REST debe derivarse del token Firebase verificado
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

No se han creado tablas, FKs ni queries a contextos ajenos; no se ha añadido
un controlador REST ni clientes gRPC, Cloudinary o RabbitMQ.

## Pruebas

- `PublicacionApplicationServiceTest`: creación, propietario, estados,
  categorías, consultas y transacciones.
- `DisponibilidadApplicationServiceTest`: solapamientos, cambios,
  inexistentes, propiedad y transacciones.
- `CategoriaApplicationServiceTest`: recuperación y ausencia.
- `CatalogoApplicationIntegrationTest`: flujo completo real
  Spring Boot + MySQL de Docker, con rollback al finalizar.

### En tu Windows / VS Code

Abrir **Docker Desktop** y verificar que su Engine esté ejecutándose.
Abrir **VS Code** en la raíz de `lendup-catalog-service` y ejecutar:

```powershell
git switch develop
git pull origin develop
docker compose up -d mysql
docker compose ps
```

Debe figurar `healthy`. Configura Java 21, Maven Wrapper distribuido
y la contraseña local (el proyecto ya dispone de estos programas):

```powershell
$jdk21 = Get-ChildItem "C:\Program Files\Microsoft" -Directory -Filter "jdk-21*" | Select-Object -First 1 -ExpandProperty FullName
$env:JAVA_HOME = $jdk21
$env:Path = "$jdk21\bin;$env:Path"
$mvn = Get-ChildItem "$HOME\.m2\wrapper\dists" -Recurse -Filter mvn.cmd -File | Select-Object -First 1
$mavenHome = Split-Path (Split-Path $mvn.FullName -Parent) -Parent
$env:DB_PASSWORD = Read-Host "Introduce DB_PASSWORD de tu .env"
```

En este equipo se detectó un problema con los scripts `mvn.cmd`/`mvnw.cmd`.
Utiliza el comando directo con Java 21 que ya funcionó:

```powershell
& "$env:JAVA_HOME\bin\java.exe" "-Dmaven.multiModuleProjectDirectory=$((Get-Location).Path)" "-Dmaven.home=$mavenHome" "-Dclassworlds.conf=$mavenHome\bin\m2.conf" -cp "$mavenHome\boot\*" org.codehaus.plexus.classworlds.launcher.Launcher test
```

El resultado esperado es `Failures: 0, Errors: 0` y `BUILD SUCCESS`.
**El código se subió a GitHub, pero estas pruebas todavía deben ejecutarse
en tu entorno de Windows antes de dar el paso 6 por validado.**

## Próximo paso

Paso 7: controladores REST, DTO, interpretación de updates parciales, tratamiento
de errores HTTP y autenticación/autorización en los límites.
