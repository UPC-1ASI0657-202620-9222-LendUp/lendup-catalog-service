# Dominio de Catálogo — paso 3

Esta etapa implementa **solo Java puro y pruebas unitarias**. No implementa JPA, SQL,
controladores, Firebase, eventos ni comunicaciones gRPC/REST.

## Clases

| Clase | Función | Correspondencia en el monolito |
|---|---|---|
| `Publicacion` | Agregado con propietario, estado, detalles, condiciones y disponibilidades | `publicaciones` |
| `DetallesPublicacion` | Categoría, título, descripción, objeto y ubicación | Campos de `publicaciones` |
| `CondicionesPublicacion` | Tarifa, garantía opcional, moneda y cuatro condiciones | Campos de `publicaciones` y futuro snapshot de Reservas |
| `EstadoPublicacion` | `ACTIVA`, `PAUSADA`, `DADA_DE_BAJA` | Estados actuales de la API |
| `Categoria` | Identidad, código, nombre y estado activo | `categorias` |
| `Disponibilidad` | Intervalo ofrecido por una publicación | `disponibilidades_publicacion` |
| `PeriodoDisponibilidad` | Intervalo semiabierto `[desde, hasta)` | Validación de fechas |
| `DisponibilidadSpecification` | Reglas de superposición y cobertura | `Store.availabilityOverlaps` / `Store.offered` |

## Reglas verificadas

- `desde < hasta`: no se admiten periodos vacíos ni invertidos.
- Dos intervalos se solapan si `a.desde < b.hasta && a.hasta > b.desde`.
  Los intervalos adyacentes se permiten.
- Al editar se excluye el mismo ID de la comparación; se rechaza invadir otro intervalo.
- Un periodo está ofrecido solo si **un único intervalo** lo contiene por completo.
  Dos intervalos consecutivos no se fusionan automáticamente.
- La publicación nace `ACTIVA`; se conservan los tres estados del monolito.
  **No se inventa** un bloqueo de transiciones desde `DADA_DE_BAJA`.
- Los ID del propietario son UUID de Identidad, nunca Firebase UID.
- La tarifa debe ser no negativa, la garantía es opcional y no puede ser negativa.
  Se valida el código de moneda de tres letras y los campos obligatorios.

## Límite importante

`ofrecePeriodo` **no equivale a una disponibilidad final reservable**.
Reservas conserva el control autoritativo de solicitudes y periodos ocupados.
El contrato interno de snapshot debe acordarse y contemplar reservas/bloqueos por
una integración explícita. Nunca consultar directamente `reservas` ni `usuarios`
en bases ajenas.

Los métodos de dominio no sustituyen la autorización ni los bloqueos
transaccionales implementados en aplicación y persistencia.

## Pruebas

- `PeriodoDisponibilidadTest`: fechas válidas, contacto y superposición.
- `PublicacionTest`: creación, estados, cambios de disponibilidad, pertenencia y cobertura.
- `CategoriaYCondicionesTest`: campos obligatorios, límites y valores monetarios.
- `DisponibilidadSpecificationTest`: exclusión del mismo ID y cobertura.

Ejecutar desde la raíz del repositorio con JDK 21:

```powershell
.\mvnw.cmd test
```

Las pruebas del dominio forman parte de la suite global de 97 pruebas
aprobadas en el entorno de desarrollo.
