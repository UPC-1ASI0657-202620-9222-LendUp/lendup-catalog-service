# API REST y DTOs de Catálogo

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
| GET | /publicaciones/{publicacionId}/imagenes | Público si visible | 200 |
| POST | /publicaciones/{publicacionId}/imagenes | Propietario | 201 + Location, multipart file |
| DELETE | /publicaciones/{publicacionId}/imagenes/{imagenId} | Propietario | 204 |

**Nota:** los intervalos ofrecidos no descuentan reservas confirmadas. Los métodos
de disponibilidad son sólo para propietarios; el contrato interno de
Reservas requiere una integración independiente.

## Seguridad y control de acceso

Catálogo no considera confiables `actorUsuarioId`, `propietarioUsuarioId`,
`X-User-Id` ni identificadores de usuario aportados por el cliente.

`UsuarioActual` exige un principal tipado `UsuarioAutenticado`. Firebase Admin valida el token y el cliente de Identidad resuelve
el UUID interno. La autenticación efectiva requiere que ambos servicios
estén configurados y disponibles.

- Las consultas públicas pueden realizarse sin autenticación.
- Las operaciones protegidas requieren identidad autenticada; sin token
  devuelven **401**. No se permite suplantación mediante cabeceras.
- Las pruebas HTTP simulan los puertos de autenticación para verificar permisos.
- Un principal incompatible no concede acceso a datos del propietario.
- Se usa una API sin sesiones, sin autenticación Basic y con CSRF deshabilitado.
- Los casos de uso comprueban la propiedad antes de modificar publicaciones.

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

La respuesta 401 emitida por autenticación puede usar la
respuesta estándar del servidor, no el DTO `ApiError`.

## Búsqueda

Combinar nombre, categoriaId, campus, universidad, desde y hasta en GET /publicaciones. El intervalo debe estar cubierto por una sola disponibilidad. Los errores de dependencias devuelven 503.

