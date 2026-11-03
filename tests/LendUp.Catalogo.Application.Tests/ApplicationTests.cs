using System.Text.Json;
using LendUp.Catalogo.Application;
using LendUp.Catalogo.Domain;
using Moq;
using Xunit;
namespace LendUp.Catalogo.Application.Tests;
public class ApplicationTests
{
    private readonly Mock<ICatalogoRepository> repo = new();
    private readonly Mock<ILecturaCache> cache = new();
    private readonly Mock<IImagenStorage> storage = new();
    private static Publicacion Nueva() => new(Guid.NewGuid(),Guid.NewGuid(),
        new(Guid.NewGuid(),"Cámara","Desc","Bueno","UPC","Campus","Lugar",12,13),
        new(10,100,"PEN","Lugar","Uso","Entrega","Devolución","Cancelación"));
    [Fact] public void PropiedadObligatoria() => Assert.Equal(403,Assert.Throws<CatalogoException>(() => CatalogoService.ExigirPropietario(Nueva(),Guid.NewGuid())).Status);
    [Fact] public async Task CrearRequiereCategoria()
    {
        var p = Nueva(); var service = new CatalogoService(repo.Object,cache.Object,storage.Object);
        Assert.Equal(404,(await Assert.ThrowsAsync<CatalogoException>(() => service.Crear(p.PropietarioUsuarioId,p.Detalles,p.Condiciones,default))).Status);
        repo.Setup(r => r.Categoria(p.Detalles.CategoriaId,It.IsAny<CancellationToken>())).ReturnsAsync(new Categoria(p.Detalles.CategoriaId,"TEC","Tecnología",true));
        var creada = await service.Crear(p.PropietarioUsuarioId,p.Detalles,p.Condiciones,default);
        Assert.NotEqual(Guid.Empty,creada.Id); Assert.Equal(p.PropietarioUsuarioId,creada.PropietarioUsuarioId);
        repo.Verify(r => r.Crear(It.IsAny<Publicacion>(),It.IsAny<CancellationToken>()),Times.Once);
    }
    [Fact] public void PatchRecursivoConservaOmitidosYNull()
    {
        var p = Nueva(); var patch = JsonSerializer.Deserialize<JsonElement>("""{"detalles":{"titulo":"Nuevo","latitud":null},"condiciones":{"garantiaMonetaria":null}}""");
        var json = ParchePublicacion.Fusionar(p,patch);
        Assert.Equal("Nuevo",json["detalles"]!["titulo"]!.GetValue<string>());
        Assert.Equal("UPC",json["detalles"]!["universidad"]!.GetValue<string>());
        Assert.Null(json["detalles"]!["latitud"]); Assert.Null(json["condiciones"]!["garantiaMonetaria"]);
    }
    [Theory, InlineData("{}"), InlineData("null"), InlineData("[]"), InlineData("{\"id\":null}"), InlineData("{\"estado\":\"ACTIVA\"}"), InlineData("{\"detalles\":{\"otro\":1}}")]
    public void PatchRechazaCampos(string json) => Assert.Throws<ReglaDominioException>(() => ParchePublicacion.Fusionar(Nueva(),JsonSerializer.Deserialize<JsonElement>(json)));
    [Fact] public async Task SubidaCompensaFalloDeCommit()
    {
        var p = Nueva();
        repo.Setup(r => r.Imagenes(p.Id,It.IsAny<CancellationToken>())).ReturnsAsync(Array.Empty<ImagenPublicacion>());
        storage.Setup(s => s.Subir(p.Id,It.IsAny<byte[]>(),It.IsAny<CancellationToken>())).ReturnsAsync(new ImagenAlmacenada("remote","https://example.test/image"));
        repo.Setup(r => r.Modificar(p.Id,It.IsAny<Func<Publicacion,CancellationToken,Task<ImagenPublicacion>>>(),It.IsAny<CancellationToken>()))
            .Returns(async (Guid _,Func<Publicacion,CancellationToken,Task<ImagenPublicacion>> change,CancellationToken ct) => { await change(p,ct); throw new InvalidOperationException("commit failure"); });
        var service = new CatalogoService(repo.Object,cache.Object,storage.Object);
        await Assert.ThrowsAsync<InvalidOperationException>(() => service.SubirImagen(p.Id,p.PropietarioUsuarioId,[255,216,255],"image/jpeg",default));
        storage.Verify(s => s.Eliminar("remote",It.IsAny<CancellationToken>()),Times.Once);
    }
    [Fact] public void FiltrosValidanParejaYPeriodo()
    {
        Assert.Throws<ReglaDominioException>(() => new FiltrosPublicaciones(Desde:new DateTime(2026,12,12)).Normalizar());
        Assert.Equal("Cámara",new FiltrosPublicaciones(" Cámara ").Normalizar().Nombre);
    }
    [Fact] public async Task PrivadasNuncaUsanCache()
    {
        var p=Nueva(); repo.Setup(r=>r.Propias(p.PropietarioUsuarioId,It.IsAny<CancellationToken>())).ReturnsAsync(new[]{p});
        var service=new CatalogoService(repo.Object,cache.Object,storage.Object);
        Assert.Single(await service.Propias(p.PropietarioUsuarioId,default)); cache.VerifyNoOtherCalls();
    }
}
