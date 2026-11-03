using System.Text.Json;
using LendUp.Catalogo.Api;
using LendUp.Catalogo.Domain;
using Xunit;
namespace LendUp.Catalogo.Api.Tests;
public class ContractTests
{
    [Theory,InlineData("2026-12-12T09:00:00Z"),InlineData("2026-12-12T09:00:00-05:00"),InlineData("12/12/2026"),InlineData("")]
    public void RechazaZonasYFechasAmbiguas(string fecha) => Assert.Throws<ReglaDominioException>(() => Solicitudes.Fecha(fecha));
    [Fact] public void FechaLocalNoSeDesplaza()
    {
        var options = new JsonSerializerOptions(); options.Converters.Add(new FechaLocalConverter());
        var fecha = Solicitudes.Fecha("2026-12-12T09:00:00");
        Assert.Equal(DateTimeKind.Unspecified,fecha.Kind);
        Assert.Equal("\"2026-12-12T09:00:00\"",JsonSerializer.Serialize(fecha,options));
    }
    [Theory,InlineData("{\"estado\":null}"),InlineData("{\"estado\":0}"),InlineData("{\"estado\":\"0\"}"),InlineData("{\"estado\":\"activa\"}")]
    public void EstadoEstricto(string json) => Assert.Throws<ReglaDominioException>(() => Solicitudes.Estado(JsonSerializer.Deserialize<JsonElement>(json)));
}
