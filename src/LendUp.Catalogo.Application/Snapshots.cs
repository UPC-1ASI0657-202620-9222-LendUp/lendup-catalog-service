using System.Globalization;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using LendUp.Catalogo.Domain;

namespace LendUp.Catalogo.Application;

/// <summary>Contrato plano v1 acordado con el adaptador Java de Reservas.</summary>
public sealed record SnapshotPublicacion(
    Guid Id, Guid PropietarioUsuarioId, string Estado, bool PeriodoOfrecido,
    string SnapshotId, string SnapshotHash, decimal TarifaDiaria, decimal? GarantiaMonetaria,
    string Moneda, string LugarIntercambio, string CondicionesUso, string CondicionesEntrega,
    string CondicionesDevolucion, string CondicionesCancelacion);

/// <summary>
/// A versioned, deterministic fingerprint of the effective offer.
/// Hash includes a strictly increasing SQL revision and all offered intervals, so modifying
/// and later restoring an offer can never silently reactivate an old consent.
/// The canonical document is persisted once and must not be overwritten.
/// </summary>
public static class SnapshotPublicacionFactory
{
    private const string Format = "yyyy-MM-dd'T'HH:mm:ss.ffffff";

    public static (SnapshotPublicacion Snapshot, string CanonicalJson) Crear(
        Publicacion publicacion, DateTime revisionSql, bool categoriaActiva, PeriodoDisponibilidad periodo)
    {
        ArgumentNullException.ThrowIfNull(publicacion);
        ArgumentNullException.ThrowIfNull(periodo);
        var condiciones = publicacion.Condiciones;
        var visible = categoriaActiva && publicacion.Estado == EstadoPublicacion.ACTIVA;
        var estado = visible ? "ACTIVA" : "PAUSADA";
        // All input is normalized by Domain value objects and ordered before hashing.
        var canonical = JsonSerializer.Serialize(new
        {
            contrato = "lendup.catalogo.snapshot.v1",
            id = publicacion.Id.ToString("D"),
            revision = revisionSql.ToString(Format, CultureInfo.InvariantCulture),
            propietario_usuario_id = publicacion.PropietarioUsuarioId.ToString("D"),
            estado,
            solicitado_desde = periodo.Desde.ToString(Format, CultureInfo.InvariantCulture),
            solicitado_hasta = periodo.Hasta.ToString(Format, CultureInfo.InvariantCulture),
            periodo_ofrecido = publicacion.OfrecePeriodo(periodo),
            disponibilidades = publicacion.Disponibilidades
                .OrderBy(x => x.Periodo.Desde).ThenBy(x => x.Id)
                .Select(x => new
                {
                    id = x.Id.ToString("D"),
                    desde = x.Periodo.Desde.ToString(Format, CultureInfo.InvariantCulture),
                    hasta = x.Periodo.Hasta.ToString(Format, CultureInfo.InvariantCulture)
                }).ToArray(),
            tarifa_diaria = condiciones.TarifaDiaria,
            garantia_monetaria = condiciones.GarantiaMonetaria,
            moneda = condiciones.Moneda,
            lugar_intercambio = condiciones.LugarIntercambio,
            condiciones_uso = condiciones.CondicionesUso,
            condiciones_entrega = condiciones.CondicionesEntrega,
            condiciones_devolucion = condiciones.CondicionesDevolucion,
            condiciones_cancelacion = condiciones.CondicionesCancelacion
        });
        var hash = Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(canonical))).ToLowerInvariant();
        return (new SnapshotPublicacion(
            publicacion.Id, publicacion.PropietarioUsuarioId, estado, publicacion.OfrecePeriodo(periodo),
            "snp_" + hash, hash, condiciones.TarifaDiaria, condiciones.GarantiaMonetaria,
            condiciones.Moneda, condiciones.LugarIntercambio, condiciones.CondicionesUso,
            condiciones.CondicionesEntrega, condiciones.CondicionesDevolucion, condiciones.CondicionesCancelacion), canonical);
    }
}
