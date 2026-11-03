# Paso 5 — Persistencia JPA del Catálogo

## Implementación

- Cuatro entidades JPA: `CategoriaEntity`, `PublicacionEntity`,
  `DisponibilidadEntity`, `ImagenPublicacionEntity`.
- Dos embeddables para los campos de `publicaciones`:
  `DetallesPublicacionEmbeddable` y `CondicionesPublicacionEmbeddable`.
- Cuatro repositorios Spring Data JPA para las tablas del esquema V1.
- Cuatro puertos de salida y sus adaptadores para ocultar JPA al dominio.
- El modelo `ImagenPublicacion` para los metadatos de Cloudinary.
- Pruebas de integración de lectura, escritura, actualización de disponibilidades,
  consulta de categorías activas, bloqueo y metadatos de imágenes.

El campo `propietario_usuario_id` usa UUID textual CHAR(36) y **no**
se relaciona con la tabla de Identidad. Tampoco se consulta ninguna tabla de Reservas.
`Publicacion` sigue siendo una clase de dominio Java puro, no una `@Entity`.

### Consistencia de disponibilidades

`PublicacionPersistencePort.guardar` actualiza los datos de la publicación y sincroniza
los intervalos asociados **en una sola transacción**.

Para editar una publicación existente, el caso de uso ejecuta **una única transacción** en la que primero llame a
`buscarPorIdParaActualizar(id)`, aplique las reglas con `Publicacion` y finalmente
llame a `guardar(publicacion)`. El método de lectura bloqueada exige una transacción
activa. No hacer la lectura normal fuera de transacción y guardar después, pues eso
permitiría sobrescribir intervalos escritos concurrentemente.

`listarActivas` exige tanto `publicaciones.estado='ACTIVA'` como
`categorias.activa=true`, siguiendo la consulta pública del monolito.

`ImagenPublicacionPersistenceAdapter` guarda **metadatos**. La subida y
eliminación de archivos reales se implementaron después mediante Cloudinary.

### Ejecución de pruebas locales

1. **Docker Desktop:** abrir y comprobar `Engine running`.
2. **VS Code:** abrir `lendup-catalog-service` y la terminal PowerShell.
3. Actualizar la rama:

   ```powershell
   git switch develop
   git pull origin develop
   ```

4. Verificar que las contraseñas de `.env` correspondan a las credenciales
   inicializadas en el contenedor (no incluirlas en documentación ni commits).
5. Levantar o verificar el contenedor MySQL:

   ```powershell
   docker compose up -d mysql
   docker compose ps
   ```

6. Verificar Java 21 e indicar la contraseña de **usuario normal** de MySQL
   (no la del administrador root):

   ```powershell
   java -version
   $env:DB_PASSWORD = Read-Host "Contraseña local de MySQL"
   ```

7. Ejecutar las pruebas:

   ```powershell
   .\mvnw.cmd test
   ```

Las pruebas `CatalogoPersistenceIntegrationTest` usan la base MySQL real y la
migración Flyway V1. Se ejecutan con `@Transactional`; Spring revierte los
cambios hechos por cada prueba. No es necesario introducir datos manualmente.

El archivo `LendupCatalogServiceApplicationTests` generado por Spring Initializr
también requiere MySQL accesible al ejecutar `mvnw test`. El servicio
no necesita permanecer levantado con `spring-boot:run` para estas pruebas:
lo que sí debe estar activo es el contenedor de **MySQL**.

### Estado de verificación

Los archivos se encuentran publicados en `develop`. La suite completa fue
validada en Windows con **97 pruebas aprobadas**, incluyendo pruebas de
persistencia contra MySQL. Ante divergencias detectadas por
`ddl-auto: validate`, revisar tipos JPA/SQL y conservar la integridad
de las migraciones Flyway aplicadas.

### Integraciones relacionadas

Los casos de uso, controladores REST, validación Firebase y gestión de
imágenes Cloudinary están implementados. La conexión real con Identidad y
el contrato de disponibilidad con Reservas siguen pendientes.
