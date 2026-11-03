using System.Globalization;
using System.Text.Json;
using System.Text.Json.Serialization;
using LendUp.Catalogo.Domain;
namespace LendUp.Catalogo.Api;
public sealed record DisponibilidadResponse(Guid Id, Guid PublicacionId, DateTime Desde, DateTime Hasta)
{
    public static DisponibilidadResponse De(Disponibilidad d) => new(d.Id, d.PublicacionId, d.Periodo.Desde, d.Periodo.Hasta);
}
public sealed record PublicacionResponse(Guid Id, Guid PropietarioUsuarioId, DetallesPublicacion Detalles,
    CondicionesPublicacion Condiciones, EstadoPublicacion Estado, IReadOnlyList<DisponibilidadResponse> Disponibilidades)
{
    public static PublicacionResponse De(Publicacion p) => new(p.Id, p.PropietarioUsuarioId, p.Detalles, p.Condiciones,
        p.Estado, p.Disponibilidades.Select(DisponibilidadResponse.De).ToArray());
}
public sealed record ImagenResponse(Guid Id, Guid PublicacionId, string Url, int Orden)
{
    public static ImagenResponse De(ImagenPublicacion i) => new(i.Id, i.PublicacionId, i.Url, i.Orden);
}
public sealed record ApiError(string Codigo, string Mensaje, DateTime Instante, object Campos);
public static class Solicitudes
{
    private static JsonElement Campo(JsonElement json, string nombre)
        => json.ValueKind == JsonValueKind.Object && json.TryGetProperty(nombre, out var value) ? value : throw new ReglaDominioException(nombre + " es obligatorio");
    private static string Texto(JsonElement json, string nombre, int max = int.MaxValue)
    {
        var value = Campo(json, nombre);
        if (value.ValueKind != JsonValueKind.String || value.GetString()!.Length > max) throw new ReglaDominioException(nombre + " inválido");
        return value.GetString()!;
    }
    private static decimal Decimal(JsonElement json, string nombre)
    {
        var value = Campo(json, nombre);
        return value.ValueKind == JsonValueKind.Number && value.TryGetDecimal(out var number) ? number : throw new ReglaDominioException(nombre + " inválido");
    }
    private static decimal? Opcional(JsonElement json, string nombre)
        => !json.TryGetProperty(nombre, out var value) || value.ValueKind == JsonValueKind.Null ? null : Decimal(json, nombre);
    public static Guid Uuid(string value) => Guid.TryParseExact(value, "D", out var id) ? id : throw new ReglaDominioException("UUID inválido");
    public static (DetallesPublicacion, CondicionesPublicacion) Publicacion(JsonElement body)
    {
        var d = Campo(body, "detalles"); var c = Campo(body, "condiciones");
        if (d.ValueKind != JsonValueKind.Object || c.ValueKind != JsonValueKind.Object) throw new ReglaDominioException("Secciones obligatorias");
        var moneda=Texto(c,"moneda");
        if (!System.Text.RegularExpressions.Regex.IsMatch(moneda,"^[A-Z]{3}$")) throw new ReglaDominioException("Moneda inválida");
        return (new(Uuid(Texto(d, "categoriaId")), Texto(d, "titulo",180), Texto(d, "descripcion"), Texto(d, "condicionObjeto"),
            Texto(d, "universidad",150), Texto(d, "campus",150), Texto(d, "ubicacion",250), Opcional(d, "latitud"), Opcional(d, "longitud")),
            new(Decimal(c, "tarifaDiaria"), Opcional(c, "garantiaMonetaria"), moneda, Texto(c, "lugarIntercambio",250),
                Texto(c, "condicionesUso"), Texto(c, "condicionesEntrega"), Texto(c, "condicionesDevolucion"), Texto(c, "condicionesCancelacion")));
    }
    public static EstadoPublicacion Estado(JsonElement body)
        => Texto(body, "estado") is var texto && Enum.TryParse<EstadoPublicacion>(texto, false, out var estado) && Enum.IsDefined(estado)
            && estado.ToString() == texto ? estado : throw new ReglaDominioException("Estado inválido");
    public static DateTime Fecha(string texto)
    {
        if (!DateTime.TryParseExact(texto, new[] { "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss.FFFFFFF" },
            CultureInfo.InvariantCulture, DateTimeStyles.None, out var fecha)) throw new ReglaDominioException("Fecha local inválida, sin zona horaria");
        return DateTime.SpecifyKind(fecha, DateTimeKind.Unspecified);
    }
    public static PeriodoDisponibilidad Periodo(JsonElement body) => new(Fecha(Texto(body, "desde")), Fecha(Texto(body, "hasta")));
}
public sealed class FechaLocalConverter : JsonConverter<DateTime>
{
    public override DateTime Read(ref Utf8JsonReader reader, Type type, JsonSerializerOptions options) => Solicitudes.Fecha(reader.GetString()!);
    public override void Write(Utf8JsonWriter writer, DateTime value, JsonSerializerOptions options)
        => writer.WriteStringValue(value.Kind == DateTimeKind.Utc ? value.ToString("O", CultureInfo.InvariantCulture)
            : value.ToString("yyyy-MM-dd'T'HH:mm:ss.FFFFFF", CultureInfo.InvariantCulture));
}
