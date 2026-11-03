# LendUp Catálogo

Microservicio de categorías, publicaciones, disponibilidad ofrecida e imágenes. La solución ASP.NET Core 10 usa DDD, EF Core/MySQL 8.4, Redis, Firebase/Identidad y Cloudinary.

**Estado de migración:** implementación .NET compilable y probada; Java permanece como referencia activa mientras se resuelve la compatibilidad de fechas históricas. No realizar el cambio definitivo de runtime antes de revisar [la validación](docs/migration-validation.md).

## Desarrollo

Instalar .NET SDK 10 estable y Docker Desktop. Desde PowerShell en la raíz:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
# Configurar .env localmente con valores propios.
docker compose up -d mysql redis
Get-Content .env | ForEach-Object {
    if ($_ -match '^([A-Z][A-Z0-9_]*)=(.*)$') {
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
    }
}
$env:ASPNETCORE_ENVIRONMENT = 'Development'
dotnet restore LendUp.Catalogo.sln
dotnet build LendUp.Catalogo.sln --configuration Release --no-restore
dotnet test LendUp.Catalogo.sln --configuration Release --no-build
dotnet run --project src/LendUp.Catalogo.Api --no-launch-profile
```

MySQL existente debe tener Flyway V1 aplicado; una base nueva se inicializa explícitamente según la [guía](docs/dotnet-guide.md). No hay migración automática ni cambios de esquema al arrancar. Se conserva catalog-mysql-data. Nunca eliminar sus volúmenes.

HTTP: 8082. Swagger: /swagger-ui/index.html. OpenAPI: /v3/api-docs. Salud: /actuator/health y /health. Consultas públicas funcionan sin configurar Firebase; operaciones protegidas fallan de forma segura. Docker: `docker compose --profile dotnet up -d --build api`; configurar integraciones y secretos mediante un override privado cuando corresponda.

## Estructura

```text
LendUp.Catalogo.sln
src/LendUp.Catalogo.Domain/
src/LendUp.Catalogo.Application/
src/LendUp.Catalogo.Infrastructure/
src/LendUp.Catalogo.Api/
tests/LendUp.Catalogo.Domain.Tests/
tests/LendUp.Catalogo.Application.Tests/
tests/LendUp.Catalogo.Api.Tests/
tests/LendUp.Catalogo.Integration.Tests/
scripts/
docs/
src/main/ y src/test/   # referencia Java conservada durante validación
```

## Documentación

- [Plan y criterios](docs/migration-plan.md).
- [Guía .NET, configuración, datos y rollback](docs/dotnet-guide.md).
- [Matriz de equivalencia y validación real](docs/migration-validation.md).
- [Rendimiento y límites de la medición](docs/performance.md).
- Los demás documentos describen la referencia Java; sus notas de migración remiten a la guía .NET.

La disponibilidad ofrecida no descuenta reservas confirmadas. No existen FK ni acceso directo a bases de Identidad o Reservas. Credenciales, tokens y cuentas de servicio permanecen fuera de Git.
