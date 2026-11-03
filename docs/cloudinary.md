> Referencia Java conservada durante la migración. Para la implementación ASP.NET Core, consultar [la guía .NET](dotnet-guide.md) y [la validación y limitación de fechas](migration-validation.md).

# Imágenes de publicaciones con Cloudinary

El binario se almacena en Cloudinary y la tabla MySQL `imagenes_publicacion`
conserva `cloudinary_public_id`, `url` y `orden`.

## Configuración local (Windows PowerShell)

Las credenciales de la cuenta se obtienen en Cloudinary Dashboard >
**Go to API Keys**. Deben permanecer fuera del repositorio y de los registros públicos:

```powershell
$env:CLOUDINARY_CLOUD_NAME = Read-Host "Cloudinary Cloud Name"
$env:CLOUDINARY_API_KEY = Read-Host "Cloudinary API Key"
$secreto = Read-Host "Cloudinary API Secret" -AsSecureString
$env:CLOUDINARY_API_SECRET = [System.Net.NetworkCredential]::new("", $secreto).Password
Remove-Variable secreto
$env:CLOUDINARY_ENABLED = "true"
```

La integración queda desactivada por defecto para que el servicio arranque sin
secretos; las escrituras de imágenes exigen Firebase e Identidad operativos.
Cuando no está configurado, el POST devolverá HTTP 503. La subida es firmada
desde Java, sin presets públicos ni API secrets en el frontend.

## Endpoints

Base: `/api/v1/catalogo/publicaciones/{publicacionId}/imagenes`.

- `GET /`: público solo si la publicación está visible y su categoría activa.
- `POST /`: propietario; `multipart/form-data`, campo `file`, HTTP 201.
- `DELETE /{imagenId}`: propietario; HTTP 204.

Se permiten JPEG, PNG y WebP de hasta 5 MiB, con validación básica de
cabeceras y tipo MIME. Máximo 8 imágenes; orden de 0 a 7 automático.
La respuesta REST expone UUID, URL HTTPS y orden, no credenciales.

## Consistencia

El bloqueo de publicación evita órdenes repetidos en subidas simultáneas.
Si falla la persistencia MySQL tras subir el archivo, se intenta eliminar
el recurso de Cloudinary. Esto es best-effort: para producción, los errores
de compensación y las eliminaciones parciales requieren conciliación y reintentos.

Las pruebas unitarias/MockMvc usan mocks y no consumen créditos de Cloudinary.
La suite completa aprobada en desarrollo local consta de 97 pruebas.
La prueba real de POST necesita configurar tanto Cloudinary como
Firebase e Identidad. Nunca subir `.env`, `.secrets/` ni API secrets.

