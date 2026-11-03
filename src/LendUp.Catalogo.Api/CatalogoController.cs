using System.Security.Claims;
using System.Text.Json;
using LendUp.Catalogo.Application;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
namespace LendUp.Catalogo.Api;
[ApiController, Authorize, Route("api/v1/catalogo")]
public sealed class CatalogoController(CatalogoService service) : ControllerBase
{
    private Guid Actor => Guid.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);
    [AllowAnonymous, HttpGet("categorias")]
    public async Task<IActionResult> Categorias(CancellationToken ct) => Ok(await service.Categorias(ct));
    [AllowAnonymous, HttpGet("categorias/{id}")]
    public async Task<IActionResult> Categoria(string id, CancellationToken ct) => Ok(await service.Categoria(Solicitudes.Uuid(id), ct));
    [AllowAnonymous, HttpGet("publicaciones")]
    public async Task<IActionResult> Publicaciones([FromQuery] string? nombre, [FromQuery] string? categoriaId,
        [FromQuery] string? campus, [FromQuery] string? universidad, [FromQuery] string? desde, [FromQuery] string? hasta, CancellationToken ct)
        => Ok((await service.Publicas(new(nombre, categoriaId is null ? null : Solicitudes.Uuid(categoriaId), campus, universidad,
            desde is null ? null : Solicitudes.Fecha(desde), hasta is null ? null : Solicitudes.Fecha(hasta)), ct)).Select(PublicacionResponse.De));
    [AllowAnonymous, HttpGet("publicaciones/{id}")]
    public async Task<IActionResult> Publicacion(string id, CancellationToken ct) => Ok(PublicacionResponse.De(await service.Publica(Solicitudes.Uuid(id), ct)));
    [HttpGet("mis-publicaciones")]
    public async Task<IActionResult> Propias(CancellationToken ct) => Ok((await service.Propias(Actor, ct)).Select(PublicacionResponse.De));
    [HttpPost("publicaciones")]
    public async Task<IActionResult> Crear([FromBody] JsonElement body, CancellationToken ct)
    {
        var (d, c) = Solicitudes.Publicacion(body); var p = await service.Crear(Actor, d, c, ct);
        return Created($"/api/v1/catalogo/publicaciones/{p.Id}", PublicacionResponse.De(p));
    }
    [HttpPut("publicaciones/{id}")]
    public async Task<IActionResult> Actualizar(string id, [FromBody] JsonElement body, CancellationToken ct)
        => Ok(PublicacionResponse.De(await service.Actualizar(Solicitudes.Uuid(id), Actor, _ => Solicitudes.Publicacion(body), ct)));
    [HttpPatch("publicaciones/{id}")]
    public async Task<IActionResult> Patch(string id, [FromBody] JsonElement body, CancellationToken ct)
        => Ok(PublicacionResponse.De(await service.Actualizar(Solicitudes.Uuid(id), Actor,
            p => Solicitudes.Publicacion(JsonSerializer.SerializeToElement(ParchePublicacion.Fusionar(p, body))), ct)));
    [HttpPatch("publicaciones/{id}/estado")]
    public async Task<IActionResult> Estado(string id, [FromBody] JsonElement body, CancellationToken ct)
        => Ok(PublicacionResponse.De(await service.CambiarEstado(Solicitudes.Uuid(id), Actor, Solicitudes.Estado(body), ct)));
    [HttpGet("publicaciones/{publicacionId}/disponibilidades")]
    public async Task<IActionResult> Disponibilidades(string publicacionId, CancellationToken ct)
        => Ok((await service.Disponibilidades(Solicitudes.Uuid(publicacionId), Actor, ct)).Select(DisponibilidadResponse.De));
    [HttpPost("publicaciones/{publicacionId}/disponibilidades")]
    public async Task<IActionResult> AgregarDisponibilidad(string publicacionId, [FromBody] JsonElement body, CancellationToken ct)
    {
        var d = await service.GuardarDisponibilidad(Solicitudes.Uuid(publicacionId), Actor, null, Solicitudes.Periodo(body), ct);
        return Created($"/api/v1/catalogo/publicaciones/{publicacionId}/disponibilidades/{d.Id}", DisponibilidadResponse.De(d));
    }
    [HttpPut("publicaciones/{publicacionId}/disponibilidades/{id}")]
    public async Task<IActionResult> ActualizarDisponibilidad(string publicacionId, string id, [FromBody] JsonElement body, CancellationToken ct)
        => Ok(DisponibilidadResponse.De(await service.GuardarDisponibilidad(Solicitudes.Uuid(publicacionId), Actor, Solicitudes.Uuid(id), Solicitudes.Periodo(body), ct)));
    [HttpDelete("publicaciones/{publicacionId}/disponibilidades/{id}")]
    public async Task<IActionResult> EliminarDisponibilidad(string publicacionId, string id, CancellationToken ct)
    {
        await service.EliminarDisponibilidad(Solicitudes.Uuid(publicacionId), Actor, Solicitudes.Uuid(id), ct); return NoContent();
    }
    [AllowAnonymous, HttpGet("publicaciones/{publicacionId}/imagenes")]
    public async Task<IActionResult> Imagenes(string publicacionId, CancellationToken ct)
        => Ok((await service.Imagenes(Solicitudes.Uuid(publicacionId), ct)).Select(ImagenResponse.De));
    [HttpPost("publicaciones/{publicacionId}/imagenes"), RequestSizeLimit(6 * 1024 * 1024)]
    [RequestFormLimits(MultipartBodyLengthLimit = 6 * 1024 * 1024)]
    public async Task<IActionResult> SubirImagen(string publicacionId, IFormFile file, CancellationToken ct)
    {
        if (file.Length > Domain.ReglasImagen.MaxBytes) throw new Domain.ReglaDominioException("Imagen superior a 5 MiB");
        using var buffer = new MemoryStream(); await file.CopyToAsync(buffer, ct);
        var imagen = await service.SubirImagen(Solicitudes.Uuid(publicacionId), Actor, buffer.ToArray(), file.ContentType, ct);
        return Created($"/api/v1/catalogo/publicaciones/{publicacionId}/imagenes/{imagen.Id}", ImagenResponse.De(imagen));
    }
    [HttpDelete("publicaciones/{publicacionId}/imagenes/{imagenId}")]
    public async Task<IActionResult> EliminarImagen(string publicacionId, string imagenId, CancellationToken ct)
    {
        await service.EliminarImagen(Solicitudes.Uuid(publicacionId), Actor, Solicitudes.Uuid(imagenId), ct); return NoContent();
    }
}
