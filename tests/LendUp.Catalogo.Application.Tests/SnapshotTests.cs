using LendUp.Catalogo.Application;
using LendUp.Catalogo.Domain;
using Xunit;

namespace LendUp.Catalogo.Application.Tests;

public sealed class SnapshotTests
{
    private static (Publicacion, PeriodoDisponibilidad) Crear()
    {
        var id = Guid.NewGuid();
        var p = new Publicacion(id, Guid.NewGuid(),
            new(Guid.NewGuid(), "Cámara", "Académico", "BUENO", "UPC", "Monterrico", "Campus", null, null),
            new(25m, 50m, "PEN", "Campus", "Uso", "Entrega", "Devolución", "Cancelación"));
        p.AgregarDisponibilidad(new Disponibilidad(Guid.NewGuid(), id,
            new(new DateTime(2026, 12, 10, 9, 0, 0), new DateTime(2026, 12, 13, 9, 0, 0))));
        return (p, new PeriodoDisponibilidad(new DateTime(2026, 12, 11, 9, 0, 0), new DateTime(2026, 12, 12, 9, 0, 0)));
    }

    [Fact]
    public void ElMismoEstadoYPeriodoProduceElMismoHash()
    {
        var (p, periodo) = Crear();
        var revision = new DateTime(2026, 11, 12, 17, 1, 3, DateTimeKind.Unspecified).AddTicks(1234560);
        var a = SnapshotPublicacionFactory.Crear(p, revision, true, periodo);
        var b = SnapshotPublicacionFactory.Crear(p, revision, true, periodo);
        Assert.Equal(a.Snapshot.SnapshotId, b.Snapshot.SnapshotId);
        Assert.Equal(a.Snapshot.SnapshotHash, b.Snapshot.SnapshotHash);
        Assert.Equal(64, a.Snapshot.SnapshotHash.Length);
        Assert.Equal(a.CanonicalJson, b.CanonicalJson);
        Assert.True(a.Snapshot.PeriodoOfrecido);
        Assert.Equal("ACTIVA", a.Snapshot.Estado);
    }

    [Fact]
    public void LaRevisionElPeriodoYLasCondicionesInvalidanElConsentimientoAnterior()
    {
        var (p, periodo) = Crear();
        var revision = new DateTime(2026, 11, 12, 12, 0, 0);
        var a = SnapshotPublicacionFactory.Crear(p, revision, true, periodo).Snapshot;
        var changedPeriod = new PeriodoDisponibilidad(periodo.Desde.AddHours(1), periodo.Hasta);
        Assert.NotEqual(a.SnapshotHash, SnapshotPublicacionFactory.Crear(p, revision, true, changedPeriod).Snapshot.SnapshotHash);
        Assert.NotEqual(a.SnapshotHash, SnapshotPublicacionFactory.Crear(p, revision.AddTicks(10), true, periodo).Snapshot.SnapshotHash);
        var changedConditions = new CondicionesPublicacion(26m, 50m, "PEN", "Campus", "Uso", "Entrega", "Devolución", "Cancelación");
        p.Actualizar(p.Detalles, changedConditions);
        Assert.NotEqual(a.SnapshotHash, SnapshotPublicacionFactory.Crear(p, revision, true, periodo).Snapshot.SnapshotHash);
    }

    [Fact]
    public void NoAnunciaPeriodoFueraDeDisponibilidadNiPublicacionPausada()
    {
        var (p, periodo) = Crear();
        var revision = new DateTime(2026, 11, 12);
        var fuera = new PeriodoDisponibilidad(periodo.Desde.AddMonths(2), periodo.Hasta.AddMonths(2));
        Assert.False(SnapshotPublicacionFactory.Crear(p, revision, true, fuera).Snapshot.PeriodoOfrecido);
        Assert.Equal("PAUSADA", SnapshotPublicacionFactory.Crear(p, revision, false, periodo).Snapshot.Estado);
        p.CambiarEstado(EstadoPublicacion.PAUSADA);
        Assert.Equal("PAUSADA", SnapshotPublicacionFactory.Crear(p, revision, true, periodo).Snapshot.Estado);
    }
}
