# Integración de Catálogo y Reservas — snapshot v1

## Contrato verificado en el código Java del consumidor

El adaptador `lendup-reservations-service/src/main/java/com/lendup/reservas/infrastructure/client/CatalogoRestAdapter.java` llama a una plantilla URI con **tres parámetros** ordenados: `publicacion_id`, `desde`, `hasta`. Espera un objeto JSON plano (no anidado). Este contrato cubre esas propiedades sin alterar el JSON de `/api/v1/catalogo/publicaciones`.

### Consulta pública para el frontend

```http
GET /api/v1/catalogo/publicaciones/{id}/snapshot?desde=2026-12-12T09:00:00&hasta=2026-12-12T12:00:00
```

Solo responde para publicaciones activas de categorías activas. Las fechas son locales de `America/Lima`, sin `Z` ni offset. También se acepta `yyyy-MM-ddTHH:mm` porque Java `LocalDateTime.toString()` omite segundos nulos. El frontend **debe mostrar estas condiciones** y pasar `snapshot_id` y `snapshot_hash` sin recalcularlos en `POST /api/v1/solicitudes`:

```json
{
  "publicacion_id": "<UUID>",
  "desde": "2026-12-12T09:00:00",
  "hasta": "2026-12-12T12:00:00",
  "aceptacion_condiciones": {
    "snapshot_id": "<snapshot_id devuelto por Catálogo>",
    "snapshot_hash": "<snapshot_hash devuelto por Catálogo>",
    "aceptada": true
  }
}
```

### Consulta privada de Reservas

```http
GET /internal/v1/catalogo/publicaciones/{id}/snapshot?desde={desde}&hasta={hasta}
Authorization: Bearer <CATALOG_RESERVATIONS_SERVICE_TOKEN>
```

La API interna retorna el mismo snapshot, inclusive para una publicación pausada; en ese caso `estado` será `PAUSADA` y Reservas la debe rechazar. Esta ruta **no debe exponerse en el API Gateway**.

El JSON contiene exactamente estos campos:

```json
{
  "id": "<UUID>",
  "propietario_usuario_id": "<UUID>",
  "estado": "ACTIVA",
  "periodo_ofrecido": true,
  "snapshot_id": "snp_<sha256>",
  "snapshot_hash": "<sha256 lowercase, 64 hex characters>",
  "tarifa_diaria": 25.00,
  "garantia_monetaria": 50.00,
  "moneda": "PEN",
  "lugar_intercambio": "Campus",
  "condiciones_uso": "Uso",
  "condiciones_entrega": "Entrega",
  "condiciones_devolucion": "Devolución",
  "condiciones_cancelacion": "Cancelación"
}
```

Catálogo calcula un hash SHA-256 sobre un documento canónico que incluye la revisión SQL monotónica de la publicación, el periodo solicitado, su disponibilidad, todos los intervalos ofrecidos, el estado y las condiciones de precio/uso. El mismo estado produce el mismo ID/hash; cualquier modificación a través de `CatalogoRepository.Modificar` incrementa la revisión y cambia el hash. La consulta GET es de **solo lectura**: genera el identificador y el hash sin crear filas nuevas. Reservas persiste la aceptación, el hash y las condiciones acordadas cuando crea la solicitud. Un snapshot obsoleto ya no coincide con el vigente.

La consulta usa una transacción MySQL `REPEATABLE READ` para obtener una vista coherente de la publicación, categoría y disponibilidades sin bloquear escrituras ni producir efectos secundarios. El snapshot **no bloquea ninguna reserva**: Reservas conserva el lock de `AgendaDelObjeto` y la autoridad contra dobles reservas. `periodo_ofrecido` valida únicamente disponibilidad publicada por el propietario.

### Configuración del despliegue

**No se requiere una migración nueva**. Catálogo conserva el esquema V1/V2; el historial contractual de condiciones aceptadas corresponde a Reservas.

En **Catálogo**, configurar un valor aleatorio fuerte (32-512 caracteres) como secreto de entorno:
```dotenv
CATALOG_RESERVATIONS_SERVICE_TOKEN=<SECRETO_LARGO_UNICO>
```
Sin él la ruta privada responde 401. No usar el Firebase ID token como secreto S2S.

En **Reservas**, con el mismo valor:
```dotenv
CATALOG_SERVICE_BASE_URL=https://catalog.internal.example
CATALOG_SERVICE_TOKEN=<SECRETO_LARGO_UNICO>
CATALOG_SNAPSHOT_URI_TEMPLATE=/internal/v1/catalogo/publicaciones/{id}/snapshot?desde={desde}&hasta={hasta}
```

Reemplazar `https://catalog.internal.example` con DNS privado y certificado TLS válido en AWS. Como el adaptador de Reservas sustituye los parámetros **por posición**, los placeholders pueden tener estos nombres; el orden debe mantenerse `id, desde, hasta`. Nunca publicar el secreto, enviar la URL interna por el frontend ni habilitar una ruta `/internal/**` en el Gateway público.

### Casos a probar entre equipos

1. GET público y GET privado para mismo ID/periodo → mismo `snapshot_id/hash`.
2. Modificar tarifa, condiciones, estado o disponibilidad → nueva versión/hash.
3. Enviar la aceptación vigente a Reservas → solicitud creada; aceptación desactualizada → 409.
4. Periodo no ofrecido → `periodo_ofrecido=false`; Reservas responde 409.
5. Sin token de servicio o con Firebase ID token → 401; con token de Reservas → 200.
6. Repetir GET público/privado: hash estable y ninguna escritura nueva en Catálogo; confirmar en Reservas que la solicitud persiste la aceptación y condiciones históricas.
7. En una carrera, Reservas sigue usando el bloqueo de agenda para confirmar la exclusión final.

### Pendientes de otros equipos

- **Reservas:** configurar el token y la plantilla, y usar el snapshot real con su formulario de consentimiento.
- **Frontend:** solicitar el snapshot público para el intervalo seleccionado, mostrar sus condiciones y enviar `aceptacion_condiciones` completo. El código actual de `services/requests.service.ts` omite esos campos.
- **Gateway:** mantener `/api/v1/catalogo/**` hacia Catálogo; no exponer `/internal/**`. Las rutas históricas `/api/v1/objetos/**` del frontend aún se dirigen al monolito y requieren migración coordinada.
- **DevOps:** inyectar el token en ambas tareas de ECS y permitir HTTPS privado entre servicios; no se necesita nueva migración.
- **Préstamos:** publicar eventos del flujo (no es responsabilidad de Catálogo).
- **Mensajería de Catálogo:** no se añade RabbitMQ sin un evento productor/consumidor acordado; evita introducir contratos incompatibles.

**Docker Compose local:** el perfil `dotnet` del servicio Catálogo publica por defecto `127.0.0.1:8084` hacia el puerto interno `8082` (`CATALOGO_HOST_PORT`). Si el Gateway corre en Docker y los servicios en Compose separados, configurar `CATALOG_SERVICE_URL=http://host.docker.internal:8084`; Reservas deberá usar la URL del mismo servicio, con red privada/host alcanzable desde su contenedor. El valor de `CATALOG_RESERVATIONS_SERVICE_TOKEN` del archivo `.env` sí se inyecta al contenedor por `compose.yaml`.
