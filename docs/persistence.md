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

**Importante para el futuro paso 6:** para editar una publicación existente, el caso
de uso debe ejecutar **una única transacción** en la que primero llame a
`buscarPorIdParaActualizar(id)`, aplique las reglas con `Publicacion` y finalmente
llame a `guardar(publicacion)`. El método de lectura bloqueada exige una transacción
activa. No hacer la lectura normal fuera de transacción y guardar después, pues eso
permitiría sobrescribir intervalos escritos concurrentemente.

`listarActivas` exige tanto `publicaciones.estado='ACTIVA'` como
`categorias.activa=true`, siguiendo la consulta pública del monolito.

`ImagenPublicacionPersistenceAdapter` guarda **metadatos**; el upload y
borrado de archivos reales en Cloudinary quedan para otro paso.

### Ejecutar pruebas locales (requiere abrir programas)

1. **Docker Desktop:** abrir y comprobar `Engine running`.
2. **VS Code:** abrir `lendup-catalog-service` y la terminal PowerShell.
3. Actualizar la rama:

   ```powershell
   git switch develop
   git pull origin develop
   ```

4. Asegurarte de que el archivo `.env` local contiene las contraseñas que ya
   inicializaste para el contenedor (no enviarlas al chat ni a Git).
5. Levantar o verificar el contenedor MySQL:

   ```powershell
   docker compose up -d mysql
   docker compose ps
   ```

6. Verificar Java 21 e indicar la contraseña de **usuario normal** de MySQL
   (no la del administrador root):

   ```powershell
   java -version
   $env:DB_PASSWORD = Read-Host "Introduce DB_PASSWORD de tu .env"
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

Los commits y los archivos están publicados en `develop`. No hay ejecución
de Maven ni de Docker disponible desde el entorno de edición de GitHub;
las pruebas y la validación de Hibernate contra MySQL **deben ejecutarse
localmente** y revisar la salida completa. Si falla `ddl-auto: validate`,
revisar la divergencia específica de tipos JPA/SQL sin cambiar una migración
Flyway V1 que ya fue aplicada.

### Siguientes pasos

- Paso 6: casos de uso y transacciones de aplicación.
- Paso 7: REST y contratos para el frontend.
- Más adelante: autorización Firebase, Identidad, Reservas y Cloudinary.
