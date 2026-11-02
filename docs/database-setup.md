# Paso 4 — MySQL y Flyway (desarrollo local)

Este paso prepara la **base de datos independiente** de Catálogo.
No migra datos del monolito ni implementa entidades JPA / API REST.

## Requisitos

- Windows PowerShell, Java 21, Maven Wrapper (ya están en el proyecto).
- **Docker Desktop instalado y abierto**, con Docker Engine activo.
- Puertos libres: MySQL local **3308** y aplicación Spring Boot **8082**.

## 1. Obtener cambios

En la raíz del repositorio:

```powershell
git switch develop
git pull origin develop
```

## 2. Preparar secretos locales (NO subir a Git)

Crea un archivo **.env** en la raíz copiando el ejemplo:

```powershell
Copy-Item .env.example .env
```

Edita `.env` en VS Code y cambia `DB_PASSWORD` y `MYSQL_ROOT_PASSWORD` por
dos contraseñas distintas y suficientemente fuertes. Mantén `DB_USERNAME=lendup_catalogo`.
No uses contraseñas reales en capturas o mensajes. El archivo `.env` está
incluido en `.gitignore`.

Comprueba sin imprimir contraseñas:

```powershell
git status --short --ignored .env
```

Debe aparecer `!! .env` (ignorado).

**Nota:** Docker Compose lee `.env` automáticamente, pero Maven/Spring Boot **no**.
Al ejecutar Spring localmente tendrás que establecer `$env:DB_PASSWORD`
en la misma terminal.

## 3. Iniciar MySQL con Docker Desktop abierto

```powershell
docker compose version
docker compose up -d mysql
docker compose ps
```

Verifica que `mysql` esté marcado como `healthy` en `docker compose ps`.
Si el puerto 3308 está ocupado, no cambies solo Compose: debes cambiar también
`spring.datasource.url` (o establecer `DB_URL`).

## 4. Iniciar Catálogo para que Flyway migre la base

Verifica primero:

```powershell
java -version
.\mvnw.cmd -version
```

Ambos deben utilizar Java 21. Antes de iniciar la aplicación configura la contraseña
**de usuario**, la misma que está en `.env` bajo `DB_PASSWORD`:

```powershell
$env:DB_PASSWORD = Read-Host "Introduce DB_PASSWORD del archivo .env"
.\mvnw.cmd spring-boot:run
```

No introduzcas `MYSQL_ROOT_PASSWORD` aquí. La conexión por defecto será
`jdbc:mysql://localhost:3308/lendup_catalogo`. Los logs deben indicar
que Flyway aplicó la migración `V1` o que el esquema ya está actualizado.
El servicio escucha en **http://localhost:8082**.

El arranque inicial puede mostrar una contraseña de seguridad autogenerada por
Spring Security; eso no es un fallo. **Todavía no existe API de Catálogo.**

## 5. Comprobar Flyway y tablas (segunda terminal PowerShell)

Desde la carpeta del repositorio:

```powershell
docker compose exec mysql mysql -u lendup_catalogo -p lendup_catalogo
```

Escribe `DB_PASSWORD` cuando MySQL la solicite (no aparecerán los caracteres).
Dentro del cliente MySQL:

```sql
SHOW TABLES;
SELECT installed_rank, version, description, success
FROM flyway_schema_history;
```

Resultado esperado: `categorias`, `publicaciones`,
`disponibilidades_publicacion`, `imagenes_publicacion` y
`flyway_schema_history`, con `version=1` y `success=1`.

Sal del cliente MySQL usando `exit;`.
Para detener Spring Boot, pulsa Ctrl+C en su terminal.
Para detener MySQL sin eliminar tus datos:

```powershell
docker compose down
```

**Nunca ejecutes `docker compose down -v`** si quieres conservar los datos
del volumen `catalog-mysql-data`.

## Límites del esquema

- Tablas propias del Catálogo (nombres y tipos tomados del `schema.sql` del monolito).
- `propietario_usuario_id` es una referencia UUID, **sin FK ni acceso directo a Identidad**.
- No hay tablas `usuarios`, `solicitudes`, `reservas`, `prestamos` ni `agendas_objeto`.
- La proyección de bloqueos `bloqueos_reserva_lectura` se evaluará tras acordar
  el contrato con Reservas; no crearemos una copia ficticia de su base.
- Flyway administra la creación/evolución del esquema; Hibernate solo valida
  (`spring.jpa.hibernate.ddl-auto=validate`).

## Pruebas y errores conocidos

- El repositorio todavía no tiene entidades JPA. La comprobación de Flyway requiere
  ejecutar Spring Boot con MySQL activo.
- Las pruebas de dominio pueden ejecutarse sin MySQL seleccionando explícitamente
  las clases de test. El `@SpringBootTest` generado sí requiere una base/configuración
  de pruebas; la infraestructura de tests de integración llegará después.
- Si aparece `Could not resolve placeholder 'DB_PASSWORD'`, configura
  `$env:DB_PASSWORD` en la terminal desde la que ejecutas Maven.
- Si aparece `Communications link failure`, verifica `docker compose ps`.
- Si MySQL rechaza credenciales tras cambiarlas, recuerda que los usuarios del
  contenedor se inicializan al crear el volumen por primera vez: cambiar
  `.env` no cambia automáticamente contraseñas dentro de una base ya existente.
