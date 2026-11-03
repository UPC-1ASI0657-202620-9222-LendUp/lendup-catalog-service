using System.Text.RegularExpressions;

namespace LendUp.Catalogo.Domain;

public sealed class ReglaDominioException(string mensaje) : Exception(mensaje);
public sealed class DisponibilidadSuperpuestaException() : Exception("El intervalo se superpone con otra disponibilidad");
public enum EstadoPublicacion { ACTIVA, PAUSADA, DADA_DE_BAJA }

internal static class Validar
{
    public static string Texto(string? valor, string campo, int max = int.MaxValue)
    {
        if (string.IsNullOrWhiteSpace(valor)) throw new ReglaDominioException($"{campo} es obligatorio");
        valor = valor.Trim();
        if (valor.Length > max) throw new ReglaDominioException($"{campo} supera {max} caracteres");
        return valor;
    }
}

public sealed record Categoria
{
    public Guid Id { get; }
    public string Codigo { get; }
    public string Nombre { get; }
    public bool Activa { get; }
    public Categoria(Guid id, string codigo, string nombre, bool activa)
    {
        Id = id; Codigo = Validar.Texto(codigo, nameof(codigo), 50);
        Nombre = Validar.Texto(nombre, nameof(nombre), 100); Activa = activa;
    }
}

public sealed record DetallesPublicacion
{
    public Guid CategoriaId { get; }
    public string Titulo { get; }
    public string Descripcion { get; }
    public string CondicionObjeto { get; }
    public string Universidad { get; }
    public string Campus { get; }
    public string Ubicacion { get; }
    public decimal? Latitud { get; }
    public decimal? Longitud { get; }
    public DetallesPublicacion(Guid categoriaId, string titulo, string descripcion, string condicionObjeto,
        string universidad, string campus, string ubicacion, decimal? latitud, decimal? longitud)
    {
        CategoriaId = categoriaId; Titulo = Validar.Texto(titulo, nameof(titulo), 180);
        Descripcion = Validar.Texto(descripcion, nameof(descripcion));
        CondicionObjeto = Validar.Texto(condicionObjeto, nameof(condicionObjeto));
        Universidad = Validar.Texto(universidad, nameof(universidad), 150);
        Campus = Validar.Texto(campus, nameof(campus), 150); Ubicacion = Validar.Texto(ubicacion, nameof(ubicacion), 250);
        if (latitud is < -90 or > 90 || longitud is < -180 or > 180) throw new ReglaDominioException("Coordenadas fuera de rango");
        Latitud = latitud; Longitud = longitud;
    }
}

public sealed record CondicionesPublicacion
{
    public decimal TarifaDiaria { get; }
    public decimal? GarantiaMonetaria { get; }
    public string Moneda { get; }
    public string LugarIntercambio { get; }
    public string CondicionesUso { get; }
    public string CondicionesEntrega { get; }
    public string CondicionesDevolucion { get; }
    public string CondicionesCancelacion { get; }
    public CondicionesPublicacion(decimal tarifaDiaria, decimal? garantiaMonetaria, string moneda,
        string lugarIntercambio, string condicionesUso, string condicionesEntrega,
        string condicionesDevolucion, string condicionesCancelacion)
    {
        if (tarifaDiaria < 0 || garantiaMonetaria < 0) throw new ReglaDominioException("Los importes no pueden ser negativos");
        TarifaDiaria = tarifaDiaria; GarantiaMonetaria = garantiaMonetaria;
        Moneda = Validar.Texto(moneda, nameof(moneda), 3);
        if (!Regex.IsMatch(Moneda, "^[A-Z]{3}$")) throw new ReglaDominioException("Moneda inválida");
        LugarIntercambio = Validar.Texto(lugarIntercambio, nameof(lugarIntercambio), 250);
        CondicionesUso = Validar.Texto(condicionesUso, nameof(condicionesUso));
        CondicionesEntrega = Validar.Texto(condicionesEntrega, nameof(condicionesEntrega));
        CondicionesDevolucion = Validar.Texto(condicionesDevolucion, nameof(condicionesDevolucion));
        CondicionesCancelacion = Validar.Texto(condicionesCancelacion, nameof(condicionesCancelacion));
    }
}

public sealed record PeriodoDisponibilidad
{
    public DateTime Desde { get; }
    public DateTime Hasta { get; }
    public PeriodoDisponibilidad(DateTime desde, DateTime hasta)
    {
        if (desde.Kind != DateTimeKind.Unspecified || hasta.Kind != DateTimeKind.Unspecified)
            throw new ReglaDominioException("Las fechas de disponibilidad no llevan zona horaria");
        if (desde >= hasta) throw new ReglaDominioException("desde debe ser anterior a hasta");
        Desde = desde; Hasta = hasta;
    }
    public bool SeSuperpone(PeriodoDisponibilidad otro) => Desde < otro.Hasta && Hasta > otro.Desde;
    public bool Contiene(PeriodoDisponibilidad otro) => Desde <= otro.Desde && Hasta >= otro.Hasta;
}

public sealed record Disponibilidad(Guid Id, Guid PublicacionId, PeriodoDisponibilidad Periodo);
public static class DisponibilidadSpecification
{
    public static bool PeriodoOfrecido(IEnumerable<Disponibilidad> disponibles, PeriodoDisponibilidad periodo)
        => disponibles.Any(d => d.Periodo.Contiene(periodo));
    public static bool SinSolapamiento(IEnumerable<Disponibilidad> disponibles, PeriodoDisponibilidad periodo, Guid? excluir = null)
        => !disponibles.Any(d => d.Id != excluir && d.Periodo.SeSuperpone(periodo));
}

public sealed class Publicacion
{
    private readonly List<Disponibilidad> intervalos = [];
    public Guid Id { get; }
    public Guid PropietarioUsuarioId { get; }
    public DetallesPublicacion Detalles { get; private set; }
    public CondicionesPublicacion Condiciones { get; private set; }
    public EstadoPublicacion Estado { get; private set; }
    public IReadOnlyList<Disponibilidad> Disponibilidades => intervalos.AsReadOnly();
    public Publicacion(Guid id, Guid propietarioUsuarioId, DetallesPublicacion detalles,
        CondicionesPublicacion condiciones, EstadoPublicacion estado = EstadoPublicacion.ACTIVA,
        IReadOnlyList<Disponibilidad>? disponibilidades = null)
    {
        Id = id; PropietarioUsuarioId = propietarioUsuarioId;
        Detalles = detalles ?? throw new ReglaDominioException("Detalles obligatorios");
        Condiciones = condiciones ?? throw new ReglaDominioException("Condiciones obligatorias");
        CambiarEstado(estado);
        foreach (var d in disponibilidades ?? []) AgregarDisponibilidad(d);
    }
    public bool EsPropietario(Guid actor) => PropietarioUsuarioId == actor;
    public void Actualizar(DetallesPublicacion detalles, CondicionesPublicacion condiciones)
    {
        ArgumentNullException.ThrowIfNull(detalles); ArgumentNullException.ThrowIfNull(condiciones);
        Detalles = detalles; Condiciones = condiciones;
    }
    public void CambiarEstado(EstadoPublicacion estado)
    {
        if (!Enum.IsDefined(estado)) throw new ReglaDominioException("Estado inválido");
        Estado = estado;
    }
    private void ValidarDisponibilidad(Disponibilidad d, Guid? excluir)
    {
        ArgumentNullException.ThrowIfNull(d.Periodo);
        if (d.PublicacionId != Id) throw new ReglaDominioException("Disponibilidad de otra publicación");
        if (!DisponibilidadSpecification.SinSolapamiento(intervalos, d.Periodo, excluir))
            throw new DisponibilidadSuperpuestaException();
    }
    public void AgregarDisponibilidad(Disponibilidad d)
    {
        if (intervalos.Any(x => x.Id == d.Id)) throw new ReglaDominioException("ID duplicado");
        ValidarDisponibilidad(d, null); intervalos.Add(d);
    }
    public void ActualizarDisponibilidad(Disponibilidad d)
    {
        var index = intervalos.FindIndex(x => x.Id == d.Id);
        if (index < 0) throw new KeyNotFoundException("Disponibilidad no encontrada");
        ValidarDisponibilidad(d, d.Id); intervalos[index] = d;
    }
    public void EliminarDisponibilidad(Guid id)
    {
        if (intervalos.RemoveAll(x => x.Id == id) == 0) throw new KeyNotFoundException("Disponibilidad no encontrada");
    }
    public bool OfrecePeriodo(PeriodoDisponibilidad periodo) => DisponibilidadSpecification.PeriodoOfrecido(intervalos, periodo);
}

public sealed record ImagenPublicacion(Guid Id, Guid PublicacionId, string CloudinaryPublicId, string Url, int Orden);
public static class ReglasImagen
{
    public const int MaxBytes = 5 * 1024 * 1024;
    public static int PrimerOrdenDisponible(IEnumerable<ImagenPublicacion> imagenes)
    {
        var ocupados = imagenes.Select(x => x.Orden).ToHashSet();
        return Enumerable.Range(0, 8).FirstOrDefault(i => !ocupados.Contains(i), -1) is var orden && orden >= 0
            ? orden : throw new ReglaDominioException("Máximo de 8 imágenes por publicación");
    }
    public static void ValidarArchivo(byte[] bytes, string mime)
    {
        if (bytes.Length == 0 || bytes.Length > MaxBytes) throw new ReglaDominioException("Imagen vacía o superior a 5 MiB");
        var b = bytes.AsSpan();
        var valido = mime switch
        {
            "image/jpeg" => b.StartsWith(new byte[] { 255, 216, 255 }),
            "image/png" => b.StartsWith(new byte[] { 137, 80, 78, 71, 13, 10, 26, 10 }),
            "image/webp" => b.Length >= 12 && b[..4].SequenceEqual("RIFF"u8) && b[8..12].SequenceEqual("WEBP"u8),
            _ => false
        };
        if (!valido) throw new ReglaDominioException("Solo JPEG, PNG y WebP válidos");
    }
}
