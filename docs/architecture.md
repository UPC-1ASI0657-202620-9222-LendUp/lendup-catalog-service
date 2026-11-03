# Arquitectura inicial — Catálogo

## Estado

Este documento recoge la **estructura DDD definida inicialmente**. Los endpoints,
las migraciones, la persistencia, Firebase y Cloudinary se incorporaron en etapas
posteriores. La integración real con Identidad y Reservas depende de sus contratos.

## Responsabilidad y propiedad de datos

Catálogo es responsable de publicaciones, categorías, imágenes de publicaciones y
disponibilidad ofrecida. Las tablas propias modeladas a partir del monolito son:

- `categorias`
- `publicaciones`
- `imagenes_publicacion`
- `disponibilidades_publicacion`

Los UUID de propietarios se almacenan como referencias externas, sin consultar directamente la tabla
`usuarios` de Identidad. La ocupación de periodos por reservas requiere un contrato explícito con Reservas;
no se incorporan sus tablas `reservas` ni `agendas_objeto`.

## Capas

- `domain/model`: reglas y conceptos de negocio, sin Spring/JPA.
- `domain/specification`: validaciones de elegibilidad y disponibilidad.
- `application/port/in`: contratos de casos de uso.
- `application/port/out`: dependencias expresadas como interfaces.
- `application/service`: orquestación de casos de uso.
- `infrastructure/persistence/entity`: representación JPA de datos propios.
- `infrastructure/persistence/repository`: adaptadores/repositories de la BD propia.
- `infrastructure/client`: integraciones externas.
- `interfaces/rest` y `interfaces/rest/dto`: API y contratos HTTP.
- `config`: configuración transversal y de seguridad.

## Integraciones y estado actual

- **Identidad:** el adaptador HTTP resuelve el Firebase UID al UUID interno sin compartir
  tablas. La verificación real depende de la disponibilidad del servicio de Identidad.
- **Reservas:** el contrato interno para consultar publicaciones y periodos ofrecidos
  está pendiente de coordinación; no existe acceso directo a la base de Reservas.
- **Cloudinary:** se implementaron subida, consulta y eliminación de fotografías con
  control de propiedad. La operación real requiere credenciales y un usuario autenticado.

## Evolución de la implementación

1. Modelo de dominio y reglas de disponibilidad.
2. Puertos y casos de uso.
3. Esquema propio mediante Flyway y adaptadores de persistencia.
4. Seguridad Firebase y adaptador de Identidad.
5. API REST, Cloudinary, OpenAPI y pruebas de integración.

No copiar `Store.java`, `FlowSupport.java` ni `schema.sql` completos del monolito.
