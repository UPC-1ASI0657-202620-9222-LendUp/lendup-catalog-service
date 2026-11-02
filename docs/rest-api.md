# Paso 7 — API REST y DTOs de Catálogo

## Base y versión

Puerto HTTP local: `8082`. Base de rutas: `/api/v1/catalogo`.

| Verbo | Ruta | Acceso | Resultado |
|---|---|---|---|
| GET | `/categorias` | Público | Lista de categorías activas (200) |
| GET | `/categorias/{id}` | Público | Categoría activa (200); inexistente/inactiva (404) |
| GET | `/publicaciones` | Público | Publicaciones activas con categoría activa (200) |
| GET | `/publicaciones/{id}` | Público | Detalle visible (200); pausada, de baja o categoría inactiva (404) |
| GET | `/mis-publicaciones` | Propietario autenticado | Lista propias, incluye pausadas (200) |
| POST | `/publicaciones` | Propietario autenticado | Crea publicación (201 + Location) |
| PUT | `/publicaciones/{id}` | Propietario autenticado | Reemplaza detalles y condiciones completos (200) |
| PATCH | `/publicaciones/{id}` | Propietario autenticado | Actualiza sólo campos presentes (200) |
| PATCH | `/publicaciones/{id}/estado` | Propietario autenticado | Cambia estado (200) |
| GET | `/publicaciones/{publicacionId}/disponibilidades` | Propietario autenticado | Intervalos ofrecidos (200) |
| POST | `/publicaciones/{publicacionId}/disponibilidades` | Propietario autenticado | Agrega intervalo (201 + Location) |
| PUT | `/publicaciones/{publicacionId}/disponibilidades/{id}` | Propietario autenticado | Cambia intervalo (200) |
| DELETE | `/publicaciones/{publicacionId}/disponibilidades/{id}` | Propietario autenticado | Elimina intervalo (204) |

**Nota:** los intervalos ofrecidos no descuentan reservas confirmadas. Los métodos
de disponibilidad del paso 7 son sólo para propietarios; el contrato interno de
Reservas se acordará y publicará en un paso posterior.

## Seguridad / limitación intencional del paso 7

Para evitar suplantación, **NO se acepta `actorUsuarioId`, `propietarioUsuarioId`,
`X-User-Id` ni identificadores arbitrarios del cliente como identidad confiable**.
El adaptador `UsuarioActual` sólo acepta un principal tipado `UsuarioAutenticado`
dentro del contexto Spring Security. **Todavía no existe ningún autenticador HTTP**
que cree ese principal desde Firebase/Identidad.

Por tanto:
- Las consultas públicas sí pueden probarse en HTTP.
- En esta etapa, **las operaciones autenticadas rechazarán peticiones HTTP
  externas con 401**, aun con un header inventado o Basic Auth.
- Las pruebas MockMvc simulan *internamente* un contexto autenticado con
  `UsuarioAutenticado` para validar los controladores y sus casos de uso.
- El paso de autenticación futuro implementará la verificación Firebase,
  resolverá el UUID interno contra Identidad y configurará autenticación Bearer.
  No activar un bypass de desarrollo con headers sin verificar.
- Acceso con un principal autenticado de tipo incorrecto devuelve 403.
- Se deshabilita CSRF porque se prepara una API sin sesiones ni cookies
  (futura autenticación Bearer); no hay formularios ni autenticación Basic.
- El servicio rechaza identidades de otros propietarios en los casos de uso.

## Ejemplo de creación

```json
{
  "detalles": {
    "categoriaId": "11111111-1111-4111-8111-111111111111",
    "titulo": "Cámara fotográfica",
    "descripcion": "Para proyectos de aula",
    "condicionObjeto": "Buen estado",
    "universidad": "UPC",
    "campus": "Monterrico",
    "ubicacion": "Campus",
    "latitud": -12.1,
    "longitud": -77.1
  },
  "condiciones": {
    "tarifaDiaria": 25.00,
    "garantiaMonetaria": 100.00,
    "moneda": "PEN",
    "lugarIntercambio": "Campus",
    "condicionesUso": "Cuidar",
    "condicionesEntrega": "En mano",
    "condicionesDevolucion": "En mano",
    "condicionesCancelacion": "Avisar"
  }
}
```

`PUT /publicaciones/{id}` exige las mismas secciones completas.
`PATCH /publicaciones/{id}` permite partes anidadas, por ejemplo:

```json
{
  "detalles": { "titulo": "Cámara mejorada" },
  "condiciones": { "garantiaMonetaria": null }
}
```

Los campos omitidos conservan su valor; `null` explícito elimina los
campos opcionales como `garantiaMonetaria`, `latitud` y `longitud`.
Un `null` en un campo obligatorio se rechaza con 400. Los campos no permitidos,
incluidos `propietarioUsuarioId`, `id` y `estado`, también se rechazan.

Cambio de estado:

```json
{"estado":"PAUSADA"}
```

Disponibilidad ofrecida:

```json
{"desde":"2026-12-12T09:00:00","hasta":"2026-12-12T12:00:00"}
```

Las fechas son `LocalDateTime` en el reloj local del negocio, sin sufijo `Z`.
Deben constituir un intervalo válido semiabierto `[desde,hasta)` y no solaparse.

## Respuestas de error

Estructura uniforme:

```json
{
  "codigo": "VALIDACION",
  "mensaje": "Datos de entrada inválidos",
  "instante": "2026-11-02T18:00:00Z",
  "campos": {"detalles.titulo": "no debe estar vacío"}
}
```

- **400** `VALIDACION` / `SOLICITUD_INVALIDA`: datos o JSON incorrectos.
- **401** sin autenticación reconocida (respuesta del filtro Security).
- **403** `ACCESO_DENEGADO`: falta de propiedad o identidad interna incompatible.
- **404** `NO_ENCONTRADO`: publicación, categoría o disponibilidad inexistente,
  o detalle público no visible.
- **409** `DISPONIBILIDAD_SUPERPUESTA`: rango solapado.

La respuesta 401/403 emitida directamente por Spring Security puede usar la
respuesta estándar del servidor, no el DTO `ApiError`.

## Pruebas automatizadas

`CatalogoRestControllerTest` es una prueba de *slice* MVC `@WebMvcTest`,
que ejecuta rutas, validación, serialización Jackson 3, filtros de seguridad
y mapeo de excepciones. Los puertos son mocks; no necesita MySQL para ese test.
También se ejecutan las **45 pruebas existentes** de dominio, aplicación y
persistencia con la configuración de MySQL del proyecto.

### Windows / VS Code

En la raíz de `lendup-catalog-service`:

```powershell
git switch develop
git pull origin develop
docker compose ps
```

Si MySQL está apagado: `docker compose up -d mysql`.
Si la terminal de VS Code es nueva, prepara las variables locales:

```powershell
$jdk21 = Get-ChildItem "C:\Program Files\Microsoft" -Directory -Filter "jdk-21*" | Select-Object -First 1 -ExpandProperty FullName
$env:JAVA_HOME = $jdk21
$env:Path = "$jdk21\bin;$env:Path"
$mvn = Get-ChildItem "$HOME\.m2\wrapper\dists" -Recurse -Filter mvn.cmd -File | Select-Object -First 1
$mavenHome = Split-Path (Split-Path $mvn.FullName -Parent) -Parent
$env:DB_PASSWORD = Read-Host "Contraseña de Catálogo"
```

Ejecutar con Java 21 directo (en esta máquina fallaba el `.cmd`):

```powershell
& "$env:JAVA_HOME\bin\java.exe" "-Dmaven.multiModuleProjectDirectory=$((Get-Location).Path)" "-Dmaven.home=$mavenHome" "-Dclassworlds.conf=$mavenHome\bin\m2.conf" -cp "$mavenHome\boot\*" org.codehaus.plexus.classworlds.launcher.Launcher test
```

Resultado esperado: **61 pruebas** (45 anteriores + 16 REST),
`Failures: 0`, `Errors: 0`, `BUILD SUCCESS`.
Esto aún **debe verificarse en tu Windows**: no se han ejecutado las
pruebas remotas desde el conector GitHub.

Para probar un endpoint público manualmente, después de una compilación correcta,
inicia la aplicación con tu mismo lanzador Java cambiando `test` por
`spring-boot:run` y ejecuta en **otra** terminal:

```powershell
Invoke-RestMethod http://localhost:8082/api/v1/catalogo/categorias
```

Es normal recibir `[]` si no hay categorías activas aún.
Evita intentar POST/PUT/PATCH/DELETE reales hasta configurar Firebase/Identidad.

## Pendiente

La verificación de identidad Firebase, resolución del usuario UUID interno y
autenticación de servicios hacia Reservas son implementaciones posteriores.
Cloudinary, imágenes y OpenAPI también quedan fuera de este paso.
