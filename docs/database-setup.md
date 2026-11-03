# MySQL y Flyway — configuración local

Catálogo utiliza una **base de datos independiente**, administrada mediante migraciones Flyway. El esquema incluye categorías, publicaciones, disponibilidades e imágenes; no replica tablas de otros microservicios.

## Requisitos

- Java 21 y Maven Wrapper; los ejemplos siguientes usan Windows PowerShell.
- **Docker Desktop instalado y abierto**, con Docker Engine activo.
- Puertos libres: MySQL local **3308** y aplicación Spring Boot **8082**.

## 1. Obtener cambios

En la raíz del repositorio:

```powershell
git switch develop
git pull origin develop
```

## 2. Preparar secretos locales (NO subir a Git)

Crear un archivo **.env** en la raíz a partir del ejemplo:

```powershell
Copy-Item .env.example .env
```

Configurar `.env` con valores distintos y suficientemente fuertes para
`DB_PASSWORD` y `MYSQL_ROOT_PASSWORD`, manteniendo `DB_USERNAME=lendup_catalogo`.
Las contraseñas no deben incluirse en registros ni commits. El archivo `.env` está
incluido en `.gitignore`. Cada línea debe utilizar formato `VARIABLE=valor`, no sintaxis de comandos PowerShell. Si una contraseña contiene `# MySQL y Flyway — configuración local

Catálogo utiliza una **base de datos independiente**, administrada mediante migraciones Flyway. El esquema incluye categorías, publicaciones, disponibilidades e imágenes; no replica tablas de otros microservicios.

## Requisitos

- Java 21 y Maven Wrapper; los ejemplos siguientes usan Windows PowerShell.
- **Docker Desktop instalado y abierto**, con Docker Engine activo.
- Puertos libres: MySQL local **3308** y aplicación Spring Boot **8082**.

## 1. Obtener cambios

En la raíz del repositorio:

```powershell
git switch develop
git pull origin develop
```

## 2. Preparar secretos locales (NO subir a Git)

Crear un archivo **.env** en la raíz a partir del ejemplo:

```powershell
Copy-Item .env.example .env
```

Configurar `.env` con valores distintos y suficientemente fuertes para
`DB_PASSWORD` y `MYSQL_ROOT_PASSWORD`, manteniendo `DB_USERNAME=lendup_catalogo`.
, debe evitarse la interpolación no deseada de Docker Compose.

Comprueba sin imprimir contraseñas:

```powershell
git status --short --ignored .env
```

Debe aparecer `!! .env` (ignorado).

**Nota:** Docker Compose lee `.env` automáticamente, pero Maven/Spring Boot **no**.
Al ejecutar Spring localmente debe establecerse `$env:DB_PASSWORD`
en la misma terminal.

## 3. Iniciar MySQL

```powershell
docker compose version
docker compose up -d mysql
docker compose ps
```

Verifica que `mysql` esté marcado como `healthy` en `docker compose ps`.
Si el puerto 3308 está ocupado, modificar tanto Docker Compose como
`spring.datasource.url` (o establecer `DB_URL`).

## 4. Iniciar Catálogo y aplicar migraciones

Verificación del entorno:

```powershell
java -version
.\mvnw.cmd -version
```

Java debe corresponder a la versión 21. La aplicación requiere la contraseña
**del usuario de MySQL**, definida en `.env` como `DB_PASSWORD`:

```powershell
$env:DB_PASSWORD = Read-Host "Introduce DB_PASSWORD del archivo .env"
.\mvnw.cmd spring-boot:run
```

No utilizar `MYSQL_ROOT_PASSWORD` para la conexión de la aplicación. La conexión por defecto será
`jdbc:mysql://localhost:3308/lendup_catalogo`. Los logs deben indicar
que Flyway aplicó la migración `V1` o que el esquema ya está actualizado.
El servicio escucha en **http://localhost:8082**.

La seguridad HTTP utiliza Firebase cuando `CATALOGO_FIREBASE_ENABLED=true`; la configuración de autenticación se documenta en [Firebase e Identidad](auth-identity.md).

## 5. Comprobar Flyway y tablas (segunda terminal PowerShell)

Desde la carpeta del repositorio:

```powershell
docker compose exec mysql mysql -u lendup_catalogo -p lendup_catalogo
```

Se solicita la contraseña `DB_PASSWORD` sin mostrarla en pantalla.
Dentro del cliente MySQL:

```sql
SHOW TABLES;
SELECT installed_rank, version, description, success
FROM flyway_schema_history;
```

Resultado esperado: `categorias`, `publicaciones`,
`disponibilidades_publicacion`, `imagenes_publicacion` y
`flyway_schema_history`, con `version=1` y `success=1`.

Para salir del cliente MySQL se utiliza `exit;`. Spring Boot se detiene con Ctrl+C.
Para detener MySQL sin eliminar los datos:

```powershell
docker compose down
```

**No ejecutar `docker compose down -v`** cuando deban conservarse los datos
del volumen `catalog-mysql-data`.

## Límites del esquema

- Tablas propias del Catálogo (nombres y tipos tomados del `schema.sql` del monolito).
- `propietario_usuario_id` es una referencia UUID, **sin FK ni acceso directo a Identidad**.
- No hay tablas `usuarios`, `solicitudes`, `reservas`, `prestamos` ni `agendas_objeto`.
- La proyección de bloqueos `bloqueos_reserva_lectura` se evaluará tras acordar
  el contrato con Reservas; no se mantiene una copia ficticia de su base.
- Flyway administra la creación/evolución del esquema; Hibernate solo valida
  (`spring.jpa.hibernate.ddl-auto=validate`).

## Pruebas y errores conocidos

- Flyway requiere MySQL activo y Hibernate valida que las entidades JPA coincidan con el esquema.
- Las pruebas de dominio pueden ejecutarse sin MySQL seleccionando explícitamente
  las clases de test. El `@SpringBootTest` generado sí requiere una base/configuración
  de pruebas; las pruebas de integración ya están implementadas.
- Si aparece `Could not resolve placeholder 'DB_PASSWORD'`, configura
  `$env:DB_PASSWORD` en la terminal desde la que ejecutas Maven.
- Si aparece `Communications link failure`, verifica `docker compose ps`.
- Si MySQL rechaza credenciales tras cambiarlas, los usuarios del
  contenedor se inicializan al crear el volumen por primera vez; cambiar
  `.env` no cambia automáticamente contraseñas dentro de una base ya existente.
