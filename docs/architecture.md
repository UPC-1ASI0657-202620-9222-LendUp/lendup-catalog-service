# Arquitectura — Catálogo

## Alcance

Este documento describe la organización DDD de Catálogo, sus límites de datos, los adaptadores implementados y las integraciones con otros microservicios.

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
  tablas. La comunicación entre servicios se ha validado en el entorno local.
- **Reservas:** el contrato interno para consultar publicaciones y periodos ofrecidos
  está pendiente de coordinación; no existe acceso directo a la base de Reservas.
- **Cloudinary:** se implementaron subida, consulta y eliminación de fotografías con
  control de propiedad. La operación real requiere credenciales y un usuario autenticado.

## Principios de mantenimiento

Las responsabilidades se mantienen separadas entre dominio, casos de uso, persistencia y adaptadores externos. La integración con otros contextos debe realizarse mediante contratos explícitos, sin copiar las implementaciones internas del monolito.
