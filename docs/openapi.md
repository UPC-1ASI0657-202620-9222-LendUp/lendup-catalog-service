> Referencia Java conservada durante la migración. Para la implementación ASP.NET Core, consultar [la guía .NET](dotnet-guide.md) y [la validación y limitación de fechas](migration-validation.md).

# OpenAPI y Swagger en Catálogo

La documentación de la API utiliza `springdoc-openapi-starter-webmvc-ui:3.0.3`, compatible
con Spring Boot 4.0.x.

Con el servicio local iniciado en el puerto 8082:

- Interfaz interactiva: http://localhost:8082/swagger-ui/index.html
- Documento OpenAPI JSON: http://localhost:8082/v3/api-docs
- Documento OpenAPI YAML: http://localhost:8082/v3/api-docs.yaml

La interfaz agrupa categorías, publicaciones, disponibilidades e imágenes.
Los métodos protegidos se documentan con el esquema HTTP Bearer
`firebaseBearer`. Swagger no reemplaza el verificador real de Firebase
ni el servicio Identidad: la operación Authorize necesita un ID token válido.

Las rutas documentales se habilitan sin autenticación solo para lectura.
Para deshabilitar Swagger en un despliegue, establece
`OPENAPI_ENABLED=false`. No subir credenciales ni tokens a GitHub.

## Verificación

Con la aplicación encendida:

```powershell
Invoke-RestMethod http://localhost:8082/actuator/health
$doc = Invoke-RestMethod http://localhost:8082/v3/api-docs
$doc.info.title
$doc.paths.PSObject.Properties.Name
```

El botón Try it out permite consultar endpoints públicos;
las operaciones protegidas rechazarán peticiones sin un token válido
y el perfil correspondiente en Identidad.

