# LendUp Catálogo

Microservicio de categorías, publicaciones, disponibilidad ofrecida e imágenes. Usa C#, ASP.NET Core 10, EF Core 10 con MySQL 8.4 y Redis. Firebase autentica al estudiante, Identidad resuelve su UUID interno y Cloudinary almacena imágenes.

La solución LendUp.Catalogo.sln contiene Domain, Application, Infrastructure y Api bajo src, con cuatro proyectos correspondientes bajo tests. Infrastructure conserva el esquema SQL V1 y añade una migración Flyway V2 con categorías iniciales en Database/Migrations.

## Inicio local

Instala .NET SDK 10 y Docker. Crea .env desde .env.example si aún no existe y configura las credenciales locales.

```powershell
docker compose up -d mysql redis
Get-Content .env | ForEach-Object {
    if ($_ -match '^([A-Z][A-Z0-9_]*)=(.*)$') {
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
    }
}
$env:ASPNETCORE_ENVIRONMENT = 'Development'
dotnet run --project src/LendUp.Catalogo.Api --no-launch-profile
```

Para una base vacía, ejecuta primero el inicializador indicado en la guía, que aplica V1 y V2 con Flyway. La API escucha en 8082, MySQL en 3308, Redis en 6379 e Identidad en 8081. Swagger está en /swagger-ui/index.html y salud en /actuator/health.

```powershell
dotnet restore
dotnet build --configuration Release
dotnet test --configuration Release
docker compose --profile dotnet build api
```

- [Integración con API Gateway e Identidad: rutas, HTTPS y pruebas de humo](docs/api-gateway-integration.md)
- [Guía de entrega para AWS y variables de producción](docs/aws-deployment-handoff.md)
- [Arquitectura, configuración, seguridad y operación](docs/dotnet-guide.md)
- [Contratos REST](docs/rest-api.md)
- [Fechas locales y DATETIME(6)](docs/datetime-semantics.md)
- [Rendimiento y Redis](docs/performance.md)
