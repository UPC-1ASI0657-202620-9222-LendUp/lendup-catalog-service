using System.Globalization;
using System.Net;
using System.Net.Http.Json;
using System.Text.Json;
using LendUp.Catalogo.Infrastructure;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using MySql.Data.MySqlClient;
using Xunit;

namespace LendUp.Catalogo.Integration.Tests;

public partial class CatalogoIntegrationTests
{
    [Theory, InlineData("+00:00"), InlineData("-05:00"), InlineData("+09:00")]
    public async Task Datetime6LiteralSeLeeSinConversionConDistintasZonasDeSesion(string zone)
    {
        var url=await Crear("Fecha literal"); var publicationId=url.Split('/').Last(); var id=Guid.NewGuid().ToString();
        await using var sql=new MySqlConnection(f.Mysql.GetConnectionString()); await sql.OpenAsync();
        await using var insert=new MySqlCommand("""
            INSERT INTO disponibilidades_publicacion(id,publicacion_id,desde,hasta)
            VALUES(@id,@publication,'2026-12-12 09:00:00.123456','2026-12-12 12:00:00.654321')
            """,sql);
        insert.Parameters.AddWithValue("@id",id); insert.Parameters.AddWithValue("@publication",publicationId); await insert.ExecuteNonQueryAsync();
        using var scope=f.Factory.Services.CreateScope(); var db=scope.ServiceProvider.GetRequiredService<CatalogoDbContext>();
        await db.Database.OpenConnectionAsync();
        await db.Database.ExecuteSqlInterpolatedAsync($"SET SESSION time_zone = {zone}");
        var row=await db.Disponibilidades.AsNoTracking().SingleAsync(d=>d.Id==id);
        Assert.Equal(DateTimeKind.Unspecified,row.Desde.Kind); Assert.Equal(DateTimeKind.Unspecified,row.Hasta.Kind);
        Assert.Equal("2026-12-12 09:00:00.123456",row.Desde.ToString("yyyy-MM-dd HH:mm:ss.ffffff",CultureInfo.InvariantCulture));
        Assert.Equal("2026-12-12 12:00:00.654321",row.Hasta.ToString("yyyy-MM-dd HH:mm:ss.ffffff",CultureInfo.InvariantCulture));
        var body=await Json(url+"/disponibilidades"); var slot=Assert.Single(body.EnumerateArray());
        Assert.Equal("2026-12-12T09:00:00.123456",slot.GetProperty("desde").GetString());
        Assert.Equal("2026-12-12T12:00:00.654321",slot.GetProperty("hasta").GetString());
        Assert.Equal(0,await db.SaveChangesAsync());
        Assert.Equal("2026-12-12 09:00:00.123456",await SqlDate(sql,id,"desde"));
    }

    [Fact]
    public async Task Datetime6EscrituraHttpLecturaSqlYActualizacionConservanMicrosegundos()
    {
        var url=await Crear("Microsegundos");
        var created=await f.Client.PostAsJsonAsync(url+"/disponibilidades",new {desde="2026-12-31T23:59:59.123456",hasta="2027-01-01T01:00:00.654321"});
        Assert.Equal(HttpStatusCode.Created,created.StatusCode);
        var body=await created.Content.ReadFromJsonAsync<JsonElement>(); var id=body.GetProperty("id").GetString()!;
        Assert.Equal("2026-12-31T23:59:59.123456",body.GetProperty("desde").GetString());
        await using var sql=new MySqlConnection(f.Mysql.GetConnectionString()); await sql.OpenAsync();
        Assert.Equal("2026-12-31 23:59:59.123456",await SqlDate(sql,id,"desde"));
        Assert.Equal("2027-01-01 01:00:00.654321",await SqlDate(sql,id,"hasta"));
        var changed=await f.Client.PutAsJsonAsync(created.Headers.Location,new {desde="2026-12-31T23:59:59.999999",hasta="2027-01-01T02:00:00.000001"});
        Assert.Equal(HttpStatusCode.OK,changed.StatusCode);
        Assert.Equal("2026-12-31 23:59:59.999999",await SqlDate(sql,id,"desde"));
        Assert.Equal("2027-01-01 02:00:00.000001",await SqlDate(sql,id,"hasta"));
        var slot=Assert.Single((await Json(url+"/disponibilidades")).EnumerateArray());
        Assert.Equal("2027-01-01T02:00:00.000001",slot.GetProperty("hasta").GetString());
        using var scope=f.Factory.Services.CreateScope(); var db=scope.ServiceProvider.GetRequiredService<CatalogoDbContext>();
        var stored=await db.Disponibilidades.AsNoTracking().SingleAsync(d=>d.Id==id);
        Assert.Equal(DateTimeKind.Unspecified,stored.Desde.Kind);
    }

    [Theory,InlineData(DateTimeKind.Utc),InlineData(DateTimeKind.Local)]
    public void DatetimeDeNegocioRechazaKindConZonaAntesDePersistir(DateTimeKind kind)
        => Assert.Throws<ArgumentException>(()=>FechaLocalMySql.ParaGuardar(new DateTime(2026,12,12,9,0,0,kind)));

    private static async Task<string> SqlDate(MySqlConnection connection,string id,string column)
    {
        var sql=column switch {"desde"=>"SELECT DATE_FORMAT(desde,'%Y-%m-%d %H:%i:%s.%f') FROM disponibilidades_publicacion WHERE id=@id",
            "hasta"=>"SELECT DATE_FORMAT(hasta,'%Y-%m-%d %H:%i:%s.%f') FROM disponibilidades_publicacion WHERE id=@id",_=>throw new ArgumentException(nameof(column))};
        await using var command=new MySqlCommand(sql,connection); command.Parameters.AddWithValue("@id",id);
        return (string)(await command.ExecuteScalarAsync())!;
    }
}
