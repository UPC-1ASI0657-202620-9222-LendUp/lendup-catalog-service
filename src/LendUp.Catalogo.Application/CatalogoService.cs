using LendUp.Catalogo.Domain;
using System.Text.Json;
using System.Text.Json.Nodes;

namespace LendUp.Catalogo.Application;

public sealed class CatalogoException(int status, string codigo, string mensaje) : Exception(mensaje)
{
    public int Status { get; } = status;
    public string Codigo { get; } = codigo;
    public static CatalogoException NoEncontrado() => new(404, "NO_ENCONTRADO", "Recurso no encontrado");
    public static CatalogoException Denegado() => new(403, "ACCESO_DENEGADO", "Acceso denegado");
    public static CatalogoException Dependencia(string codigo = "DEPENDENCIA_NO_DISPONIBLE") => new(503, codigo, "Dependencia no disponible");
}

public sealed record FiltrosPublicaciones(string? Nombre = null, Guid? CategoriaId = null, string? Campus = null,
    string? Universidad = null, DateTime? Desde = null, DateTime? Hasta = null)
{
    public FiltrosPublicaciones Normalizar()
    {
        if (Desde.HasValue != Hasta.HasValue) throw new ReglaDominioException("desde y hasta deben enviarse juntos");
        if (Desde is { } desde && Hasta is { } hasta) _ = new PeriodoDisponibilidad(desde, hasta);
        return this with { Nombre = Limpiar(Nombre), Campus = Limpiar(Campus), Universidad = Limpiar(Universidad) };
    }
    private static string? Limpiar(string? valor) => string.IsNullOrWhiteSpace(valor) ? null : valor.Trim();
}

public interface ICatalogoRepository
{
    Task<IReadOnlyList<Categoria>> Categorias(CancellationToken ct);
    Task<Categoria?> Categoria(Guid id, CancellationToken ct);
    Task<IReadOnlyList<Publicacion>> Publicas(FiltrosPublicaciones filtros, CancellationToken ct);
    Task<IReadOnlyList<Publicacion>> Propias(Guid actor, CancellationToken ct);
    Task<Publicacion?> Obtener(Guid id, CancellationToken ct);
    Task<bool> Visible(Guid id, CancellationToken ct);
    Task Crear(Publicacion publicacion, CancellationToken ct);
    // Callback runs after acquiring the parent row lock, before SaveChanges and commit.
    Task<T> Modificar<T>(Guid id, Func<Publicacion, CancellationToken, Task<T>> cambio, CancellationToken ct);
    Task<IReadOnlyList<ImagenPublicacion>> Imagenes(Guid publicacionId, CancellationToken ct);
    Task AgregarImagen(ImagenPublicacion imagen, CancellationToken ct);
    Task EliminarImagen(ImagenPublicacion imagen, CancellationToken ct);
    Task<SnapshotPublicacion> Snapshot(Guid id, PeriodoDisponibilidad periodo, bool publico, CancellationToken ct);
    // Durable cache generation derived from SQL metadata, including category visibility.
    Task<string> RevisionPublica(CancellationToken ct);
}

public interface ILecturaCache
{
    Task<T> Obtener<T>(string clave, Func<CancellationToken, Task<T>> consultar, CancellationToken ct);
}
public sealed record ImagenAlmacenada(string PublicId, string Url);
public interface IImagenStorage
{
    Task<ImagenAlmacenada> Subir(Guid publicacionId, byte[] bytes, CancellationToken ct);
    Task Eliminar(string publicId, CancellationToken ct);
}
public sealed record TokenVerificado(string Uid, bool EmailVerified);
public interface IFirebaseTokenVerifier { Task<TokenVerificado> Verificar(string token, CancellationToken ct); }
public interface IIdentidadClient { Task<Guid> Resolver(string token, CancellationToken ct); }

public sealed class CatalogoService(ICatalogoRepository repo, ILecturaCache cache, IImagenStorage storage)
{
    public async Task<IReadOnlyList<Categoria>> Categorias(CancellationToken ct)
        => await cache.Obtener("categorias:" + await repo.RevisionPublica(ct), repo.Categorias, ct);
    public async Task<Categoria> Categoria(Guid id, CancellationToken ct)
    {
        var categoria = await repo.Categoria(id, ct);
        if (categoria is null || !categoria.Activa) throw CatalogoException.NoEncontrado();
        return await cache.Obtener($"categoria:{id}:" + await repo.RevisionPublica(ct), _ => Task.FromResult(categoria), ct);
    }
    public async Task<IReadOnlyList<Publicacion>> Publicas(FiltrosPublicaciones filtros, CancellationToken ct)
    {
        filtros = filtros.Normalizar();
        return await cache.Obtener("publicaciones:" + JsonSerializer.Serialize(filtros) + ":" + await repo.RevisionPublica(ct),
            token => repo.Publicas(filtros, token), ct);
    }
    public async Task<Publicacion> Publica(Guid id, CancellationToken ct)
    {
        if (!await repo.Visible(id, ct)) throw CatalogoException.NoEncontrado();
        return await cache.Obtener($"publicacion:{id}:" + await repo.RevisionPublica(ct),
            async token => await repo.Obtener(id, token) ?? throw CatalogoException.NoEncontrado(), ct);
    }
    // Snapshot is never served from Redis; it must read the current SQL revision atomically.
    public Task<SnapshotPublicacion> Snapshot(Guid id, PeriodoDisponibilidad periodo, bool publico, CancellationToken ct)
        => repo.Snapshot(id, periodo, publico, ct);
    public Task<IReadOnlyList<Publicacion>> Propias(Guid actor, CancellationToken ct) => repo.Propias(actor, ct);
    public async Task<Publicacion> Crear(Guid actor, DetallesPublicacion detalles, CondicionesPublicacion condiciones, CancellationToken ct)
    {
        await ExigirCategoria(detalles.CategoriaId, ct);
        var nueva = new Publicacion(Guid.NewGuid(), actor, detalles, condiciones);
        await repo.Crear(nueva, ct); return nueva;
    }
    public Task<Publicacion> Actualizar(Guid id, Guid actor, Func<Publicacion, (DetallesPublicacion, CondicionesPublicacion)> datos, CancellationToken ct)
        => repo.Modificar(id, async (p, token) =>
        {
            ExigirPropietario(p, actor);
            var (detalles, condiciones) = datos(p);
            await ExigirCategoria(detalles.CategoriaId, token);
            p.Actualizar(detalles, condiciones); return p;
        }, ct);
    public Task<Publicacion> CambiarEstado(Guid id, Guid actor, EstadoPublicacion estado, CancellationToken ct)
        => repo.Modificar(id, (p, _) =>
        {
            ExigirPropietario(p, actor); p.CambiarEstado(estado); return Task.FromResult(p);
        }, ct);
    public async Task<IReadOnlyList<Disponibilidad>> Disponibilidades(Guid id, Guid actor, CancellationToken ct)
    {
        var p = await repo.Obtener(id, ct) ?? throw CatalogoException.NoEncontrado();
        ExigirPropietario(p, actor); return p.Disponibilidades;
    }
    public Task<Disponibilidad> GuardarDisponibilidad(Guid id, Guid actor, Guid? disponibilidadId, PeriodoDisponibilidad periodo, CancellationToken ct)
        => repo.Modificar(id, (p, _) =>
        {
            ExigirPropietario(p, actor);
            var d = new Disponibilidad(disponibilidadId ?? Guid.NewGuid(), id, periodo);
            if (disponibilidadId is null) p.AgregarDisponibilidad(d); else p.ActualizarDisponibilidad(d);
            return Task.FromResult(d);
        }, ct);
    public Task<bool> EliminarDisponibilidad(Guid id, Guid actor, Guid disponibilidadId, CancellationToken ct)
        => repo.Modificar(id, (p, _) =>
        {
            ExigirPropietario(p, actor); p.EliminarDisponibilidad(disponibilidadId); return Task.FromResult(true);
        }, ct);
    public async Task<IReadOnlyList<ImagenPublicacion>> Imagenes(Guid id, CancellationToken ct)
    {
        if (!await repo.Visible(id, ct)) throw CatalogoException.NoEncontrado();
        return await cache.Obtener($"imagenes:{id}:" + await repo.RevisionPublica(ct), token => repo.Imagenes(id, token), ct);
    }
    public async Task<ImagenPublicacion> SubirImagen(Guid id, Guid actor, byte[] bytes, string mime, CancellationToken ct)
    {
        ReglasImagen.ValidarArchivo(bytes, mime);
        ImagenAlmacenada? subida = null;
        try
        {
            return await repo.Modificar(id, async (p, token) =>
            {
                ExigirPropietario(p, actor);
                var orden = ReglasImagen.PrimerOrdenDisponible(await repo.Imagenes(id, token));
                subida = await storage.Subir(id, bytes, token);
                if (!Uri.TryCreate(subida.Url, UriKind.Absolute, out var uri) || uri.Scheme != "https")
                    throw CatalogoException.Dependencia("ALMACENAMIENTO_NO_DISPONIBLE");
                var imagen = new ImagenPublicacion(Guid.NewGuid(), id, subida.PublicId, subida.Url, orden);
                await repo.AgregarImagen(imagen, token); return imagen;
            }, ct);
        }
        catch
        {
            // Compensation also covers SaveChanges/commit failures. Never hides the original failure.
            if (subida is not null)
            {
                using var cleanup = new CancellationTokenSource(TimeSpan.FromSeconds(10));
                try { await storage.Eliminar(subida.PublicId, cleanup.Token); } catch { /* orphan reconciliation required */ }
            }
            throw;
        }
    }
    public Task<bool> EliminarImagen(Guid id, Guid actor, Guid imagenId, CancellationToken ct)
        => repo.Modificar(id, async (p, token) =>
        {
            ExigirPropietario(p, actor);
            var imagen = (await repo.Imagenes(id, token)).SingleOrDefault(i => i.Id == imagenId) ?? throw CatalogoException.NoEncontrado();
            await storage.Eliminar(imagen.CloudinaryPublicId, token);
            await repo.EliminarImagen(imagen, token); return true;
        }, ct);
    public static void ExigirPropietario(Publicacion p, Guid actor)
    {
        if (!p.EsPropietario(actor)) throw CatalogoException.Denegado();
    }
    private async Task ExigirCategoria(Guid id, CancellationToken ct)
    {
        if (await repo.Categoria(id, ct) is null) throw CatalogoException.NoEncontrado();
    }
}

public static class ParchePublicacion
{
    private static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web);
    public static JsonObject Fusionar(Publicacion actual, JsonElement cambios)
    {
        if (cambios.ValueKind != JsonValueKind.Object || !cambios.EnumerateObject().Any())
            throw new ReglaDominioException("El PATCH requiere un objeto JSON no vacío");
        var baseJson = JsonSerializer.SerializeToNode(new { actual.Detalles, actual.Condiciones }, Json)!.AsObject();
        FusionarObjeto(baseJson, cambios); return baseJson;
    }
    private static void FusionarObjeto(JsonObject destino, JsonElement cambios)
    {
        foreach (var campo in cambios.EnumerateObject())
        {
            if (!destino.ContainsKey(campo.Name)) throw new ReglaDominioException("Campo no permitido en PATCH: " + campo.Name);
            if (campo.Value.ValueKind == JsonValueKind.Object)
            {
                if (destino[campo.Name] is not JsonObject objeto) throw new ReglaDominioException("Objeto inesperado");
                FusionarObjeto(objeto, campo.Value);
            }
            else destino[campo.Name] = JsonNode.Parse(campo.Value.GetRawText());
        }
    }
}
