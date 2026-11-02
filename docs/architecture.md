# Arquitectura inicial — Catálogo

## Estado

**Solo estructura DDD creada.** Este cambio no implementa endpoints, migraciones, autenticación,
clientes gRPC, persistencia ni integración con otros microservicios. El proyecto Spring Boot existente permanece intacto.

## Responsabilidad y propiedad de datos

Catálogo será responsable de publicaciones, categorías, imágenes de publicaciones y disponibilidad ofrecida.
Las tablas por extraer del monolito se revisarán antes de crear una migración Flyway:

- `categorias`
- `publicaciones`
- `imagenes_publicacion`
- `disponibilidades_publicacion`

Los UUID de propietarios se almacenarán como referencias externas, sin consultar directamente la tabla
`usuarios` de Identidad. La ocupación de periodos por reservas requiere un contrato explícito con Reservas;
no se trasladarán sus tablas `reservas` ni `agendas_objeto`.

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

## Integraciones previstas (aún no implementadas)

- **Identidad:** resolver el Firebase UID autenticado al UUID interno del usuario sin compartir tablas.
  El equipo evaluará gRPC para esta comunicación, con autenticación de servicio.
- **Reservas:** publicar un contrato REST interno para snapshot autoritativo de una publicación y
  disponibilidad del periodo solicitado. El servicio de Reservas existente ya espera esta operación,
  pero no existe todavía una ruta acordada.
- **Cloudinary:** adaptar el almacenamiento de imágenes del monolito manteniendo los controles de propiedad.

## Próxima implementación

1. Modelar `Publicacion`, disponibilidad y reglas de dominio con pruebas unitarias.
2. Definir puertos de aplicación y casos de uso compatibles con la API actual.
3. Crear Flyway para la BD propia `lendup_catalogo`.
4. Implementar adaptadores de persistencia, Firebase y las integraciones acordadas.
5. Agregar endpoints, OpenAPI, Docker y pruebas contractuales/integración.

No copiar `Store.java`, `FlowSupport.java` ni `schema.sql` completos del monolito.
