using LendUp.Catalogo.Application;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace LendUp.Catalogo.Api;

/// <summary>Private integration contract for Reservations; never register this route in the public Gateway.</summary>
[ApiController]
[ApiExplorerSettings(IgnoreApi = true)]
[Authorize(AuthenticationSchemes = CatalogoInternalAuthentication.SchemeName, Policy = "ReservationsOnly")]
[Route("internal/v1/catalogo")]
public sealed class InternalCatalogoController(CatalogoService service) : ControllerBase
{
    [HttpGet("publicaciones/{id}/snapshot")]
    public async Task<IActionResult> Snapshot(
        string id, [FromQuery] string desde, [FromQuery] string hasta, CancellationToken ct)
        => Ok(SnapshotResponse.De(await service.Snapshot(
            Solicitudes.Uuid(id), new(Solicitudes.Fecha(desde), Solicitudes.Fecha(hasta)), publico: false, ct)));
}
