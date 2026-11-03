using LendUp.Catalogo.Domain;
using Xunit;
namespace LendUp.Catalogo.Domain.Tests;
public class DomainTests
{
    private static DetallesPublicacion Detalles(string titulo = "Cámara", decimal? lat = null, decimal? lon = null)
        => new(Guid.NewGuid(), titulo, "Descripción", "Bueno", "UPC", "Monterrico", "Campus", lat, lon);
    private static CondicionesPublicacion Condiciones(decimal tarifa = 10, decimal? garantia = null, string moneda = "PEN")
        => new(tarifa, garantia, moneda, "Campus", "Cuidado", "Presencial", "Presencial", "Avisar");
    private static Publicacion Nueva() => new(Guid.NewGuid(), Guid.NewGuid(), Detalles(), Condiciones());
    private static PeriodoDisponibilidad Periodo(int desde, int hasta) => new(new(2026,12,12,desde,0,0), new(2026,12,12,hasta,0,0));
    [Theory, InlineData(""), InlineData(" "), InlineData(null)]
    public void TituloObligatorio(string? titulo) => Assert.Throws<ReglaDominioException>(() => Detalles(titulo!));
    [Fact] public void LimitesTextoYCoordenadas()
    {
        Assert.Throws<ReglaDominioException>(() => Detalles(new string('a',181)));
        Assert.Throws<ReglaDominioException>(() => Detalles(lat:91));
        Assert.Throws<ReglaDominioException>(() => Detalles(lon:-181));
        Assert.Equal(90, Detalles(lat:90).Latitud);
        Assert.Throws<ReglaDominioException>(() => new Categoria(Guid.NewGuid(), new string('a',51), "Cat", true));
    }
    [Theory, InlineData(-1, null, "PEN"), InlineData(0, -1, "PEN"), InlineData(0, null, "pen"), InlineData(0, null, "P1N")]
    public void CondicionesInvalidas(int tarifa, int? garantia, string moneda)
        => Assert.Throws<ReglaDominioException>(() => Condiciones(tarifa, garantia, moneda));
    [Fact] public void CreaActivaYNoCambiaPropietario()
    {
        var p = Nueva(); var actor = p.PropietarioUsuarioId;
        Assert.Equal(EstadoPublicacion.ACTIVA,p.Estado);
        foreach (var estado in Enum.GetValues<EstadoPublicacion>()) p.CambiarEstado(estado);
        p.CambiarEstado(EstadoPublicacion.ACTIVA); p.Actualizar(Detalles("Trípode"), Condiciones(0));
        Assert.Equal(actor,p.PropietarioUsuarioId); Assert.True(p.EsPropietario(actor));
        Assert.False(p.EsPropietario(Guid.NewGuid())); Assert.Null(p.Condiciones.GarantiaMonetaria);
    }
    [Fact] public void PeriodosSemiabiertosYNoCombinados()
    {
        var p = Nueva(); var id = Guid.NewGuid();
        p.AgregarDisponibilidad(new(id,p.Id,Periodo(9,12)));
        p.AgregarDisponibilidad(new(Guid.NewGuid(),p.Id,Periodo(12,15)));
        Assert.True(p.OfrecePeriodo(Periodo(10,12))); Assert.False(p.OfrecePeriodo(Periodo(11,14)));
        Assert.Throws<DisponibilidadSuperpuestaException>(() => p.AgregarDisponibilidad(new(Guid.NewGuid(),p.Id,Periodo(11,13))));
        p.ActualizarDisponibilidad(new(id,p.Id,Periodo(8,12)));
        Assert.Throws<DisponibilidadSuperpuestaException>(() => p.ActualizarDisponibilidad(new(id,p.Id,Periodo(11,14))));
        Assert.Equal(8,p.Disponibilidades[0].Periodo.Desde.Hour);
        p.EliminarDisponibilidad(id); Assert.Throws<KeyNotFoundException>(() => p.EliminarDisponibilidad(id));
    }
    [Fact] public void RechazaPeriodoInvalido()
    {
        Assert.Throws<ReglaDominioException>(() => Periodo(12,12));
        Assert.Throws<ReglaDominioException>(() => Periodo(13,12));
        Assert.Throws<ReglaDominioException>(() => new PeriodoDisponibilidad(DateTime.UtcNow,DateTime.UtcNow.AddHours(1)));
    }
    [Fact] public void DisponibilidadAjenaDuplicadaYReconstruccionInvalida()
    {
        var p = Nueva(); var d = new Disponibilidad(Guid.NewGuid(),p.Id,Periodo(9,12));
        p.AgregarDisponibilidad(d);
        Assert.Throws<ReglaDominioException>(() => p.AgregarDisponibilidad(d));
        Assert.Throws<ReglaDominioException>(() => p.AgregarDisponibilidad(d with { Id = Guid.NewGuid(), PublicacionId = Guid.NewGuid() }));
        Assert.Throws<DisponibilidadSuperpuestaException>(() => new Publicacion(p.Id,p.PropietarioUsuarioId,p.Detalles,p.Condiciones,
            disponibilidades: [d,d with { Id = Guid.NewGuid() }]));
    }
    [Theory, InlineData("image/jpeg",new byte[]{255,216,255}), InlineData("image/png",new byte[]{137,80,78,71,13,10,26,10}),
        InlineData("image/webp",new byte[]{82,73,70,70,0,0,0,0,87,69,66,80})]
    public void FirmasValidas(string mime,byte[] bytes) => ReglasImagen.ValidarArchivo(bytes,mime);
    [Fact] public void ImagenesLimiteMimeYOrden()
    {
        Assert.Throws<ReglaDominioException>(() => ReglasImagen.ValidarArchivo([255,216,255],"image/png"));
        Assert.Throws<ReglaDominioException>(() => ReglasImagen.ValidarArchivo([],"image/jpeg"));
        Assert.Throws<ReglaDominioException>(() => ReglasImagen.ValidarArchivo(new byte[ReglasImagen.MaxBytes+1],"image/jpeg"));
        var images = Enumerable.Range(0,8).Select(i => new ImagenPublicacion(Guid.NewGuid(),Guid.NewGuid(),"remote","https://example.test/image",i)).ToArray();
        Assert.Throws<ReglaDominioException>(() => ReglasImagen.PrimerOrdenDisponible(images));
        Assert.Equal(3,ReglasImagen.PrimerOrdenDisponible(images.Where(i => i.Orden != 3)));
    }
}
